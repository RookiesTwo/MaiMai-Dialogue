package top.rookiestwo.maimai_dialogue_editor.project;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import top.rookiestwo.maimai_dialogue_editor.document.ContentWorkspace;
import top.rookiestwo.maimai_dialogue_editor.document.ContentTextField;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;

// 输入缓冲、自动保存与保存游标的唯一所有者；磁盘协议仍由 ProjectSaveSession 执行。
public final class ProjectSaveCoordinator {
    public interface State {
        ProjectHistory history();
        Path directory();
        boolean disposed();
        boolean busy();
        boolean closing();
        boolean allowsAutosave();
        boolean editing();
        EditorSessionState sessionState();
        void statusChanged();
        void saved(boolean automatic);
        void autoSaveFailed(Exception error);
    }
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final ProjectStore store;
    private final ProjectSessionCoordinator sessions;
    private final Executor ui;
    private final Consumer<Runnable> autosaveDelay;
    private final State state;
    private boolean autoSave = true;
    private String fingerprint;
    private long autosaveRequest, inputRevision, savedInputRevision = -1, appliedSaveSequence;
    private Object inputOwner;
    private PendingText pendingText;
    private ProjectDraft savedInputBase, observedAutosaveDraft;
    private ProjectSaveSession saves;
    private Instant lastSavedAt;
    private AutomaticWrite automaticWrite;
    private static final class AutomaticWrite { volatile boolean cancelled; }
    private record SaveInput(ProjectDraft base, PendingText text, long revision) {
        ProjectDraft snapshot() { return text == null ? base : text.apply(base); }
    }
    public ProjectSaveCoordinator(ProjectStore store, ProjectSessionCoordinator sessions, Executor ui,
                                  Consumer<Runnable> autosaveDelay, State state) {
        this.store = store; this.sessions = sessions; this.ui = ui;
        this.autosaveDelay = autosaveDelay; this.state = state;
    }
    private ProjectDraft draft() { return state.history() == null ? null : state.history().current(); }
    public boolean persisted() { return fingerprint != null; }
    public Instant lastSavedAt() { return lastSavedAt; }
    public void restoreAutoSave(boolean value) { autoSave = value; }
    public void draftChanged() {
        if (observedAutosaveDraft != draft()) { observedAutosaveDraft = draft(); scheduleAutosave(); }
    }
    public void savedCopy(ProjectDraft written) {
        state.history().edit(written, null); state.history().markSaved(written); lastSavedAt = Instant.now();
    }
    // 已排队的写入仍可完成；仅阻止待触发的去抖任务，保留关闭后立即重开的恢复顺序。
    public void dispose() { ++autosaveRequest; }
    public ManualSave manualSave() { return new ManualSave(); }

    public final class ManualSave {
        private final ProjectHistory owner = state.history();
        private final ProjectSaveSession session = saves;
        private final SaveInput input = saveInput();
        private ProjectSaveSession.Saved result;
        private ManualSave() {}
        public ManualSave write() throws java.io.IOException { result = session.save(input.snapshot(), false); return this; }
        public boolean accept() {
            if (state.history() != owner || saves != session) return false;
            acceptSave(result, input, false); return true;
        }
    }
    public boolean autoSave() { return autoSave; }
    public void autoSave(boolean enabled) {
        if (state.disposed() || state.busy() || state.history() == null) return;
        autoSave = enabled;
        cancelAutosave();
        sessions.changed();
        if (enabled) scheduleAutosave();
        state.statusChanged();
    }

    public void stageText(Object owner, ResourceKey key, ContentWorkspace.Cursor cursor, ContentTextField field, String text) {
        if (state.disposed() || state.busy() || state.history() == null || draft().revision(key) == null || !draft().isLoaded(key)) return;
        var next = new PendingText(key, draft().revision(key), cursor, field, text);
        if (inputOwner == owner && next.equals(pendingText)) return;
        inputOwner = owner; pendingText = next; inputRevision++;
        scheduleAutosave();
        state.statusChanged();
    }

    public void clearStagedText(Object owner) {
        if (inputOwner != owner) return;
        inputOwner = null; pendingText = null; inputRevision++;
        scheduleAutosave();
        state.statusChanged();
    }

    public void reset(Path directory, String fingerprint) {
        this.fingerprint = fingerprint;
        saves = directory == null ? null : new ProjectSaveSession(store, directory, fingerprint);
        appliedSaveSequence = 0;
        lastSavedAt = null;
        inputOwner = null; pendingText = null; inputRevision++;
        savedInputRevision = -1; savedInputBase = null; observedAutosaveDraft = null;
    }

    private SaveInput saveInput() {
        return new SaveInput(draft(), pendingText != null && pendingText.appliesTo(draft()) ? pendingText : null, inputRevision);
    }

    public void cancelAutosave() {
        autosaveRequest++;
        if (automaticWrite != null) automaticWrite.cancelled = true;
    }

    public void scheduleAutosave() {
        long expected = ++autosaveRequest;
        if (state.disposed() || !autoSave || !sessions.started() || state.history() == null) return;
        autosaveDelay.accept(() -> {
            if (expected != autosaveRequest || state.disposed() || !autoSave) return;
            if (state.busy() || !state.allowsAutosave() || state.editing()) {
                scheduleAutosave();
                return;
            }
            flushAutosave();
        });
    }

    /** Capture on the UI thread, then persist without refreshing controls, preview or undo grouping. */
    public void flushAutosave() {
        if (state.disposed() || state.closing() || !autoSave || !sessions.started() || state.busy() || saves == null || !dirty()
                || !state.allowsAutosave()) return;
        cancelAutosave();
        AutomaticWrite write = new AutomaticWrite();
        automaticWrite = write;
        ProjectSaveSession session = saves;
        ProjectHistory owner = state.history();
        SaveInput input = saveInput();
        Path target = state.directory();
        EditorSessionState preferences = state.sessionState();
        sessions.enqueue(() -> {
            if (write.cancelled) return;
            ProjectSaveSession.Saved result = null;
            Exception failure = null;
            try {
                result = session.save(input.snapshot(), true);
                // These writes also survive closing/reopening the Fragment before the UI callback.
                sessions.writePreferences(target, preferences);
            } catch (Exception error) { failure = error; }
            ProjectSaveSession.Saved completed = result;
            Exception error = failure;
            ui.execute(() -> {
                if (state.disposed() || state.history() != owner || saves != session) return;
                if (automaticWrite == write) automaticWrite = null;
                if (completed != null) {
                    acceptSave(completed, input, true);
                    // Preferences may have changed while the first manifest was being created.
                    sessions.flush();
                }
                if (error != null) {
                    state.autoSaveFailed(error);
                    LOGGER.warn("Cannot autosave editor project {}", target, error);
                }
                state.statusChanged();
            });
        });
    }

    private void acceptSave(ProjectSaveSession.Saved result, SaveInput input, boolean automatic) {
        if (result.sequence() <= appliedSaveSequence) return;
        appliedSaveSequence = result.sequence();
        fingerprint = result.fingerprint();
        lastSavedAt = result.savedAt();
        savedInputBase = input.base(); savedInputRevision = input.revision();
        if (automatic) state.history().markAutosaved(result.draft());
        else state.history().markSaved(result.draft());
        state.saved(automatic);
    }

    public boolean dirty() {
        if (state.history() == null) return false;
        if (pendingText != null && pendingText.appliesTo(draft()))
            return savedInputBase != draft() || savedInputRevision != inputRevision;
        return state.history().dirty();
    }
}
