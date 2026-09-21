package top.rookiestwo.maimai_dialogue.client.scene;

import top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record SceneState(
        DialogueBoxState dialogueBox,
        Optional<SceneBackgroundState> background,
        Map<String, SceneObjectState> objects
) {
    public SceneState {
        Objects.requireNonNull(dialogueBox, "dialogueBox");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(objects, "objects");
        objects = Map.copyOf(objects);
    }

    public static SceneState initial(SceneDefinition sceneDefinition) {
        return initial(sceneDefinition, 1.0F);
    }

    public static SceneState initial(
            SceneDefinition sceneDefinition,
            float dialogueOpacity
    ) {
        Map<String, SceneObjectState> objects = new LinkedHashMap<>();
        sceneDefinition.visualObjects().forEach((id, definition) ->
                objects.put(id, SceneObjectState.initial(definition))
        );
        return new SceneState(
                DialogueBoxState.initial(
                        sceneDefinition.dialogueBox(),
                        dialogueOpacity
                ),
                sceneDefinition.background().map(SceneBackgroundState::initial),
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
        return new SceneState(dialogueBox, background, updated);
    }

    public SceneState withBackground(SceneBackgroundState nextBackground) {
        return new SceneState(
                dialogueBox,
                Optional.of(nextBackground),
                objects
        );
    }

    public SceneState withDialogueBox(DialogueBoxState nextDialogueBox) {
        return new SceneState(nextDialogueBox, background, objects);
    }

    public float dialogueOpacity() {
        return dialogueBox.opacity();
    }
}
