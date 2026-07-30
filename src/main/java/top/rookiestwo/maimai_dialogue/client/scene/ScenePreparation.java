package top.rookiestwo.maimai_dialogue.client.scene;

import java.util.List;
import java.util.Objects;

public record ScenePreparation(
        ScenePlayback playback,
        List<String> errors
) {
    public ScenePreparation {
        Objects.requireNonNull(playback, "playback");
        Objects.requireNonNull(errors, "errors");
        errors = List.copyOf(errors);
    }
}
