package top.rookiestwo.maimai_dialogue.client.scene;

import java.util.Objects;

public record VariantTransition(
        String target,
        String fromVariant,
        String toVariant,
        float progress
) {
    public VariantTransition {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(fromVariant, "fromVariant");
        Objects.requireNonNull(toVariant, "toVariant");
        progress = Math.clamp(progress, 0.0F, 1.0F);
    }

    public float outgoingAlpha(float opacity) {
        return opacity * (1.0F - progress);
    }

    public float incomingAlpha(float opacity) {
        float outgoing = outgoingAlpha(opacity);
        float denominator = 1.0F - outgoing;
        if (denominator <= 0.0001F) {
            return 0.0F;
        }
        return Math.clamp(
                opacity * progress / denominator,
                0.0F,
                1.0F
        );
    }
}
