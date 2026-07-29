package top.rookiestwo.maimai_dialogue.client;

import top.rookiestwo.maimai_dialogue.dialogue.DialogueOption;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DialogueViewState(
        Optional<String> speaker,
        Optional<String> text,
        Optional<String> error,
        List<DialogueOption> options,
        boolean loadingOptions,
        boolean requestingTarget
) {
    public DialogueViewState {
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(options, "options");
        options = List.copyOf(options);
    }
}
