package top.rookiestwo.maimai_dialogue.client.session;

import java.util.List;
import java.util.Objects;

public record DialogueSessionUpdate(
        DialogueScreenState state,
        List<DialogueSessionEffect> effects,
        boolean changed
) {
    public DialogueSessionUpdate {
        Objects.requireNonNull(state, "state");
        effects = List.copyOf(effects);
    }
}
