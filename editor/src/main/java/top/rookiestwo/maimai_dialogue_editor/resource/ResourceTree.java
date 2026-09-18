package top.rookiestwo.maimai_dialogue_editor.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** Navigation hierarchy only; does not depend on a View or its dimensions. */
public final class ResourceTree {
    public enum Type { PROJECT, CATEGORY, FOLDER, RESOURCE, STEP, END }
    public record Node(Type type, ResourceKind kind, String path, int stepIndex) {
        public Node(Type type, ResourceKind kind, String path) { this(type, kind, path, -1); }
        public static Node project() { return new Node(Type.PROJECT, null, ""); }
        public static Node category(ResourceKind kind) { return new Node(Type.CATEGORY, kind, ""); }
        public static Node resource(ResourceKey key) { return new Node(Type.RESOURCE, key.kind(), key.path()); }
        public static Node step(ResourceKey key, int index) {
            return new Node(index < 0 ? Type.END : Type.STEP, key.kind(), key.path(), index);
        }
        public ResourceKey resource() { return type == Type.RESOURCE ? new ResourceKey(kind, path) : null; }
        public boolean isStep() { return type == Type.STEP || type == Type.END; }
        public ResourceKey owner() { return isStep() ? new ResourceKey(kind, path) : resource(); }
        public String name() { return path.substring(path.lastIndexOf('/') + 1); }
    }
    public record Row(Node node, int depth, boolean branch, boolean expanded) {}

    private ResourceTree() {}

    public static List<Row> rows(ResourceCatalog catalog, String query, Set<Node> collapsed) {
        return rows(catalog, query, collapsed, Set.of());
    }

    public static List<Row> rows(ResourceCatalog catalog, String query, Set<Node> collapsed,
                                 Set<ResourceKey> expandedDialogues) {
        List<Row> rows = new ArrayList<>();
        boolean searching = !query.isBlank();
        Node root = Node.project();
        boolean open = searching || !collapsed.contains(root);
        rows.add(new Row(root, 0, true, open));
        if (!open) return List.copyOf(rows);
        List<ResourceKey> matches = catalog.search(query);
        for (ResourceKind kind : ResourceKind.values()) {
            List<ResourceKey> resources = matches.stream().filter(key -> key.kind() == kind).toList();
            if (searching && resources.isEmpty()) continue;
            Node category = Node.category(kind);
            boolean expanded = searching || !collapsed.contains(category);
            rows.add(new Row(category, 1, true, expanded));
            if (!expanded) continue;
            Folder folder = new Folder();
            for (ResourceKey resource : resources) {
                // Invalid stored paths are shown as leaves; never normalize or silently drop user data.
                Folder parent = folder;
                if (ResourceKey.validPath(resource.path())) {
                    for (String segment : resource.folder().split("/")) {
                        if (!segment.isEmpty()) parent = parent.folders.computeIfAbsent(segment, name -> new Folder());
                    }
                }
                parent.resources.add(resource);
            }
            append(rows, folder, kind, "", 2, searching, collapsed, catalog, expandedDialogues);
        }
        return List.copyOf(rows);
    }

    private static void append(List<Row> rows, Folder parent, ResourceKind kind, String path,
                               int depth, boolean searching, Set<Node> collapsed, ResourceCatalog catalog,
                               Set<ResourceKey> expandedDialogues) {
        parent.folders.forEach((name, folder) -> {
            String nested = path.isEmpty() ? name : path + "/" + name;
            Node node = new Node(Type.FOLDER, kind, nested);
            boolean expanded = searching || !collapsed.contains(node);
            rows.add(new Row(node, depth, true, expanded));
            if (expanded) append(rows, folder, kind, nested, depth + 1, searching, collapsed, catalog, expandedDialogues);
        });
        for (ResourceKey resource : parent.resources) {
            boolean dialogue = resource.kind() == ResourceKind.DIALOGUE;
            boolean expanded = dialogue && expandedDialogues.contains(resource);
            rows.add(new Row(Node.resource(resource), depth, dialogue, expanded));
            if (expanded) {
                for (int index = 0; index < catalog.stepCount(resource); index++) {
                    rows.add(new Row(Node.step(resource, index), depth + 1, false, false));
                }
                rows.add(new Row(Node.step(resource, -1), depth + 1, false, false));
            }
        }
    }

    private static final class Folder {
        final TreeMap<String, Folder> folders = new TreeMap<>();
        final List<ResourceKey> resources = new ArrayList<>();
    }
}
