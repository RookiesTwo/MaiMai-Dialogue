package top.rookiestwo.maimai_dialogue_editor.resource;

import java.util.Objects;

/** Logical ID inside a category, never a user-selected filesystem path. */
public record ResourceKey(ResourceKind kind, String path) {
    public ResourceKey {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(path);
    }

    public String id(String namespace) { return namespace + ":" + path; }
    public String name() { return path.substring(path.lastIndexOf('/') + 1); }
    public String folder() { return path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : ""; }

    public static boolean validPath(String path) {
        if (!path.matches("[a-z0-9_./-]+")) return false;
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        }
        return true;
    }
}
