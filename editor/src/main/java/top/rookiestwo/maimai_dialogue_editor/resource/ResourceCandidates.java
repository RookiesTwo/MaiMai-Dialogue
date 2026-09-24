package top.rookiestwo.maimai_dialogue_editor.resource;

import top.rookiestwo.maimai_dialogue_editor.material.MaterialPack;
import java.util.*;

/** Candidate data and ordering, independent of runtime registries and UI widgets. */
public final class ResourceCandidates {
    public record Item(String value, String label) {}
    public enum Label { ID, NAME_AND_ID, PATH }
    public enum Order { ID, PROJECT_FIRST }

    private ResourceCandidates() {}

    /** Keeps catalog order; sound files must use their event names instead of ResourceKey IDs. */
    public static List<Item> project(ResourceCatalog catalog, String namespace, ResourceKind kind, Label label) {
        if (kind == ResourceKind.SOUND) throw new IllegalArgumentException("Use soundEvents for sound candidates");
        return catalog.keys().stream().filter(key -> key.kind() == kind).map(key -> {
            String id = kind == ResourceKind.IMAGE ? MaterialPack.imageId(key, namespace) : key.id(namespace);
            String name = catalog.displayName(key);
            return new Item(id, switch (label) {
                case ID -> id;
                case PATH -> key.path();
                case NAME_AND_ID -> name.isBlank() ? id : name + " · " + id;
            });
        }).toList();
    }

    /** Unqualified names for editing a project's sound event; retain first occurrence order. */
    public static List<Item> soundEvents(ResourceCatalog catalog) {
        return catalog.keys().stream().filter(key -> key.kind() == ResourceKind.SOUND).map(catalog::displayName)
                .filter(event -> !event.isBlank()).distinct().map(event -> new Item(event, event)).toList();
    }

    public static List<Item> sounds(ResourceCatalog catalog, String namespace, Collection<String> external) {
        var local = soundEvents(catalog).stream().map(event -> {
            String id = namespace + ":" + event.value();
            return new Item(id, id);
        }).toList();
        return merge(namespace, local, external, Order.PROJECT_FIRST);
    }

    /** The whole project namespace shadows external content, including removed project IDs. */
    public static List<Item> merge(String namespace, List<Item> local, Collection<String> external, Order order) {
        var project = new TreeMap<String, Item>();
        local.forEach(item -> project.put(item.value(), item));
        var outside = new TreeMap<String, Item>();
        external.stream().filter(id -> !id.startsWith(namespace + ":"))
                .forEach(id -> outside.put(id, new Item(id, id)));
        if (order == Order.ID) {
            outside.putAll(project);
            return List.copyOf(outside.values());
        }
        var result = new ArrayList<>(project.values());
        result.addAll(outside.values());
        return List.copyOf(result);
    }
}
