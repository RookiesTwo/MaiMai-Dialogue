package top.rookiestwo.maimai_dialogue.speaker.resource;

import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

public record SpeakerLoadResult(
        SpeakerSnapshot snapshot,
        List<SpeakerLoadError> errors
) {
    public SpeakerLoadResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(errors, "errors");
        errors = List.copyOf(errors);
    }

    public void logErrors(Logger logger) {
        Objects.requireNonNull(logger, "logger");
        for (SpeakerLoadError error : errors) {
            logger.error(
                    "Failed to load speaker resource {}: {}",
                    error.resourceId(),
                    error.message()
            );
        }
    }
}
