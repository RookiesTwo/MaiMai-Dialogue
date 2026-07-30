package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;

public enum VisualSampling {
    LINEAR("linear"),
    NEAREST("nearest");

    public static final Codec<VisualSampling> CODEC =
            Codec.STRING.comapFlatMap(
                    VisualSampling::parse,
                    VisualSampling::serializedName
            );

    private final String serializedName;

    VisualSampling(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    private static DataResult<VisualSampling> parse(String value) {
        return Arrays.stream(values())
                .filter(sampling -> sampling.serializedName.equals(value))
                .findFirst()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(
                        () -> "Unknown visual sampling '" + value + "'."
                ));
    }
}
