package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;

public enum OptionIcon {
    NONE("none"),
    QUESTION("question"),
    EXCLAMATION("exclamation"),
    DIALOGUE("dialogue");

    public static final Codec<OptionIcon> CODEC = Codec.STRING.comapFlatMap(
            OptionIcon::parse,
            OptionIcon::serializedName
    );

    private final String serializedName;

    OptionIcon(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    private static DataResult<OptionIcon> parse(String value) {
        return Arrays.stream(values())
                .filter(icon -> icon.serializedName.equals(value))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(
                        () -> "Unknown option icon '" + value + "'."
                ));
    }
}
