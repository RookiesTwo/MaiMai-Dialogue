package top.rookiestwo.maimai_dialogue_editor.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.Consumer;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceWorkspace;
import top.rookiestwo.maimai_dialogue_editor.document.ContentWorkspace;
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
    private final MaterialWorkspace materials;
    private long previewSelectionRevision;
    private long projectGeneration;
    private ValidationIssue focusedIssue;
    private ProjectDraft issueSource;
    private ResourceTree.Node issueNode;
    private long issueFocusRevision;
    private Runnable changed = () -> {};
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

    public ProjectWorkspace(ProjectStore store, Executor io, Executor ui,
                            Runnable closeEditor) {
        this.store = store;
        this.io = io;
        this.ui = ui;
        this.closeEditor = closeEditor;
        resources.setLoadRequest(this::loadResource);
        materials = new MaterialWorkspace(this, io, ui, () -> notifyChanged(), store.root().getParent());
    }
    public MaterialWorkspace materials() { return materials; }
    public top.rookiestwo.maimai_dialogue_editor.document.SceneWorkspace scenes() { return scenes; }
    public top.rookiestwo.maimai_dialogue_editor.document.ThemeWorkspace themes() { return themes; }
    public top.rookiestwo.maimai_dialogue_editor.document.AudioWorkspace audio() { return audio; }
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
        materials.synchronize();
        changed.run();
    }

    public void setListener(Runnable listener) {
        changed = Objects.requireNonNull(listener);
    }

    public ProjectDraft draft() { return history == null ? null : history.current(); }
    public long projectGeneration() { return projectGeneration; }
    public Path directory() { return directory; }
    public Page page() { return page; }
    public boolean busy() { return busy; }
    public boolean windowFocused() { return windowFocused; }
    public boolean dirty() { return history != null && history.dirty(); }
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
        if (!focused) scenes.endNumberDrag(true);
        if (!focused) themes.endGesture(true);
        if (!focused) audio.endGesture(true);
        windowFocused = focused;
        if (!focused) dismissMenu();
    }

    public void request(Action action) {
        if (busy || disposed) return;
        scenes.endNumberDrag(true);
        themes.endGesture(true);
        audio.endGesture(true);
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
    }

    public void discardAndContinue() {
        if (busy || disposed || page != Page.CONFIRM || pending == null) return;
        Action action = pending;
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
                projectGeneration++;
                history = null;
                resources.reset();
                content.reset();
                directory = null;
                fingerprint = null;
                page = Page.NONE;
                message = "project.closed";
            }
            case CLOSE_EDITOR -> {
                page = Page.NONE;
                closeEditor.run();
            }
        }
        notifyChanged();
    }

    public void submitNew() {
        if (busy || disposed || page != Page.NEW) return;
        ProjectDraft draft = ProjectDraft.create(formName, formNamespace);
        runIo("project.creating", () -> store.allocateDirectory(draft.namespace()), target -> {
            projectGeneration++;
            history = new ProjectHistory(draft, false);
            resources.reset();
            content.reset();
            directory = target;
            fingerprint = null;
            page = Page.NONE;
            message = "project.created";
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
        runIo("project.opening", () -> store.open(target), result -> {
            projectGeneration++;
            history = new ProjectHistory(result.draft(), true);
            resources.reset();
            content.reset();
            directory = target;
            fingerprint = result.fingerprint();
            page = Page.NONE;
            message = "project.opened";
        });
    }

    public void showSaveAs() {
        if (history == null || busy || disposed) return;
        endEdit();
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
        runIo("project.saving", () -> store.saveCopy(written), result -> {
            directory = result.directory();
            fingerprint = result.fingerprint();
            owner.edit(written, null);
            owner.markSaved(written);
            page = Page.NONE;
            message = "project.saved";
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

    public void undo() {
        scenes.endNumberDrag(false);
        themes.endGesture(false);
        audio.endGesture(false);
        if (canUndo() && !disposed) {
            history.undo();
            edited();
        }
    }

    public void redo() {
        scenes.endNumberDrag(false);
        themes.endGesture(false);
        audio.endGesture(false);
        if (canRedo() && !disposed) {
            history.redo();
            edited();
        }
    }

    public void save() { save(null); }

    private void save(Action afterSave) {
        scenes.endNumberDrag(true);
        themes.endGesture(true);
        audio.endGesture(true);
        if (history == null || busy || disposed) return;
        endEdit();
        ProjectHistory owner = history;
        ProjectDraft written = owner.current();
        Path target = directory;
        String expected = fingerprint;
        runIo("project.saving", () -> store.save(target, written, expected), result -> {
            fingerprint = result;
            owner.markSaved(written);
            message = "project.saved";
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
        disposed = true;
        materials.dispose();
        changed = () -> {};
        pending = null;
    }

    @FunctionalInterface
    private interface IoOperation<T> {
        T run() throws Exception;
    }
}
