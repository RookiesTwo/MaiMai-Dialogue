package top.rookiestwo.maimai_dialogue_editor.client.preview;

import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;
import top.rookiestwo.maimai_dialogue.client.controller.DialogueScreenHandle;
import top.rookiestwo.maimai_dialogue.client.controller.DialogueUiActions;
import top.rookiestwo.maimai_dialogue.client.resource.ClientContentSnapshot;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;
import top.rookiestwo.maimai_dialogue.client.ui.screen.DialogueFragment;
import top.rookiestwo.maimai_dialogue.dialogue.branch.DialogueOption;
import top.rookiestwo.maimai_dialogue_editor.content.ProjectContentSnapshot;
import top.rookiestwo.maimai_dialogue_editor.client.EditorPreviewAssets;
import top.rookiestwo.maimai_dialogue_editor.client.EditorDialogueAudio;
import top.rookiestwo.maimai_dialogue_editor.client.EditorContentPreparation;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialSnapshot;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueImageSource;
import top.rookiestwo.maimai_dialogue_editor.preview.EditorPreviewSession;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceTree;

import java.util.Objects;
import java.util.function.Consumer;

// Dialogue 会话、缓存与导航状态由此对象拥有；Host 仅提供挂载和模式间协调。
public final class EditorDialoguePreview {
    interface Surface {
        boolean disposed();
        boolean ready();
        PreviewDisplay display();
        DialogueFragment fragment();
        void show(DialogueUiActions actions, DialogueImageSource images);
        void showIdle();
        void refresh();
        void stop();
    }
    private final ProjectWorkspace workspace;
    private final EditorPreviewAssets assets;
    private final EditorTimelinePreview timeline;
    private final EditorAudioAudition audition;
    private final Surface surface;
    private EditorDialogueAudio dialogueAudio;
    private String message = "preview.idle", error = "";
    private EditorPreviewSession playback;
    private PreviewActions previewActions;
    private long dialogueGeneration;
    private record PreparedDialogue(long project, ProjectDraft draft, ResourceKey key, ClientContentSnapshot external,
                                    ProjectContentSnapshot content, MaterialSnapshot assets) {}
    private PreparedDialogue preparedDialogue;
    private ProjectDraft source;
    private ResourceKey dialogue;
    private long revision;
    private boolean loading;
    private boolean advanceAfterLoad;
    private boolean skipAfterLoad;
    private DialogueOption optionAfterLoad;
    private boolean staleDialogueSession;
    private top.rookiestwo.maimai_dialogue.client.session.PlaybackPhase advancePhaseAfterLoad;
    private ProjectDraft observedDraft;
    private long observedProject = -1;
    private ResourceKey observedDocument;
    private ResourceTree.Node observedSelection;
    private long observedSelectionRevision = -1;
    private boolean publishingPosition;
    private top.rookiestwo.maimai_dialogue_editor.preview.PreviewScenario observedSimulation;
    private java.util.List<EditorPreviewSession.SimulationResult> simulationResults = java.util.List.of();
    private Runnable simulationChanged = () -> {};
    public void setSimulationListener(Runnable listener) { simulationChanged = listener; }
    public java.util.List<EditorPreviewSession.SimulationResult> simulationResults() { return simulationResults; }
    public boolean canSimulateSkip() { return canOperate() && !loading && running() && playback.state().canSkipToEnd(); }
    public void simulateSkip() {
        if (!canSimulateSkip()) return;
        if (staleDialogueSession) { restartStep(); skipAfterLoad = true; return; }
        playback.skipToEnd(); render();
    }
    EditorDialoguePreview(ProjectWorkspace workspace, EditorPreviewAssets assets, EditorTimelinePreview timeline,
                          EditorAudioAudition audition, Surface surface) {
        this.workspace = workspace; this.assets = assets; this.timeline = timeline;
        this.audition = audition; this.surface = surface;
    }
    boolean canSeek() { return canOperate() && source == workspace.draft() && workspace.resources().selection().isStep(); }
    boolean acceptsCanvas(ProjectDraft before) { return running() && source == before; }
    void canvasCommitted() { source = workspace.draft(); staleDialogueSession = true; }
    void forgetDisplay() { previewActions = null; preparedDialogue = null; }
    DialogueUiActions idleActions() { return new PreviewActions(null); }
    void notifySimulation() { simulationChanged.run(); }
    void releaseListeners() { observedProject = -1; simulationChanged = () -> {}; }
    private void refresh() { surface.refresh(); }
    boolean synchronize(Runnable canvasCommit) {
        var resources = workspace.resources();
        ProjectDraft draft = workspace.draft();
        ResourceKey opened = resources.opened();
        ResourceTree.Node selected = resources.selection();
        boolean draftChanged = draft != observedDraft;
        boolean projectChanged = observedProject != workspace.projectGeneration();
        boolean simulationUpdated = !Objects.equals(observedSimulation, workspace.simulation());
        observedSimulation = workspace.simulation();
        if (projectChanged || !Objects.equals(observedDocument, opened)) simulationResults = java.util.List.of();
        observedProject = workspace.projectGeneration();
        boolean selectionChanged = observedSelectionRevision != resources.selectionRevision()
                || !Objects.equals(observedSelection, selected) || !Objects.equals(observedDocument, opened);
        observedDraft = draft;
        observedDocument = opened;
        observedSelection = selected;
        observedSelectionRevision = resources.selectionRevision();
        if (publishingPosition) {
            if (surface.display() != null) surface.display().refresh();
            return false;
        }
        if (draftChanged && !projectChanged && !selectionChanged) canvasCommit.run();
        // Restoring a Step enters it just like a click; sampling at zero would freeze its entrance effects invisible.
        // Only edits to an already open document preserve the manual timeline position.
        if ((projectChanged || simulationUpdated || !draftChanged && selectionChanged) && selected.isStep() && Objects.equals(selected.owner(), opened)) {
            startAt(selected.stepIndex());
        } else {
            if (selected.isStep() && Objects.equals(selected.owner(), opened) && source != draft && canOperate()) {
                startAt(selected.stepIndex(), false, projectChanged ? 0 : timeline.model().manual() ? timeline.model().position() : Integer.MAX_VALUE);
            } else if (source != null && (source != draft || !Objects.equals(dialogue, opened))) surface.stop();
            if (!draftChanged && selectionChanged && selected.kind() == ResourceKind.DIALOGUE
                    && selected.type() == ResourceTree.Type.RESOURCE) surface.stop();
        }
        return true;
    }

    boolean canOperate() {
        ResourceKey opened = workspace.resources().opened();
        return !surface.disposed() && !workspace.busy() && workspace.page() == ProjectWorkspace.Page.NONE
                && workspace.resources().form() == top.rookiestwo.maimai_dialogue_editor.resource.ResourceWorkspace.Form.NONE
                && workspace.draft() != null && opened != null && opened.kind() == ResourceKind.DIALOGUE;
    }

    public boolean running() { return playback != null && playback.running(); }
    public boolean loading() { return loading; }
    public String message() { return message; }
    public String error() { return error; }
    private EditorDialogueAudio audioScope(MaterialSnapshot materials, long expected) {
        return new EditorDialogueAudio(materials, task -> workspace.prepare(() -> { task.run(); return null; }),
                failure -> Core.getUiHandler().post(() -> {
                    if (surface.disposed() || expected != revision) return;
                    error = failure; refresh();
                }), () -> Core.getUiHandler().post(this::refresh));
    }
    void closeAudio() {
        if (dialogueAudio != null) dialogueAudio.close();
        dialogueAudio = null;
    }
    public void start() {
        startAt(0);
    }
    void restartStep() {
        var selected = workspace.resources().selection();
        if (selected.isStep()) startAt(selected.stepIndex());
    }

    private void startAt(int step) {
        startAt(step, true, 0);
    }
    private void startAt(int step, boolean playNow, int seekTime) {
        // A new tree selection may supersede a pending start before the client snapshot arrives.
        if (!canOperate() || !surface.ready()) return;
        // Automatic draft refresh is part of the current edit, not a new user operation.
        if (playNow) workspace.endEdit();
        audition.stop();
        closeAudio();
        timeline.freezeFrame();
        // Keep the displayed session and controls until the replacement is ready.
        // Its callbacks are suspended while loading, then discarded by session identity.
        source = workspace.draft();
        dialogue = workspace.resources().opened();
        ProjectDraft captured = source;
        var capturedSimulation = workspace.simulation();
        ResourceKey capturedKey = dialogue;
        long capturedProject = workspace.projectGeneration();
        var cached = preparedDialogue;
        long expected = ++revision;
        loading = true;
        advanceAfterLoad = false;
        skipAfterLoad = false;
        optionAfterLoad = null;
        advancePhaseAfterLoad = null;
        refresh();
        // Read the loaded resource snapshot on the client thread; never replace the global repository.
        record Start(PreparedDialogue content, int interval) {}
        EditorContentPreparation.onClientSnapshot(external -> {
            int interval = ClientConfig.get().defaultTypewriterIntervalMs();
            var preparation = cached != null && cached.project() == capturedProject && cached.draft() == captured
                    && cached.key().equals(capturedKey) && cached.external() == external
                    ? java.util.concurrent.CompletableFuture.completedFuture(cached) : workspace.prepare(() -> {
                try {
                    return new PreparedDialogue(capturedProject, captured, capturedKey, external,
                            new ProjectContentSnapshot(captured, external).prepare(
                            ResourceLocation.fromNamespaceAndPath(captured.namespace(), capturedKey.path())),
                            MaterialSnapshot.prepare(captured));
                } catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
            });
            return preparation.thenApply(content -> new Start(content, interval));
        }).whenComplete((start, preparationFailure) -> Core.getUiHandler().post(() -> {
            if (surface.disposed() || expected != revision || surface.display() == null) return;
            if (!surface.ready()) {
                // A saved/detached Fragment must be prepared again once it can mount, not marked as displayed.
                reset();
                observedProject = -1;
                return;
            }
            loading = false;
            if (capturedProject != workspace.projectGeneration() || captured != workspace.draft()
                    || !capturedSimulation.equals(workspace.simulation()) || !Objects.equals(capturedKey, workspace.resources().opened())) {
                surface.stop();
                return;
            }
            try {
                if (preparationFailure != null) throw new java.util.concurrent.CompletionException(preparationFailure);
                var content = start.content();
                var prepared = new EditorPreviewSession(content.content(),
                        ResourceLocation.fromNamespaceAndPath(captured.namespace(), capturedKey.path()), start.interval(), step,
                        capturedSimulation, ++dialogueGeneration);
                if (playback != null) playback.stop();
                playback = prepared;
                staleDialogueSession = false;
                if (!playNow) playback.prepareForEditing();
                if (optionAfterLoad != null) playback.selectOptionAfterRefresh(optionAfterLoad);
                optionAfterLoad = null;
                if (skipAfterLoad) playback.skipToEnd();
                skipAfterLoad = false;
                if (advanceAfterLoad) {
                    if (advancePhaseAfterLoad == null) playback.advance();
                    else playback.advanceAfterRefresh(advancePhaseAfterLoad);
                }
                advanceAfterLoad = false;
                advancePhaseAfterLoad = null;
                if (running()) {
                    if (playNow) dialogueAudio = audioScope(content.assets(), expected);
                    // Keep the mounted scene and its image handles when only playback data changed.
                    boolean reuse = surface.fragment() != null && previewActions != null && preparedDialogue != null
                            && preparedDialogue.project() == capturedProject && preparedDialogue.external() == content.external()
                            && preparedDialogue.assets().equals(content.assets());
                    if (reuse) previewActions.session = playback;
                    else {
                        previewActions = new PreviewActions(playback);
                        surface.show(previewActions, assets.openImages(content.assets()));
                    }
                    preparedDialogue = content;
                }
                render();
                if (!playNow && running()) {
                    timeline.seek(timeline.model().playback(), seekTime);
                    timeline.freezeFrame();
                }
            } catch (RuntimeException failure) {
                closeAudio();
                advanceAfterLoad = false;
                if (playback != null) playback.stop();
                playback = null;
                surface.showIdle();
                Throwable cause = failure;
                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null)
                    cause = cause.getCause();
                error = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                message = "preview.failed";
                refresh();
            }
        }));
    }

    public void advance() {
        if (!canOperate()) return;
        if (loading) {
            // Apply a click to the requested Step once ready, never to the old displayed session.
            advanceAfterLoad = true;
            return;
        }
        if (!running()) {
            var selected = workspace.resources().selection();
            startAt(selected.isStep() && selected.owner().equals(workspace.resources().opened()) ? selected.stepIndex() : 0);
            return;
        }
        if (staleDialogueSession) {
            var phase = playback.state().playbackPhase();
            startAt(workspace.resources().selection().stepIndex());
            advanceAfterLoad = true; advancePhaseAfterLoad = phase;
            return;
        }
        playback.advance();
        render();
    }

    public void stop() {
        if (timeline.model().manual()) { restartStep(); return; }
        reset();
        surface.showIdle();
        refresh();
    }

    void reset() {
        timeline.reset();
        closeAudio();
        ++revision;
        loading = false;
        advanceAfterLoad = false;
        staleDialogueSession = false; advancePhaseAfterLoad = null;
        skipAfterLoad = false;
        optionAfterLoad = null;
        if (playback != null) playback.stop();
        playback = null;
        previewActions = null;
        preparedDialogue = null;
        source = null;
        dialogue = null;
        error = "";
        message = "preview.idle";
    }

    private void render() {
        if (playback == null) return;
        dialogueGeneration = Math.max(dialogueGeneration, playback.state().generation());
        simulationResults = playback.simulationResults();
        followPosition();
        message = switch (playback.status()) {
            case RUNNING -> "preview.running";
            case FINISHED -> "preview.finished";
            case STOPPED -> "preview.idle";
            case FAILED -> "preview.failed";
        };
        error = playback.error();
        if (running() && surface.fragment() != null) {
            var calls = workspace.actions().calls();
            timeline.bind(playback, playback.state().scenePlayback().orElse(null), calls == null ? 0 : calls.size(), true);
            if (dialogueAudio != null) dialogueAudio.render(playback.state(), playback.drainBgm());
            if (!timeline.model().manual()) surface.fragment().render(playback.state());
        }
        else {
            timeline.clear();
            closeAudio();
            // Drop decoded definitions, history and simulated commands as soon as playback ends.
            playback = null;
            surface.showIdle();
        }
        refresh();
    }

    private void followPosition() {
        var position = playback.position();
        if (position == null || source != workspace.draft() || !position.dialogueId().getNamespace().equals(source.namespace())) return;
        ResourceKey key = new ResourceKey(ResourceKind.DIALOGUE, position.dialogueId().getPath());
        if (!workspace.resources().catalog().contains(key)) return;
        dialogue = key;
        publishingPosition = true;
        try {
            workspace.followPreviewStep(key, position.end() ? -1 : position.stepIndex());
        } finally {
            publishingPosition = false;
        }
    }

    private final class PreviewActions implements DialogueUiActions {
        private volatile EditorPreviewSession session;
        PreviewActions(EditorPreviewSession session) { this.session = session; }

        @Override public DialogueScreenState viewState() {
            return session == null ? DialogueScreenState.empty(0) : session.state();
        }

        private void dispatch(Consumer<EditorPreviewSession> action) {
            var target = session;
            if (target == null) return;
            Core.getUiHandler().post(() -> {
                // Old animation, text and destruction callbacks must never operate on a restarted preview.
                if (surface.disposed() || loading || timeline.model().manual() || playback != target || !target.running()) return;
                action.accept(target);
                render();
            });
        }

        @Override public void advance() { dispatch(EditorPreviewSession::advance); }
        @Override public void skipToEnd() { /* The preview's skip icon is decorative. */ }
        @Override public void selectOption(DialogueOption option) {
            var target = session;
            Core.getUiHandler().post(() -> {
                if (surface.disposed() || loading || target == null || playback != target || !target.running()
                        || !canOperate() || source != workspace.draft() || !target.state().options().contains(option)) return;
                if (timeline.model().manual() || staleDialogueSession) {
                    // 显式点击才重新进入可播放会话，始终用最新草稿校验选项和命令。
                    startAt(workspace.resources().selection().stepIndex()); optionAfterLoad = option;
                } else { target.selectOption(option); render(); }
            });
        }
        @Override public void completePlayback(long generation, long token) {
            dispatch(current -> current.completeScene(generation, token));
        }
        @Override public void completeTextPlayback(long generation, long token) {
            dispatch(current -> current.completeText(generation, token));
        }
        private void audio(Consumer<EditorDialogueAudio> action) {
            var target = session;
            Core.getUiHandler().post(() -> {
                if (!surface.disposed() && !loading && !timeline.model().manual() && target != null && playback == target && target.running() && dialogueAudio != null)
                    action.accept(dialogueAudio);
            });
        }
        @Override public void audioFrame(long generation, long token, int elapsedMs) {
            var target = session;
            Core.getUiHandler().post(() -> {
                if (!surface.disposed() && !loading && playback == target && target != null && target.state().generation() == generation)
                    timeline.follow(target, token, elapsedMs);
            });
            audio(audio -> audio.frame(generation, token, elapsedMs));
        }
        @Override public void textRevealed(long generation, long token, int end, boolean audible) { audio(audio -> audio.reveal(generation, token, end, audible)); }
        @Override public void closeFromUi() { dispatch(EditorPreviewSession::stop); }
        @Override public void onScreenDestroyed(DialogueScreenHandle screen) {
            dispatch(current -> { if (surface.fragment() == screen) current.stop(); });
        }
    }
}
