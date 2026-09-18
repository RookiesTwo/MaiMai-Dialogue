package top.rookiestwo.maimai_dialogue_editor.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** Navigation hierarchy only; does not depend on a View or its dimensions. */
public final class ResourceTree {
    public enum Type { PROJECT, CATEGORY, FOLDER, RESOURCE }
    public record Node(Type type, ResourceKind kind, String path) {
        public static Node project() { return new Node(Type.PROJECT, null, ""); }
        public static Node category(ResourceKind kind) { return new Node(Type.CATEGORY, kind, ""); }
        public static Node resource(ResourceKey key) { return new Node(Type.RESOURCE, key.kind(), key.path()); }
        public ResourceKey resource() { return type == Type.RESOURCE ? new ResourceKey(kind, path) : null; }
        public String name() { return path.substring(path.lastIndexOf('/') + 1); }
    }
    public record Row(Node node, int depth, boolean branch, boolean expanded) {}

    private ResourceTree() {}

    public static List<Row> rows(ResourceCatalog catalog, String query, Set<Node> collapsed) {
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
            append(rows, folder, kind, "", 2, searching, collapsed);
        }
        return List.copyOf(rows);
    }

    private static void append(List<Row> rows, Folder parent, ResourceKind kind, String path,
                               int depth, boolean searching, Set<Node> collapsed) {
        parent.folders.forEach((name, folder) -> {
            String nested = path.isEmpty() ? name : path + "/" + name;
            Node node = new Node(Type.FOLDER, kind, nested);
            boolean expanded = searching || !collapsed.contains(node);
            rows.add(new Row(node, depth, true, expanded));
            if (expanded) append(rows, folder, kind, nested, depth + 1, searching, collapsed);
        });
        for (ResourceKey resource : parent.resources) rows.add(new Row(Node.resource(resource), depth, false, false));
    }

    private static final class Folder {
        final TreeMap<String, Folder> folders = new TreeMap<>();
        final List<ResourceKey> resources = new ArrayList<>();
    }
}
