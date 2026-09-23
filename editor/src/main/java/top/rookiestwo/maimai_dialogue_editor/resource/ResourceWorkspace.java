package top.rookiestwo.maimai_dialogue_editor.resource;

import com.google.gson.JsonElement;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Per-project navigation, open document and resource operations. All calls run on the owning UI thread. */
public final class ResourceWorkspace {
    public enum Form { NONE, CREATE, COPY, DELETE, EXTRACT }
    private final Supplier<ProjectDraft> current;
    private final Consumer<ProjectDraft> edit;
    private final BooleanSupplier enabled;
    private final Runnable changed;
    private ProjectDraft indexed;
    private ResourceCatalog catalog = new ResourceCatalog(null);
    private ResourceTree.Node selection = ResourceTree.Node.project();
    private ResourceKey opened;
    private final Set<ResourceTree.Node> collapsed = new HashSet<>();
    private final Set<ResourceKey> expandedDialogues = new HashSet<>();
    private String query = "";
    private Form form = Form.NONE;
    private ResourceKind formKind = ResourceKind.DIALOGUE;
    private String formPath = "";
    private String createFolder = "";
    private boolean suggestFormPath;
    private ResourceKey source;
    private InlineResource extraction;
    private String error;
    private long revealRevision;
    private long selectionRevision;
    private long navigationRequest;
    private BiConsumer<ResourceKey, Runnable> loadRequest = (key, ready) -> ready.run();

    public ResourceWorkspace(Supplier<ProjectDraft> current, Consumer<ProjectDraft> edit,
                             BooleanSupplier enabled, Runnable changed) {
        this.current = current;
        this.edit = edit;
        this.enabled = enabled;
        this.changed = changed;
    }

    public ResourceCatalog catalog() { synchronize(); return catalog; }
    public void setLoadRequest(BiConsumer<ResourceKey, Runnable> request) { loadRequest = request; }
    public long navigationRequest() { return navigationRequest; }
    /** Keep the current document visible while its replacement is loaded on the IO executor. */
    public boolean whenLoaded(ResourceKey key, Runnable retry) {
        long expected = ++navigationRequest;
        ProjectDraft draft = current.get();
        if (key == null || draft == null || draft.isLoaded(key)) return true;
        var revision = draft.revision(key);
        loadRequest.accept(key, () -> {
            ProjectDraft now = current.get();
            if (expected == navigationRequest && active() && form == Form.NONE
                    && now != null && now.revision(key) == revision && now.isLoaded(key)) retry.run();
        });
        return false;
    }
    public ResourceTree.Node selection() { synchronize(); return selection; }
    public ResourceKey opened() { synchronize(); return opened; }
    public String query() { return query; }
    public Form form() { return form; }
    public ResourceKind formKind() { return formKind; }
    public String formPath() { return formPath; }
    public String error() { return error; }
    public ResourceKey source() { return source; }
    public long revealRevision() { return revealRevision; }
    /** Explicit browser navigation, including a second click on the same node. */
    public long selectionRevision() { return selectionRevision; }
    public boolean active() { return current.get() != null && enabled.getAsBoolean(); }
    public List<ResourceTree.Row> rows() { return ResourceTree.rows(catalog(), query, collapsed, expandedDialogues); }
    public List<ResourceCatalog.Use> blockers() {
        return source == null ? List.of() : catalog().deletionBlockers(source);
    }

    public void reset() {
        navigationRequest++;
        indexed = null;
        catalog = new ResourceCatalog(null);
        selection = ResourceTree.Node.project();
        opened = null;
        collapsed.clear();
        expandedDialogues.clear();
        query = "";
        form = Form.NONE;
        source = null;
        extraction = null;
        error = null;
        revealRevision++;
        selectionRevision++;
    }

    private void synchronize() {
        ProjectDraft draft = current.get();
        if (draft == indexed) return;
        indexed = draft;
        catalog = new ResourceCatalog(draft);
        if (opened != null && !catalog.contains(opened)) opened = null;
        ResourceKey selected = selection.owner();
        if (selected != null && !catalog.contains(selected)) selection = ResourceTree.Node.category(selected.kind());
        if (selection.type() == ResourceTree.Type.FOLDER && catalog.keys().stream().noneMatch(key ->
                key.kind() == selection.kind() && key.path().startsWith(selection.path() + "/"))) {
            selection = ResourceTree.Node.category(selection.kind());
        }
    }

    public void select(ResourceTree.Node node) {
        if (!active() || form != Form.NONE) return;
        if (!rows().stream().anyMatch(row -> row.node().equals(node))) return;
        if (!whenLoaded(node.kind() != null && node.kind().available() ? node.owner() : null, () -> select(node))) return;
        selection = node;
        selectionRevision++;
        if (node.isStep()) {
            opened = node.owner();
            revealRevision++;
        } else if (node.resource() != null && node.kind().available()) {
            opened = node.resource();
            if (node.kind() == ResourceKind.DIALOGUE) expandedDialogues.add(opened);
            revealRevision++;
        }
        changed.run();
    }

    public void toggle(ResourceTree.Node node) {
        if (!active() || form != Form.NONE || node.isStep()) return;
        if (!whenLoaded(node.resource(), () -> toggle(node))) return;
        if (node.type() == ResourceTree.Type.RESOURCE) {
            if (node.kind() != ResourceKind.DIALOGUE || !catalog().contains(node.resource())) return;
            if (!expandedDialogues.remove(node.resource())) expandedDialogues.add(node.resource());
        } else {
            if (!query.isBlank()) return;
            if (!collapsed.remove(node)) collapsed.add(node);
        }
        changed.run();
    }

    public void setQuery(String query) {
        if (!active() || form != Form.NONE) return;
        navigationRequest++;
        this.query = query;
        changed.run();
    }

    public void open(ResourceKey key) {
        if (!active() || form != Form.NONE || !key.kind().available() || !catalog().contains(key)) return;
        if (!whenLoaded(key, () -> open(key))) return;
        reveal(key);
        opened = key;
        selectionRevision++;
        changed.run();
    }

    public boolean canCreate() {
        return active() && form == Form.NONE && !selection().isStep()
                && (selection.kind() == null || selection.kind().creatable());
    }

    public boolean canModifySelected() {
        return active() && form == Form.NONE && selection().resource() != null && selection.kind().available();
    }

    public void beginCreate() {
        if (!canCreate()) return;
        navigationRequest++;
        form = Form.CREATE;
        formKind = selection.kind() == null ? ResourceKind.DIALOGUE : selection.kind();
        createFolder = switch (selection.type()) {
            case FOLDER -> selection.path();
            case RESOURCE -> selection.resource().folder();
            default -> "";
        };
        suggestFormPath = true;
        formPath = suggestedCreatePath();
        source = null;
        error = null;
        changed.run();
    }

    public void beginCopy() { beginSelected(Form.COPY); }
    public void beginDelete() { beginSelected(Form.DELETE); }

    public void beginExtract(InlineResource value, String suggestedPath) {
        if (!active() || form != Form.NONE || !value.current(current.get())) return;
        navigationRequest++;
        extraction = value; source = value.owner(); form = Form.EXTRACT; formKind = value.kind();
        formPath = catalog().unusedPath(formKind, ResourceKey.validPath(suggestedPath) ? suggestedPath : "new_" + formKind.key());
        error = null;
        changed.run();
    }

    private void beginSelected(Form next) {
        if (!canModifySelected()) return;
        if (!whenLoaded(selection.resource(), () -> beginSelected(next))) return;
        source = selection.resource();
        form = next;
        formKind = source.kind();
        formPath = next == Form.COPY ? catalog().unusedPath(formKind, source.path() + "_copy") : source.path();
        error = null;
        changed.run();
    }

    public void setFormKind(ResourceKind kind) {
        if (!active() || form != Form.CREATE || !kind.creatable()) return;
        formKind = kind;
        if (suggestFormPath) formPath = suggestedCreatePath();
        error = null;
        changed.run();
    }

    private String suggestedCreatePath() {
        return catalog().unusedPath(formKind, (createFolder.isEmpty() ? "" : createFolder + "/") + "new_" + formKind.key());
    }

    public void setFormPath(String path) {
        if (!active() || (form != Form.CREATE && form != Form.COPY && form != Form.EXTRACT)) return;
        // Once edited, keep the user's value even if it later matches a suggested name again.
        if (!formPath.equals(path)) suggestFormPath = false;
        formPath = path;
        error = null;
        changed.run();
    }

    public void cancel() {
        if (form == Form.NONE || !enabled.getAsBoolean()) return;
        form = Form.NONE;
        source = null;
        extraction = null;
        error = null;
        changed.run();
    }

    public void submit() {
        if (!active() || form == Form.NONE) return;
        ProjectDraft draft = current.get();
        ResourceCatalog index = catalog();
        ResourceKey target = new ResourceKey(formKind, formPath);
        if (form == Form.DELETE) {
            if (source == null || !index.contains(source)) { fail("missing"); return; }
            if (!blockers().isEmpty()) { fail("referenced"); return; }
            edit.accept(draft.withoutResource(source));
        } else {
            if (!ResourceKey.validPath(formPath)) { fail("invalid_path"); return; }
            if (!draft.hasResourceGroup(formKind)) { fail("invalid_group"); return; }
            if (index.contains(target)) { fail("duplicate"); return; }
            if (form == Form.EXTRACT) {
                if (extraction == null || !extraction.current(draft)) { fail("stale_source"); return; }
                edit.accept(extraction.apply(draft, target));
            } else {
                JsonElement value = form == Form.CREATE ? ResourceCatalog.emptyDraft(formKind) : draft.resource(source);
                if (value == null) { fail("missing"); return; }
                edit.accept(draft.withResource(target, value));
                reveal(target);
                opened = target;
            }
        }
        form = Form.NONE;
        source = null;
        extraction = null;
        error = null;
        synchronize();
        changed.run();
    }

    private void fail(String reason) { error = reason; changed.run(); }

    private void reveal(ResourceKey key) {
        revealRevision++;
        query = "";
        collapsed.remove(ResourceTree.Node.project());
        collapsed.remove(ResourceTree.Node.category(key.kind()));
        collapsed.removeIf(node -> node.type() == ResourceTree.Type.FOLDER && node.kind() == key.kind()
                && key.path().startsWith(node.path() + "/"));
        selection = ResourceTree.Node.resource(key);
        if (key.kind() == ResourceKind.DIALOGUE) expandedDialogues.add(key);
    }

    /** Reveal a validation location even when search/folding currently hides it. */
    public void locate(ResourceKey key, int step) {
        if (!active() || form != Form.NONE || !catalog().contains(key)) return;
        if (!whenLoaded(key, () -> locate(key, step))) return;
        reveal(key);
        if (key.kind().available()) opened = key;
        if (key.kind() == ResourceKind.DIALOGUE && step >= -1 && step < catalog.stepCount(key)) {
            selection = ResourceTree.Node.step(key, step);
        }
        selectionRevision++;
        changed.run();
    }

    /** Content edits publish one notification after updating both document cursor and browser selection. */
    public void focusStep(ResourceKey key, int index, boolean reveal) {
        if (key.kind() != ResourceKind.DIALOGUE || !catalog().contains(key)) return;
        if (index < -1 || index >= catalog.stepCount(key)) return;
        ResourceTree.Node target = ResourceTree.Node.step(key, index);
        if (reveal || !target.equals(selection)) reveal(key);
        opened = key;
        selection = target;
    }

    /** A changed history cursor reveals its new node; ordinary field edits leave navigation alone. */
    public void reconcileStep(ResourceKey key, int index) {
        if (selection.isStep() && key.equals(selection.owner()) && selection.stepIndex() != index) {
            focusStep(key, index, true);
        }
    }
}
