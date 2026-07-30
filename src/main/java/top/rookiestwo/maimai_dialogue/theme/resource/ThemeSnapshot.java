package top.rookiestwo.maimai_dialogue.theme.resource;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ThemeSnapshot {
    public static final ThemeSnapshot EMPTY = new ThemeSnapshot(Map.of());

    private final Map<ResourceLocation, ThemeDefinition> definitions;

    public ThemeSnapshot(Map<ResourceLocation, ThemeDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        this.definitions = Map.copyOf(definitions);
    }

    public Optional<ThemeDefinition> find(ResourceLocation themeId) {
        Objects.requireNonNull(themeId, "themeId");
        return Optional.ofNullable(definitions.get(themeId));
    }

    public int size() {
        return definitions.size();
    }

    public Map<ResourceLocation, ThemeDefinition> definitions() {
        return definitions;
    }
}
