package top.rookiestwo.maimai_dialogue.content;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class DefinitionRegistry<T> {
    private final Map<ResourceLocation, T> entries;

    public DefinitionRegistry(Map<ResourceLocation, T> entries) {
        Objects.requireNonNull(entries, "entries");
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public static <T> DefinitionRegistry<T> empty() {
        return new DefinitionRegistry<>(Map.of());
    }

    public Optional<T> find(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(entries.get(id));
    }

    public boolean contains(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return entries.containsKey(id);
    }

    public Set<ResourceLocation> ids() {
        return entries.keySet();
    }

    public Map<ResourceLocation, T> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
