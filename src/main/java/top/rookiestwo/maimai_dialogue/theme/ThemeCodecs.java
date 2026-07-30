package top.rookiestwo.maimai_dialogue.theme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

final class ThemeCodecs {
    static final Codec<Integer> DP = range(0, 64, "dp value");
    static final Codec<Integer> POSITIVE_DP =
            range(1, 64, "positive dp value");
    static final Codec<Integer> PANEL_DP =
            range(32, 2048, "panel dp value");
    static final Codec<Integer> TEXT_SIZE =
            range(8, 64, "text size");

    private ThemeCodecs() {
    }

    private static Codec<Integer> range(
            int minimum,
            int maximum,
            String name
    ) {
        return Codec.INT.validate(value ->
                value >= minimum && value <= maximum
                        ? DataResult.success(value)
                        : DataResult.error(
                                () -> name + " must be between "
                                        + minimum + " and " + maximum + "."
                        )
        );
    }
}
