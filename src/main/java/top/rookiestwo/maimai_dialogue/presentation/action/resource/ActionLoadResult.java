package top.rookiestwo.maimai_dialogue.presentation.action.resource;

import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

public record ActionLoadResult(
        ActionSnapshot snapshot,
        List<ActionLoadError> errors
) {
    public ActionLoadResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(errors, "errors");
        errors = List.copyOf(errors);
    }

    public void logErrors(Logger logger) {
        Objects.requireNonNull(logger, "logger");
        for (ActionLoadError error : errors) {
            logger.error(
                    "Failed to load presentation action resource {}: {}",
                    error.resourceId(),
                    error.message()
            );
        }
    }
}
