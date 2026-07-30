package top.rookiestwo.maimai_dialogue.presentation.action.resource;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.presentation.action.PresentationAction;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ActionSnapshot {
    public static final ActionSnapshot EMPTY = new ActionSnapshot(Map.of());

    private final Map<ResourceLocation, PresentationAction> definitions;

    public ActionSnapshot(
            Map<ResourceLocation, PresentationAction> definitions
    ) {
        Objects.requireNonNull(definitions, "definitions");
        this.definitions = Map.copyOf(definitions);
    }

    public Optional<PresentationAction> find(ResourceLocation actionId) {
        Objects.requireNonNull(actionId, "actionId");
        return Optional.ofNullable(definitions.get(actionId));
    }

    public int size() {
        return definitions.size();
    }
}
