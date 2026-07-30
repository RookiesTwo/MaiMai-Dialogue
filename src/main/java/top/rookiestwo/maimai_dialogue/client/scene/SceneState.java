package top.rookiestwo.maimai_dialogue.client.scene;

import top.rookiestwo.maimai_dialogue.dialogue.Presentation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record SceneState(
        float dialogueOpacity,
        Optional<SceneBackgroundState> background,
        Map<String, SceneObjectState> objects
) {
    public SceneState {
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(objects, "objects");
        objects = Map.copyOf(objects);
    }

    public static SceneState initial(Presentation presentation) {
        return initial(presentation, 1.0F);
    }

    public static SceneState initial(
            Presentation presentation,
            float dialogueOpacity
    ) {
        Map<String, SceneObjectState> objects = new LinkedHashMap<>();
        presentation.visualObjects().forEach((id, definition) ->
                objects.put(id, SceneObjectState.initial(definition))
        );
        return new SceneState(
                dialogueOpacity,
                presentation.background().map(SceneBackgroundState::initial),
                objects
        );
    }

    public Optional<SceneObjectState> find(String objectId) {
        return Optional.ofNullable(objects.get(objectId));
    }

    public SceneState with(
            String objectId,
            SceneObjectState objectState
    ) {
        Map<String, SceneObjectState> updated =
                new LinkedHashMap<>(objects);
        updated.put(objectId, objectState);
        return new SceneState(dialogueOpacity, background, updated);
    }

    public SceneState withBackground(SceneBackgroundState nextBackground) {
        return new SceneState(
                dialogueOpacity,
                Optional.of(nextBackground),
                objects
        );
    }

    public SceneState withDialogueOpacity(float nextOpacity) {
        return new SceneState(nextOpacity, background, objects);
    }
}
