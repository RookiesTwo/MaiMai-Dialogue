package top.rookiestwo.maimai_dialogue.presentation.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;

public enum ActionEasing {
    LINEAR("linear"),
    EASE_IN("ease_in"),
    EASE_OUT("ease_out"),
    EASE_IN_OUT("ease_in_out");

    public static final Codec<ActionEasing> CODEC =
            Codec.STRING.comapFlatMap(
                    ActionEasing::parse,
                    ActionEasing::serializedName
            );

    private final String serializedName;

    ActionEasing(String serializedName) {
        this.serializedName = serializedName;
    }

    public float apply(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return switch (this) {
            case LINEAR -> clamped;
            case EASE_IN -> clamped * clamped;
            case EASE_OUT -> 1.0F
                    - (1.0F - clamped) * (1.0F - clamped);
            case EASE_IN_OUT -> clamped < 0.5F
                    ? 2.0F * clamped * clamped
                    : 1.0F - (float) Math.pow(-2.0F * clamped + 2.0F, 2)
                            / 2.0F;
        };
    }

    public String serializedName() {
        return serializedName;
    }

    private static DataResult<ActionEasing> parse(String value) {
        return Arrays.stream(values())
                .filter(easing -> easing.serializedName.equals(value))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(
                        () -> "Unknown action easing '" + value + "'."
                ));
    }
}
