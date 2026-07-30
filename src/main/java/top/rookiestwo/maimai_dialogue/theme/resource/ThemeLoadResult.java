package top.rookiestwo.maimai_dialogue.theme.resource;

import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

public record ThemeLoadResult(
        ThemeSnapshot snapshot,
        List<ThemeLoadError> errors
) {
    public ThemeLoadResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(errors, "errors");
        errors = List.copyOf(errors);
    }

    public void logErrors(Logger logger) {
        Objects.requireNonNull(logger, "logger");
        for (ThemeLoadError error : errors) {
            logger.error(
                    "Failed to load dialogue theme resource {}: {}",
                    error.resourceId(),
                    error.message()
            );
        }
    }
}
