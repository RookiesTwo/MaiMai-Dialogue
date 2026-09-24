package top.rookiestwo.maimai_dialogue_editor.project;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.Consumer;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceWorkspace;
import top.rookiestwo.maimai_dialogue_editor.document.ContentWorkspace;
import top.rookiestwo.maimai_dialogue_editor.document.ContentTextField;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceTree;
import top.rookiestwo.maimai_dialogue_editor.export.ValidationIssue;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialWorkspace;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialFiles;

/** Per-open editor state. Mutations and completion callbacks run on the owning UI thread. */
public final class ProjectWorkspace {
    public enum Page { NONE, MENU, EXPORT, NEW, OPEN, SAVE_AS, CONFIRM, IMPORT }
    public enum Action { NEW, OPEN, CLOSE_PROJECT, CLOSE_EDITOR }

    private final ProjectStore store;
    private final Executor io;
    private final Executor ui;
    private final Runnable closeEditor;
    private final Consumer<Runnable> autosaveDelay;
    private final MaterialWorkspace materials;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    // A freshly opened Fragment has a different IO executor; its restore must follow the old screen's final write.
    private static CompletableFuture<Void> sessionWrites = CompletableFuture.completedFuture(null);
    private EditorSessionStore sessionStore;
    private EditorSessionState.Layout layoutPreferences = EditorSessionState.Layout.defaults();
    private int themeExample;
    private top.rookiestwo.maimai_dialogue_editor.preview.PreviewScenario simulation = top.rookiestwo.maimai_dialogue_editor.preview.PreviewScenario.defaults();
    public top.rookiestwo.maimai_dialogue_editor.preview.PreviewScenario simulation() { return simulation; }
    public void simulation(top.rookiestwo.maimai_dialogue_editor.preview.PreviewScenario value) {
        if (disposed || busy || draft() == null || simulation.equals(value)) return;
        endEdit(); simulation = value; notifyChanged();
    }
    private long startupRequest, sessionSaveRequest;
    private EditorSessionState queuedSession;
    private Path queuedSessionDirectory;
    private long previewSelectionRevision;
    private long projectGeneration;
    private ValidationIssue focusedIssue;
    private ProjectDraft issueSource;
    private ResourceTree.Node issueNode;
    private long issueFocusRevision;
    private Runnable changed = () -> {};
    private Runnable statusChanged = () -> {};
    private boolean autoSave = true;
    private boolean closingEditor;
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
    private ProjectHistory history;
    private Path directory;
    private String fingerprint;
    private Page page = Page.NONE;
    private Action pending;
    private boolean busy;
    private boolean disposed;
    private boolean windowFocused = true;
    private String message = "project.ready";
    private String errorReason;
    private String errorDetail = "";
    private String formName = "";
    private String formNamespace = "my_project";
    private boolean suggestNamespace = true;
    private List<ProjectStore.Entry> projects = List.of();
    private final ResourceWorkspace resources = new ResourceWorkspace(this::draft, this::editResources,
            () -> !busy && !disposed && page == Page.NONE, this::resourceNavigationChanged);
    private final ContentWorkspace content = new ContentWorkspace(this::draft, resources,
            this::editContent, this::endEdit, () -> notifyChanged());
    private final top.rookiestwo.maimai_dialogue_editor.document.SceneWorkspace scenes =
            new top.rookiestwo.maimai_dialogue_editor.document.SceneWorkspace(this, () -> notifyChanged());
    private final top.rookiestwo.maimai_dialogue_editor.document.ThemeWorkspace themes =
            new top.rookiestwo.maimai_dialogue_editor.document.ThemeWorkspace(this);
    private final top.rookiestwo.maimai_dialogue_editor.document.AudioWorkspace audio =
            new top.rookiestwo.maimai_dialogue_editor.document.AudioWorkspace(this);
    private final top.rookiestwo.maimai_dialogue_editor.document.ActionWorkspace actions =
            new top.rookiestwo.maimai_dialogue_editor.document.ActionWorkspace(this, this::notifyChanged);

    public ProjectWorkspace(ProjectStore store, Executor io, Executor ui,
                            Runnable closeEditor) {
        this(store, io, ui, closeEditor, task -> CompletableFuture.delayedExecutor(
                5, java.util.concurrent.TimeUnit.SECONDS, ui).execute(task));
    }

    ProjectWorkspace(ProjectStore store, Executor io, Executor ui, Runnable closeEditor, Consumer<Runnable> autosaveDelay) {
        this.store = store;
        this.io = io;
        this.ui = ui;
        this.closeEditor = closeEditor;
        this.autosaveDelay = autosaveDelay;
        resources.setLoadRequest(this::loadResource);
        materials = new MaterialWorkspace(this, io, ui, () -> notifyChanged(), store.root().getParent());
    }
    public MaterialWorkspace materials() { return materials; }
    public top.rookiestwo.maimai_dialogue_editor.document.SceneWorkspace scenes() { return scenes; }
    public top.rookiestwo.maimai_dialogue_editor.document.ThemeWorkspace themes() { return themes; }
    public top.rookiestwo.maimai_dialogue_editor.document.AudioWorkspace audio() { return audio; }
    public top.rookiestwo.maimai_dialogue_editor.document.ActionWorkspace actions() { return actions; }
    public void showImport() {
        if (busy || disposed || draft() == null || resources.form() != ResourceWorkspace.Form.NONE) return;
        endEdit(); page = Page.IMPORT; clearError(); notifyChanged();
    }
    public void importMaterial(Path source, ResourceKey key, boolean replace) {
        importMaterials(List.of(new MaterialFiles.ImportRequest(source, key)), replace);
    }
    public void importMaterials(List<MaterialFiles.ImportRequest> requests, boolean replace) {
        if (busy || disposed || draft() == null || page != Page.IMPORT || requests.isEmpty()) return;
        List<MaterialFiles.ImportRequest> batch = List.copyOf(requests);
        ProjectDraft before = draft();
        Path targetDirectory = directory;
        runIo("material.importing", () -> MaterialFiles.importBatch(store, targetDirectory, before, batch, replace), result -> {
            history.edit(result, null);
            page = Page.NONE; message = "material.imported";
            resources.open(batch.getFirst().key());
        });
    }
    public void editAsset(ResourceKey key, com.google.gson.JsonObject value, String group) {
        if (busy || disposed || history == null || page != Page.NONE) return;
        history.edit(draft().withResource(key, value), group == null ? null : "asset/" + key + "/" + group);
        edited();
    }

    /** Background preparation shared by lazy document loading and preview snapshots. */
    public <T> CompletableFuture<T> prepare(Supplier<T> operation) {
        return CompletableFuture.supplyAsync(operation, io);
    }

    private void loadResource(ResourceKey key, Runnable ready) {
        ProjectHistory owner = history;
        ProjectDraft snapshot = draft();
        if (snapshot == null) return;
        var revision = snapshot.revision(key);
        long request = resources.navigationRequest();
        io.execute(() -> {
            Exception failure = null;
            try { snapshot.load(key); } catch (Exception error) { failure = error; }
            Exception result = failure;
            ui.execute(() -> {
                if (disposed || busy || page != Page.NONE || history != owner
                        || resources.navigationRequest() != request || draft().revision(key) != revision) return;
                if (result == null) {
                    clearError();
                    ready.run();
                } else {
                    message = "project.failed";
                    errorReason = result instanceof ProjectException error ? error.reason() : "io";
                    errorDetail = result instanceof ProjectException ? "" : String.valueOf(result.getMessage());
                    notifyChanged();
                }
            });
        });
    }

    private void notifyChanged() {
        if (observedAutosaveDraft != draft()) {
            observedAutosaveDraft = draft();
            scheduleAutosave();
        }
        materials.synchronize();
        changed.run();
        sessionStateChanged();
    }

    public EditorSessionState.Layout layoutPreferences() { return layoutPreferences; }
    public void layoutPreferences(EditorSessionState.Layout value) { layoutPreferences = value; sessionStateChanged(); }
    public int themeExample() { return themeExample; }
    public void themeExample(int value) { themeExample = Math.clamp(value, 0, 2); sessionStateChanged(); }

    /** Bootstrap once per editor, after the View listeners are connected. Loading does not seize focus. */
    public void startSession() {
        if (disposed || sessionStore != null) return;
        sessionStore = new EditorSessionStore(store);
        long request = ++startupRequest;
        if (history != null || page != Page.NONE || busy) return;
        CompletableFuture<Void> previousWrites;
        synchronized (ProjectWorkspace.class) { previousWrites = sessionWrites; }
        io.execute(() -> {
            previousWrites.join();
            Path last = sessionStore.lastProject();
            if (last == null) return;
            OpenedSession result = null;
            Exception failure = null;
            try { result = readSession(last); } catch (Exception error) { failure = error; }
            OpenedSession loaded = result;
            Exception error = failure;
            ui.execute(() -> {
                if (disposed || request != startupRequest || history != null || busy || page != Page.NONE) return;
                if (error == null) acceptSession(loaded);
                else {
                    message = "project.failed";
                    errorReason = error instanceof ProjectException problem ? problem.reason() : "io";
                    errorDetail = error instanceof ProjectException ? "" : String.valueOf(error.getMessage());
                }
                notifyChanged();
            });
        });
    }

    private record OpenedSession(Path directory, ProjectStore.Loaded project, EditorSessionState state) {}
    private OpenedSession readSession(Path target) throws java.io.IOException {
        var loaded = store.open(target);
        var state = sessionStore == null ? EditorSessionState.defaults() : sessionStore.read(target);
        // Expanded Dialogue children need their text before publishing the restored tree, even when not selected.
        // Only these and the current document/selection are loaded; all other resource bodies remain lazy.
        var keys = new java.util.HashSet<ResourceKey>();
        keys.add(state.navigation().opened()); keys.add(state.navigation().selection().owner());
        keys.addAll(state.navigation().expandedDialogues());
        for (var key : keys) if (key != null && loaded.draft().revision(key) != null) {
            try { loaded.draft().load(key); }
            catch (java.io.IOException | RuntimeException failure) {
                LOGGER.warn("Cannot restore editor document {}", key, failure);
            }
        }
        return new OpenedSession(target, loaded, state);
    }

    private void acceptSession(OpenedSession loaded) {
        cancelAutosave();
        projectGeneration++;
        history = new ProjectHistory(loaded.project().draft(), true);
        directory = loaded.directory(); fingerprint = loaded.project().fingerprint();
        resetSaveSession();
        page = Page.NONE; message = "project.opened";
        resources.restoreSession(loaded.state().navigation()); content.reset(); content.acceptBrowserSelection();
        restorePreferences(loaded.state());
        rememberProject(directory);
    }

    private void restorePreferences(EditorSessionState state) {
        autoSave = state.autoSave();
        layoutPreferences = state.layout();
        themeExample = state.preview().themeExample();
        simulation = state.preview().simulation();
        materials.restoreVariantPreferences(state.preview().materialVariants());
        scenes.restoreObjectPreferences(state.preview().sceneObjects());
        actions.restorePreviewPreferences(state.preview().actions());
    }

    public EditorSessionState sessionState() {
        return new EditorSessionState(EditorSessionState.VERSION, layoutPreferences, resources.sessionState(),
                new EditorSessionState.Preview(themeExample, scenes.objectPreferences(), materials.variantPreferences(), actions.previewPreferences(), simulation), autoSave);
    }

    /** Coalesce rapid navigation/gestures; disk work never runs on the UI or Minecraft thread. */
    public void sessionStateChanged() {
        if (disposed || sessionStore == null || history == null || fingerprint == null) return;
        long request = ++sessionSaveRequest;
        CompletableFuture.delayedExecutor(500, java.util.concurrent.TimeUnit.MILLISECONDS, ui).execute(() -> {
            if (!disposed && request == sessionSaveRequest) flushSession();
        });
    }

    public void flushSession() {
        ++sessionSaveRequest;
        if (sessionStore == null || directory == null || history == null || fingerprint == null) return;
        var state = sessionState();
        Path target = directory;
        if (target.equals(queuedSessionDirectory) && state.equals(queuedSession)) return;
        queuedSessionDirectory = target; queuedSession = state;
        writeSession(() -> {
            try { sessionStore.write(target, state); }
            catch (java.io.IOException | RuntimeException failure) {
                LOGGER.warn("Cannot save editor state for {}", target, failure);
                ui.execute(() -> {
                    if (target.equals(queuedSessionDirectory) && state.equals(queuedSession)) queuedSession = null;
                });
            }
        });
    }

    private void rememberProject(Path target) {
        if (sessionStore == null) return;
        writeSession(() -> {
            try { sessionStore.remember(target); }
            catch (java.io.IOException | RuntimeException failure) { LOGGER.warn("Cannot remember editor project", failure); }
        });
    }

    private void writeSession(Runnable write) {
        CompletableFuture<Void> previous;
        var completed = new CompletableFuture<Void>();
        synchronized (ProjectWorkspace.class) {
            previous = sessionWrites;
            sessionWrites = completed;
        }
        try {
            // Enqueue immediately so shutting down this screen's executor still drains the final write.
            io.execute(() -> {
                previous.join();
                try { write.run(); } finally { completed.complete(null); }
            });
        } catch (RuntimeException failure) {
            completed.complete(null);
            LOGGER.warn("Cannot schedule editor state save", failure);
        }
    }

    public void setListener(Runnable listener) {
        changed = Objects.requireNonNull(listener);
    }

    public void setStatusListener(Runnable listener) { statusChanged = Objects.requireNonNull(listener); }
    public boolean autoSave() { return autoSave; }
    public void autoSave(boolean enabled) {
        if (disposed || busy || history == null) return;
        autoSave = enabled;
        cancelAutosave();
        sessionStateChanged();
        if (enabled) scheduleAutosave();
        statusChanged.run();
    }

    public void stageText(Object owner, ResourceKey key, ContentWorkspace.Cursor cursor, ContentTextField field, String text) {
        if (disposed || busy || history == null || draft().revision(key) == null || !draft().isLoaded(key)) return;
        var next = new PendingText(key, draft().revision(key), cursor, field, text);
        if (inputOwner == owner && next.equals(pendingText)) return;
        inputOwner = owner; pendingText = next; inputRevision++;
        scheduleAutosave();
        statusChanged.run();
    }

    public void clearStagedText(Object owner) {
        if (inputOwner != owner) return;
        inputOwner = null; pendingText = null; inputRevision++;
        scheduleAutosave();
        statusChanged.run();
    }

    private void resetSaveSession() {
        saves = directory == null ? null : new ProjectSaveSession(store, directory, fingerprint);
        appliedSaveSequence = 0;
        lastSavedAt = null;
        inputOwner = null; pendingText = null; inputRevision++;
        savedInputRevision = -1; savedInputBase = null; observedAutosaveDraft = null;
    }

    private SaveInput saveInput() {
        return new SaveInput(draft(), pendingText != null && pendingText.appliesTo(draft()) ? pendingText : null, inputRevision);
    }

    private void cancelAutosave() {
        autosaveRequest++;
        if (automaticWrite != null) automaticWrite.cancelled = true;
    }

    private void scheduleAutosave() {
        long expected = ++autosaveRequest;
        if (disposed || !autoSave || sessionStore == null || history == null) return;
        autosaveDelay.accept(() -> {
            if (expected != autosaveRequest || disposed || !autoSave) return;
            if (busy || (page != Page.NONE && page != Page.MENU) || resources.form() != ResourceWorkspace.Form.NONE
                    || scenes.dragPosition() != null || scenes.numberPreview() != null || themes.editing()
                    || audio.editing() || actions.editing()) {
                scheduleAutosave();
                return;
            }
            flushAutosave();
        });
    }

    /** Capture on the UI thread, then persist without refreshing controls, preview or undo grouping. */
    public void flushAutosave() {
        if (disposed || closingEditor || !autoSave || sessionStore == null || busy || saves == null || !dirty()
                || (page != Page.NONE && page != Page.MENU)) return;
        cancelAutosave();
        AutomaticWrite write = new AutomaticWrite();
        automaticWrite = write;
        ProjectSaveSession session = saves;
        ProjectHistory owner = history;
        SaveInput input = saveInput();
        Path target = directory;
        EditorSessionState preferences = sessionState();
        writeSession(() -> {
            if (write.cancelled) return;
            ProjectSaveSession.Saved result = null;
            Exception failure = null;
            try {
                result = session.save(input.snapshot(), true);
                // These writes also survive closing/reopening the Fragment before the UI callback.
                sessionStore.write(target, preferences);
                sessionStore.remember(target);
            } catch (Exception error) { failure = error; }
            ProjectSaveSession.Saved completed = result;
            Exception error = failure;
            ui.execute(() -> {
                if (disposed || history != owner || saves != session) return;
                if (automaticWrite == write) automaticWrite = null;
                if (completed != null) {
                    acceptSave(completed, input, true);
                    // Preferences may have changed while the first manifest was being created.
                    flushSession();
                }
                if (error != null) {
                    message = "project.autosave_failed";
                    errorReason = error instanceof ProjectException problem ? problem.reason() : "io";
                    errorDetail = error instanceof ProjectException ? "" : String.valueOf(error.getMessage());
                    LOGGER.warn("Cannot autosave editor project {}", target, error);
                }
                statusChanged.run();
            });
        });
    }

    private void acceptSave(ProjectSaveSession.Saved result, SaveInput input, boolean automatic) {
        if (result.sequence() <= appliedSaveSequence) return;
        appliedSaveSequence = result.sequence();
        fingerprint = result.fingerprint();
        lastSavedAt = result.savedAt();
        savedInputBase = input.base(); savedInputRevision = input.revision();
        if (automatic) history.markAutosaved(result.draft());
        else history.markSaved(result.draft());
        if (!automatic || message.equals("project.autosave_failed")) {
            clearError();
            if (automatic) message = "project.ready";
        }
    }

    public ProjectDraft draft() { return history == null ? null : history.current(); }
    public long projectGeneration() { return projectGeneration; }
    public Path directory() { return directory; }
    public Page page() { return page; }
    public boolean busy() { return busy; }
    public boolean windowFocused() { return windowFocused; }
    public Instant lastSavedAt() { return lastSavedAt; }
    public boolean dirty() {
        if (history == null) return false;
        if (pendingText != null && pendingText.appliesTo(draft()))
            return savedInputBase != draft() || savedInputRevision != inputRevision;
        return history.dirty();
    }
    public boolean canUndo() { return !busy && history != null && history.canUndo(); }
    public boolean canRedo() { return !busy && history != null && history.canRedo(); }
    public String message() { return message; }
    public String errorReason() { return errorReason; }
    public String errorDetail() { return errorDetail; }
    public String formName() { return formName; }
    public String formNamespace() { return formNamespace; }
    public List<ProjectStore.Entry> projects() { return projects; }
    public ResourceWorkspace resources() { return resources; }
    public ContentWorkspace content() { return content; }
    public long previewSelectionRevision() { return previewSelectionRevision; }
    public long issueFocusRevision() { return issueFocusRevision; }
    public ValidationIssue focusedIssue() {
        return issueSource == draft() && (focusedIssue == null || focusedIssue.resource() == null
                || Objects.equals(issueNode, resources.selection())) ? focusedIssue : null;
    }

    public void showExportMenu() {
        if (busy || disposed || !windowFocused || draft() == null) return;
        endEdit();
        page = Page.EXPORT;
        notifyChanged();
    }

    public void locateIssue(ValidationIssue issue) {
        if (busy || disposed || draft() == null) return;
        page = Page.NONE;
        focusedIssue = null;
        ResourceKey key = issue.resource();
        if (key != null && !resources.whenLoaded(key, () -> locateIssue(issue))) return;
        if (key == null) {
            page = Page.MENU;
        } else if (resources.catalog().contains(key)) {
            int step = -2;
            var match = java.util.regex.Pattern.compile("^steps\\[(\\d+)]").matcher(issue.field());
            if (match.find()) step = Integer.parseInt(match.group(1));
            else if (issue.field().startsWith("end")) step = -1;
            resources.locate(key, step);
            var option = java.util.regex.Pattern.compile("^end\\.exit\\.options\\[(\\d+)]").matcher(issue.field());
            if (option.find()) content.selectOption(Integer.parseInt(option.group(1)));
            var action = java.util.regex.Pattern.compile("\\.actions\\[(\\d+)]").matcher(issue.field());
            if (action.find()) actions.select(Integer.parseInt(action.group(1)));
        }
        focusedIssue = issue;
        issueSource = draft();
        issueNode = resources.selection();
        issueFocusRevision++;
        notifyChanged();
    }

    /** Preview navigation updates the browser/properties together, without an edit or a user click event. */
    public void followPreviewStep(ResourceKey key, int step) {
        if (disposed || draft() == null || key.kind() != ResourceKind.DIALOGUE
                || !resources.catalog().contains(key) || step < -1 || step >= resources.catalog().stepCount(key)) return;
        if (key.equals(resources.opened()) && ResourceTree.Node.step(key, step).equals(resources.selection())) return;
        endEdit();
        resources.focusStep(key, step, true);
        content.acceptBrowserSelection();
        previewSelectionRevision++;
        notifyChanged();
    }

    private void resourceNavigationChanged() {
        endEdit();
        content.acceptBrowserSelection();
        notifyChanged();
    }

    private void editResources(ProjectDraft next) {
        editContent(next, null);
    }

    private void editContent(ProjectDraft next, String group) {
        if (history == null || busy || disposed) return;
        history.edit(next, group);
        clearError();
        message = "project.ready";
    }

    // Forms also belong to the workspace, so rebuilding a View cannot discard typed input.
    public void setFormName(String value) {
        if (busy || disposed) return;
        formName = value;
        if (page == Page.NEW) {
            if (suggestNamespace) formNamespace = ProjectNames.suggestNamespace(value);
        }
        notifyChanged();
    }

    public void setFormNamespace(String value) {
        if (busy || disposed) return;
        formNamespace = value;
        if (page == Page.NEW) {
            suggestNamespace = false;
        }
        notifyChanged();
    }

    public void showMenu() {
        if (busy || disposed || !windowFocused) return;
        ++startupRequest;
        endEdit();
        page = Page.MENU;
        clearError();
        notifyChanged();
    }

    /** 收起菜单不关闭项目、不清除草稿，也不取消已经进入的确认流程。 */
    public void dismissMenu() {
        if (disposed || (page != Page.MENU && page != Page.EXPORT)) return;
        endEdit();
        page = Page.NONE;
        notifyChanged();
    }

    public void windowFocusChanged(boolean focused) {
        if (disposed) return;
        if (!focused) finishGestures(true);
        windowFocused = focused;
        if (!focused) dismissMenu();
    }

    public void request(Action action) {
        if (busy || disposed) return;
        cancelAutosave();
        ++startupRequest;
        finishGestures(true);
        materials.cancelSelection();
        endEdit();
        clearError();
        if (dirty()) {
            pending = action;
            page = Page.CONFIRM;
            notifyChanged();
        } else {
            perform(action);
        }
    }

    public void escape() {
        if (resources.form() != ResourceWorkspace.Form.NONE) resources.cancel();
        else if (page == Page.NONE) request(Action.CLOSE_EDITOR);
        else cancel();
    }

    public void cancel() {
        if (busy || disposed) return;
        materials.cancelSelection();
        endEdit();
        pending = null;
        page = Page.NONE;
        notifyChanged();
        scheduleAutosave();
    }

    public void discardAndContinue() {
        if (busy || disposed || page != Page.CONFIRM || pending == null) return;
        Action action = pending;
        cancelAutosave();
        pending = null;
        perform(action);
    }

    public void saveAndContinue() {
        if (page == Page.CONFIRM && pending != null) save(pending);
    }

    private void perform(Action action) {
        pending = null;
        clearError();
        switch (action) {
            case NEW -> {
                page = Page.NEW;
                formName = "";
                formNamespace = "my_project";
                suggestNamespace = true;
            }
            case OPEN -> {
                page = Page.OPEN;
                projects = List.of();
                refreshProjects();
                return;
            }
            case CLOSE_PROJECT -> {
                flushSession();
                rememberProject(null);
                projectGeneration++;
                history = null;
                resources.reset();
                content.reset();
                directory = null;
                fingerprint = null;
                resetSaveSession();
                page = Page.NONE;
                message = "project.closed";
            }
            case CLOSE_EDITOR -> {
                closingEditor = true;
                flushSession();
                page = Page.NONE;
                closeEditor.run();
            }
        }
        notifyChanged();
    }

    public void submitNew() {
        if (busy || disposed || page != Page.NEW) return;
        ProjectDraft draft = ProjectDraft.create(formName, formNamespace);
        flushSession();
        runIo("project.creating", () -> store.allocateDirectory(draft.namespace()), target -> {
            projectGeneration++;
            history = new ProjectHistory(draft, false);
            resources.reset();
            content.reset();
            directory = target;
            fingerprint = null;
            resetSaveSession();
            page = Page.NONE;
            message = "project.created";
            restorePreferences(EditorSessionState.defaults());
            rememberProject(null);
        });
    }

    public void refreshProjects() {
        if (busy || disposed || page != Page.OPEN) return;
        runIo("project.scanning", store::listProjects, result -> {
            projects = result;
            message = "project.list_ready";
        });
    }

    public void openProject(ProjectStore.Entry entry) {
        if (busy || disposed || page != Page.OPEN || !projects.contains(entry) || !entry.canOpen()) return;
        Path target = entry.directory();
        cancelAutosave();
        flushSession();
        runIo("project.opening", () -> readSession(target), this::acceptSession);
    }

    public void showSaveAs() {
        if (history == null || busy || disposed) return;
        endEdit();
        cancelAutosave();
        pending = null;
        clearError();
        page = Page.SAVE_AS;
        formName = history.current().name();
        notifyChanged();
    }

    public void submitSaveAs() {
        if (history == null || busy || disposed || page != Page.SAVE_AS) return;
        ProjectHistory owner = history;
        ProjectDraft written = owner.current().withName(formName);
        var preferences = sessionState();
        flushSession();
        runIo("project.saving", () -> store.saveCopy(written), result -> {
            directory = result.directory();
            fingerprint = result.fingerprint();
            resetSaveSession();
            owner.edit(written, null);
            owner.markSaved(written);
            lastSavedAt = Instant.now();
            page = Page.NONE;
            message = "project.saved";
            restorePreferences(preferences);
            rememberProject(directory);
            flushSession();
        });
    }

    public void editName(String value) {
        if (history != null && !busy && !disposed) {
            history.edit(history.current().withName(value), "name");
            edited();
        }
    }

    public void editNamespace(String value) {
        if (history != null && !busy && !disposed) {
            history.edit(history.current().withNamespace(value), "namespace");
            edited();
        }
    }

    private void edited() {
        clearError();
        message = "project.ready";
        notifyChanged();
    }

    public void endEdit() {
        if (history != null) history.endEdit();
    }

    private void finishGestures(boolean commit) {
        scenes.endNumberDrag(commit);
        themes.endGesture(commit);
        audio.endGesture(commit);
        actions.endGesture(commit);
    }

    public void undo() {
        finishGestures(false);
        if (canUndo() && !disposed) {
            history.undo();
            edited();
        }
    }

    public void redo() {
        finishGestures(false);
        if (canRedo() && !disposed) {
            history.redo();
            edited();
        }
    }

    public void save() { save(null); }

    private void save(Action afterSave) {
        finishGestures(true);
        if (history == null || busy || disposed) return;
        cancelAutosave();
        endEdit();
        ProjectHistory owner = history;
        SaveInput input = saveInput();
        ProjectSaveSession session = saves;
        Path target = directory;
        runIo("project.saving", () -> session.save(input.snapshot(), false), result -> {
            if (history != owner || saves != session) return;
            acceptSave(result, input, false);
            message = "project.saved";
            rememberProject(target);
            flushSession();
            if (afterSave != null) perform(afterSave);
        });
    }

    private <T> void runIo(String workingMessage, IoOperation<T> operation, Consumer<T> onSuccess) {
        busy = true;
        message = workingMessage;
        clearError();
        notifyChanged();
        io.execute(() -> {
            T result = null;
            Exception failure = null;
            try {
                result = operation.run();
            } catch (Exception exception) {
                failure = exception;
            }
            T completed = result;
            Exception error = failure;
            ui.execute(() -> {
                if (disposed) return;
                busy = false;
                if (error == null) {
                    onSuccess.accept(completed);
                } else {
                    message = "project.failed";
                    errorReason = error instanceof ProjectException problem ? problem.reason() : "io";
                    errorDetail = error instanceof ProjectException ? "" : String.valueOf(error.getMessage());
                }
                notifyChanged();
            });
        });
    }

    private void clearError() {
        errorReason = null;
        errorDetail = "";
    }

    /** Disposing a screen suppresses stale completions; an already started disk write may finish. */
    public void dispose() {
        if (!disposed) flushSession();
        disposed = true;
        autosaveRequest++;
        materials.dispose();
        changed = () -> {};
        statusChanged = () -> {};
        pending = null;
    }

    @FunctionalInterface
    private interface IoOperation<T> {
        T run() throws Exception;
    }
}
