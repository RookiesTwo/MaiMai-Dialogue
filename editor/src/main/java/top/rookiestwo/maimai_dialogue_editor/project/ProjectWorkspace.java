package top.rookiestwo.maimai_dialogue_editor.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceWorkspace;

/** Per-open editor state. Mutations and completion callbacks run on the owning UI thread. */
public final class ProjectWorkspace {
    public enum Page { NONE, MENU, NEW, OPEN, SAVE_AS, CONFIRM }
    public enum Action { NEW, OPEN, CLOSE_PROJECT, CLOSE_EDITOR }

    private final ProjectStore store;
    private final Executor io;
    private final Executor ui;
    private final Runnable closeEditor;
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
            () -> !busy && !disposed && page == Page.NONE, () -> changed.run());

    public ProjectWorkspace(ProjectStore store, Executor io, Executor ui,
                            Runnable closeEditor) {
        this.store = store;
        this.io = io;
        this.ui = ui;
        this.closeEditor = closeEditor;
    }

    public void setListener(Runnable listener) {
        changed = Objects.requireNonNull(listener);
    }

    public ProjectDraft draft() { return history == null ? null : history.current(); }
    public Path directory() { return directory; }
    public Page page() { return page; }
    public boolean busy() { return busy; }
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

    private void editResources(ProjectDraft next) {
        if (history == null || busy || disposed) return;
        history.edit(next, null);
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
        changed.run();
    }

    public void setFormNamespace(String value) {
        if (busy || disposed) return;
        formNamespace = value;
        if (page == Page.NEW) {
            suggestNamespace = false;
        }
        changed.run();
    }

    public void showMenu() {
        if (busy || disposed || !windowFocused) return;
        endEdit();
        page = Page.MENU;
        clearError();
        changed.run();
    }

    /** 收起菜单不关闭项目、不清除草稿，也不取消已经进入的确认流程。 */
    public void dismissMenu() {
        if (disposed || page != Page.MENU) return;
        endEdit();
        page = Page.NONE;
        changed.run();
    }

    public void windowFocusChanged(boolean focused) {
        if (disposed) return;
        windowFocused = focused;
        if (!focused) dismissMenu();
    }

    public void request(Action action) {
        if (busy || disposed) return;
        endEdit();
        clearError();
        if (dirty()) {
            pending = action;
            page = Page.CONFIRM;
            changed.run();
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
        endEdit();
        pending = null;
        page = Page.NONE;
        changed.run();
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
                history = null;
                resources.reset();
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
        changed.run();
    }

    public void submitNew() {
        if (busy || disposed || page != Page.NEW) return;
        ProjectDraft draft = ProjectDraft.create(formName, formNamespace);
        runIo("project.creating", () -> store.allocateDirectory(draft.namespace()), target -> {
            history = new ProjectHistory(draft, false);
            resources.reset();
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
            history = new ProjectHistory(result.draft(), true);
            resources.reset();
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
        changed.run();
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
        changed.run();
    }

    public void endEdit() {
        if (history != null) history.endEdit();
    }

    public void undo() {
        if (canUndo() && !disposed) {
            history.undo();
            edited();
        }
    }

    public void redo() {
        if (canRedo() && !disposed) {
            history.redo();
            edited();
        }
    }

    public void save() { save(null); }

    private void save(Action afterSave) {
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
        changed.run();
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
                changed.run();
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
        changed = () -> {};
        pending = null;
    }

    @FunctionalInterface
    private interface IoOperation<T> {
        T run() throws Exception;
    }
}
