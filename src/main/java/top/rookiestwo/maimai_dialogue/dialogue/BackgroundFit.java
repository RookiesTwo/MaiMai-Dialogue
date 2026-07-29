package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;

public enum BackgroundFit {
    COVER("cover"),
    CONTAIN("contain"),
    STRETCH("stretch");

    public static final Codec<BackgroundFit> CODEC =
            Codec.STRING.comapFlatMap(
                    BackgroundFit::parse,
                    BackgroundFit::serializedName
            );

    private final String serializedName;

    BackgroundFit(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    private static DataResult<BackgroundFit> parse(String value) {
        return Arrays.stream(values())
                .filter(fit -> fit.serializedName.equals(value))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(
                        () -> "Unknown background fit '" + value + "'."
                ));
    }
}
