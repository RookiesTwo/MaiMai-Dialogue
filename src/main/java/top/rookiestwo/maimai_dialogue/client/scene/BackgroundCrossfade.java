package top.rookiestwo.maimai_dialogue.client.scene;

import java.util.Objects;

public record BackgroundCrossfade(
        SceneBackgroundState from,
        SceneBackgroundState to,
        float progress
) {
    public BackgroundCrossfade {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        progress = Math.clamp(progress, 0.0F, 1.0F);
    }
}
