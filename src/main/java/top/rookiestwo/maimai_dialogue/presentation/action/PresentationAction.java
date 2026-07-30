package top.rookiestwo.maimai_dialogue.presentation.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.Optional;

public record PresentationAction(
        int durationMs,
        ActionEasing easing,
        boolean blocking,
        Optional<NumericTrack> x,
        Optional<NumericTrack> y,
        Optional<NumericTrack> scale,
        Optional<NumericTrack> opacity,
        Optional<VariantChange> variant,
        Optional<VisibilityChange> visible
) {
    private static final Codec<Integer> DURATION_CODEC = Codec.INT.validate(
            value -> value >= 0 && value <= 60_000
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "Action duration_ms must be between 0 and 60000."
                    )
    );

    public static final Codec<PresentationAction> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DURATION_CODEC.optionalFieldOf("duration_ms", 300)
                            .forGetter(PresentationAction::durationMs),
                    ActionEasing.CODEC.optionalFieldOf(
                                    "easing",
                                    ActionEasing.LINEAR
                            )
                            .forGetter(PresentationAction::easing),
                    Codec.BOOL.optionalFieldOf("blocking", true)
                            .forGetter(PresentationAction::blocking),
                    NumericTrack.CODEC.optionalFieldOf("x")
                            .forGetter(PresentationAction::x),
                    NumericTrack.CODEC.optionalFieldOf("y")
                            .forGetter(PresentationAction::y),
                    NumericTrack.CODEC.optionalFieldOf("scale")
                            .forGetter(PresentationAction::scale),
                    NumericTrack.CODEC.optionalFieldOf("opacity")
                            .forGetter(PresentationAction::opacity),
                    VariantChange.CODEC.optionalFieldOf("variant")
                            .forGetter(PresentationAction::variant),
                    VisibilityChange.CODEC.optionalFieldOf("visible")
                            .forGetter(PresentationAction::visible)
            ).apply(instance, PresentationAction::new));

    public PresentationAction {
        Objects.requireNonNull(easing, "easing");
        x = normalize(x);
        y = normalize(y);
        scale = normalize(scale);
        opacity = normalize(opacity);
        variant = normalize(variant);
        visible = normalize(visible);
    }

    public boolean writes(ActionProperty property) {
        return switch (property) {
            case X -> x.isPresent();
            case Y -> y.isPresent();
            case SCALE -> scale.isPresent();
            case OPACITY -> opacity.isPresent();
            case VARIANT -> variant.isPresent();
            case VISIBLE -> visible.isPresent();
        };
    }

    private static <T> Optional<T> normalize(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }
}
