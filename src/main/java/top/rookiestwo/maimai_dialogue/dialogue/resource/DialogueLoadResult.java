package top.rookiestwo.maimai_dialogue.dialogue.resource;

import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

public record DialogueLoadResult(
        DialogueSnapshot snapshot,
        List<DialogueLoadError> errors
) {
    public DialogueLoadResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(errors, "errors");
        errors = List.copyOf(errors);
    }

    public void logErrors(Logger logger) {
        Objects.requireNonNull(logger, "logger");
        for (DialogueLoadError error : errors) {
            logger.error(
                    "Failed to load dialogue resource {}: {}",
                    error.resourceId(),
                    error.message()
            );
        }
    }
}
