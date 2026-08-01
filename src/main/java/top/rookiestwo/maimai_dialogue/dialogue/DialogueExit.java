package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.Arrays;

public sealed interface DialogueExit permits ReturnExit, ChoiceExit {
    Codec<DialogueExit> CODEC = Type.CODEC.dispatch(
            "type",
            DialogueExit::type,
            Type::codec
    );

    Type type();

    enum Type {
        RETURN("return", ReturnExit.CODEC),
        OPTIONS("options", ChoiceExit.CODEC);

        private static final Codec<Type> CODEC = Codec.STRING.comapFlatMap(
                Type::parse,
                Type::serializedName
        );

        private final String serializedName;
        private final MapCodec<? extends DialogueExit> codec;

        Type(String serializedName, MapCodec<? extends DialogueExit> codec) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        public String serializedName() {
            return serializedName;
        }

        public MapCodec<? extends DialogueExit> codec() {
            return codec;
        }

        private static DataResult<Type> parse(String value) {
            return Arrays.stream(values())
                    .filter(type -> type.serializedName.equals(value))
                    .findFirst()
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(
                            () -> "Unknown exit type '" + value + "'."
                    ));
        }
    }
}
