package top.rookiestwo.maimai_dialogue_editor.resource;

import com.google.gson.JsonElement;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Per-project navigation, open document and resource operations. All calls run on the owning UI thread. */
public final class ResourceWorkspace {
    public enum Form { NONE, CREATE, COPY, DELETE }
    private final Supplier<ProjectDraft> current;
    private final Consumer<ProjectDraft> edit;
    private final BooleanSupplier enabled;
    private final Runnable changed;
    private ProjectDraft indexed;
    private ResourceCatalog catalog = new ResourceCatalog(null);
    private ResourceTree.Node selection = ResourceTree.Node.project();
    private ResourceKey opened;
    private final Set<ResourceTree.Node> collapsed = new HashSet<>();
    private String query = "";
    private Form form = Form.NONE;
    private ResourceKind formKind = ResourceKind.DIALOGUE;
    private String formPath = "";
    private ResourceKey source;
    private String error;

    public ResourceWorkspace(Supplier<ProjectDraft> current, Consumer<ProjectDraft> edit,
                             BooleanSupplier enabled, Runnable changed) {
        this.current = current;
        this.edit = edit;
        this.enabled = enabled;
        this.changed = changed;
    }

    public ResourceCatalog catalog() { synchronize(); return catalog; }
    public ResourceTree.Node selection() { synchronize(); return selection; }
    public ResourceKey opened() { synchronize(); return opened; }
    public String query() { return query; }
    public Form form() { return form; }
    public ResourceKind formKind() { return formKind; }
    public String formPath() { return formPath; }
    public String error() { return error; }
    public ResourceKey source() { return source; }
    public boolean active() { return current.get() != null && enabled.getAsBoolean(); }
    public List<ResourceTree.Row> rows() { return ResourceTree.rows(catalog(), query, collapsed); }
    public List<ResourceCatalog.Use> blockers() {
        return source == null ? List.of() : catalog().deletionBlockers(source);
    }

    public void reset() {
        indexed = null;
        catalog = new ResourceCatalog(null);
        selection = ResourceTree.Node.project();
        opened = null;
        collapsed.clear();
        query = "";
        form = Form.NONE;
        source = null;
        error = null;
    }

    private void synchronize() {
        ProjectDraft draft = current.get();
        if (draft == indexed) return;
        indexed = draft;
        catalog = new ResourceCatalog(draft);
        if (opened != null && !catalog.contains(opened)) opened = null;
        ResourceKey selected = selection.resource();
        if (selected != null && !catalog.contains(selected)) selection = ResourceTree.Node.category(selected.kind());
        if (selection.type() == ResourceTree.Type.FOLDER && catalog.keys().stream().noneMatch(key ->
                key.kind() == selection.kind() && key.path().startsWith(selection.path() + "/"))) {
            selection = ResourceTree.Node.category(selection.kind());
        }
    }

    public void select(ResourceTree.Node node) {
        if (!active() || form != Form.NONE) return;
        if (!rows().stream().anyMatch(row -> row.node().equals(node))) return;
        selection = node;
        changed.run();
    }

    public void toggle(ResourceTree.Node node) {
        if (!active() || form != Form.NONE || node.type() == ResourceTree.Type.RESOURCE || !query.isBlank()) return;
        if (!collapsed.remove(node)) collapsed.add(node);
        changed.run();
    }

    public void setQuery(String query) {
        if (!active() || form != Form.NONE) return;
        this.query = query;
        changed.run();
    }

    public void open(ResourceKey key) {
        if (!active() || form != Form.NONE || !key.kind().available() || !catalog().contains(key)) return;
        reveal(key);
        opened = key;
        changed.run();
    }

    public void closeDocument() {
        if (!active() || form != Form.NONE) return;
        opened = null;
        changed.run();
    }

    public boolean canCreate() {
        return active() && form == Form.NONE && (selection().kind() == null || selection.kind().available());
    }

    public boolean canModifySelected() {
        return active() && form == Form.NONE && selection().resource() != null && selection.kind().available();
    }

    public void beginCreate() {
        if (!canCreate()) return;
        form = Form.CREATE;
        formKind = selection.kind() == null ? ResourceKind.DIALOGUE : selection.kind();
        String folder = switch (selection.type()) {
            case FOLDER -> selection.path();
            case RESOURCE -> selection.resource().folder();
            default -> "";
        };
        formPath = catalog().unusedPath(formKind, (folder.isEmpty() ? "" : folder + "/") + "new_" + formKind.key());
        source = null;
        error = null;
        changed.run();
    }

    public void beginCopy() { beginSelected(Form.COPY); }
    public void beginDelete() { beginSelected(Form.DELETE); }

    private void beginSelected(Form next) {
        if (!canModifySelected()) return;
        source = selection.resource();
        form = next;
        formKind = source.kind();
        formPath = next == Form.COPY ? catalog().unusedPath(formKind, source.path() + "_copy") : source.path();
        error = null;
        changed.run();
    }

    public void setFormKind(ResourceKind kind) {
        if (!active() || form != Form.CREATE || !kind.available()) return;
        formKind = kind;
        error = null;
        changed.run();
    }

    public void setFormPath(String path) {
        if (!active() || (form != Form.CREATE && form != Form.COPY)) return;
        formPath = path;
        error = null;
        changed.run();
    }

    public void cancel() {
        if (form == Form.NONE || !enabled.getAsBoolean()) return;
        form = Form.NONE;
        source = null;
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
            JsonElement value = form == Form.CREATE ? ResourceCatalog.emptyDraft(formKind) : draft.resource(source);
            if (value == null) { fail("missing"); return; }
            edit.accept(draft.withResource(target, value));
            reveal(target);
            opened = target;
        }
        form = Form.NONE;
        source = null;
        error = null;
        synchronize();
        changed.run();
    }

    private void fail(String reason) { error = reason; changed.run(); }

    private void reveal(ResourceKey key) {
        query = "";
        collapsed.remove(ResourceTree.Node.project());
        collapsed.remove(ResourceTree.Node.category(key.kind()));
        collapsed.removeIf(node -> node.type() == ResourceTree.Type.FOLDER && node.kind() == key.kind()
                && key.path().startsWith(node.path() + "/"));
        selection = ResourceTree.Node.resource(key);
    }
}
