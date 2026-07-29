package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.Arrays;

public sealed interface Exit permits ReturnExit, OptionsExit {
    Codec<Exit> CODEC = Type.CODEC.dispatch(
            "type",
            Exit::type,
            Type::codec
    );

    Type type();

    enum Type {
        RETURN("return", ReturnExit.CODEC),
        OPTIONS("options", OptionsExit.CODEC);

        private static final Codec<Type> CODEC = Codec.STRING.comapFlatMap(
                Type::parse,
                Type::serializedName
        );

        private final String serializedName;
        private final MapCodec<? extends Exit> codec;

        Type(String serializedName, MapCodec<? extends Exit> codec) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        public String serializedName() {
            return serializedName;
        }

        public MapCodec<? extends Exit> codec() {
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
