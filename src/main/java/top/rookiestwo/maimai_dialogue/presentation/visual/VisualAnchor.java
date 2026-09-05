package top.rookiestwo.maimai_dialogue.presentation.visual;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;

public enum VisualAnchor {
    TOP_LEFT("top_left"),
    TOP_CENTER("top_center"),
    TOP_RIGHT("top_right"),
    CENTER_LEFT("center_left"),
    CENTER("center"),
    CENTER_RIGHT("center_right"),
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_CENTER("bottom_center"),
    BOTTOM_RIGHT("bottom_right");

    public static final Codec<VisualAnchor> CODEC = Codec.STRING.comapFlatMap(
            VisualAnchor::parse,
            VisualAnchor::serializedName
    );

    private final String serializedName;

    VisualAnchor(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    private static DataResult<VisualAnchor> parse(String value) {
        return Arrays.stream(values())
                .filter(anchor -> anchor.serializedName.equals(value))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(
                        () -> "Unknown visual anchor '" + value + "'."
                ));
    }
}
