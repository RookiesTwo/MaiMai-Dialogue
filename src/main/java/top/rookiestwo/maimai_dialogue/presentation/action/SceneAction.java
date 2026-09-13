package top.rookiestwo.maimai_dialogue.presentation.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.rookiestwo.maimai_dialogue.audio.SoundSpec;
import top.rookiestwo.maimai_dialogue.audio.BgmOperation;

import java.util.Objects;
import java.util.Optional;

public record SceneAction(
        int durationMs,
        ActionEasing easing,
        boolean blocking,
        Optional<NumericTrack> x,
        Optional<NumericTrack> y,
        Optional<NumericTrack> scale,
        Optional<NumericTrack> opacity,
        Optional<VariantChange> variant,
        Optional<VisibilityChange> visible,
        Optional<SoundSpec> sound,
        Optional<BgmOperation> bgm
) {
    private static final Codec<Integer> DURATION_CODEC = Codec.INT.validate(
            value -> value >= 0 && value <= 60_000
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "Action duration_ms must be between 0 and 60000."
                    )
    );

    public static final Codec<SceneAction> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DURATION_CODEC.optionalFieldOf("duration_ms")
                            .forGetter(value -> Optional.of(value.durationMs())),
                    ActionEasing.CODEC.optionalFieldOf(
                                    "easing",
                                    ActionEasing.LINEAR
                            )
                            .forGetter(SceneAction::easing),
                    Codec.BOOL.optionalFieldOf("blocking")
                            .forGetter(value -> Optional.of(value.blocking())),
                    NumericTrack.CODEC.optionalFieldOf("x")
                            .forGetter(SceneAction::x),
                    NumericTrack.CODEC.optionalFieldOf("y")
                            .forGetter(SceneAction::y),
                    NumericTrack.CODEC.optionalFieldOf("scale")
                            .forGetter(SceneAction::scale),
                    NumericTrack.CODEC.optionalFieldOf("opacity")
                            .forGetter(SceneAction::opacity),
                    VariantChange.CODEC.optionalFieldOf("variant")
                            .forGetter(SceneAction::variant),
                    VisibilityChange.CODEC.optionalFieldOf("visible")
                            .forGetter(SceneAction::visible),
                    SoundSpec.CODEC.optionalFieldOf("sound").forGetter(SceneAction::sound),
                    BgmOperation.CODEC.optionalFieldOf("bgm").forGetter(SceneAction::bgm)
            ).apply(instance, (duration, easing, blocking, x, y, scale, opacity, variant, visible, sound, bgm) -> {
                boolean audioOnly = (sound.isPresent() || bgm.isPresent())
                        && x.isEmpty() && y.isEmpty() && scale.isEmpty() && opacity.isEmpty()
                        && variant.isEmpty() && visible.isEmpty();
                return new SceneAction(duration.orElse(audioOnly ? 0 : 300), easing,
                        blocking.orElse(!audioOnly), x, y, scale, opacity, variant, visible, sound, bgm);
            }));

    public SceneAction {
        Objects.requireNonNull(easing, "easing");
        x = normalize(x);
        y = normalize(y);
        scale = normalize(scale);
        opacity = normalize(opacity);
        variant = normalize(variant);
        visible = normalize(visible);
        sound = normalize(sound);
        bgm = normalize(bgm);
    }

    public SceneAction(int durationMs, ActionEasing easing, boolean blocking,
                       Optional<NumericTrack> x, Optional<NumericTrack> y, Optional<NumericTrack> scale,
                       Optional<NumericTrack> opacity, Optional<VariantChange> variant, Optional<VisibilityChange> visible) {
        this(durationMs, easing, blocking, x, y, scale, opacity, variant, visible, Optional.empty(), Optional.empty());
    }

    public boolean hasVisualTracks() {
        return x.isPresent() || y.isPresent() || scale.isPresent() || opacity.isPresent() || variant.isPresent() || visible.isPresent();
    }

    public boolean audioOnly() { return !hasVisualTracks() && (sound.isPresent() || bgm.isPresent()); }

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
