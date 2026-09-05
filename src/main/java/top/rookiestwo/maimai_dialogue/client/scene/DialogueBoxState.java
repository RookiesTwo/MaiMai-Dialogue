package top.rookiestwo.maimai_dialogue.client.scene;

import top.rookiestwo.maimai_dialogue.presentation.DialogueBoxLayout;

import java.util.Objects;

/**
 * Animated DialogueBox properties stored in the Scene Action state.
 */
public record DialogueBoxState(
        float x,
        float y,
        float scale,
        float opacity
) {
    public static DialogueBoxState initial(
            DialogueBoxLayout layout,
            float opacity
    ) {
        Objects.requireNonNull(layout, "layout");
        return new DialogueBoxState(
                layout.x(),
                layout.y(),
                1.0F,
                opacity
        );
    }

    public DialogueBoxState withAnimated(
            float nextX,
            float nextY,
            float nextScale,
            float nextOpacity
    ) {
        return new DialogueBoxState(
                nextX,
                nextY,
                nextScale,
                nextOpacity
        );
    }
}
