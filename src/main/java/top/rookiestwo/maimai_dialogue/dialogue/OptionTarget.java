package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.Arrays;

public sealed interface OptionTarget permits
        DialogueTarget,
        ReturnTarget,
        CloseTarget {
    Codec<OptionTarget> CODEC = Type.CODEC.dispatch(
            "type",
            OptionTarget::type,
            Type::codec
    );

    Type type();

    enum Type {
        DIALOGUE("dialogue", DialogueTarget.CODEC),
        RETURN("return", ReturnTarget.CODEC),
        CLOSE("close", CloseTarget.CODEC);

        private static final Codec<Type> CODEC = Codec.STRING.comapFlatMap(
                Type::parse,
                Type::serializedName
        );

        private final String serializedName;
        private final MapCodec<? extends OptionTarget> codec;

        Type(String serializedName, MapCodec<? extends OptionTarget> codec) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        public String serializedName() {
            return serializedName;
        }

        public MapCodec<? extends OptionTarget> codec() {
            return codec;
        }

        private static DataResult<Type> parse(String value) {
            return Arrays.stream(values())
                    .filter(type -> type.serializedName.equals(value))
                    .findFirst()
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(
                            () -> "Unknown option target type '" + value + "'."
                    ));
        }
    }
}
