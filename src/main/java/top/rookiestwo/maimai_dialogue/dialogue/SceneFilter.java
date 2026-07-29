package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.Arrays;

public sealed interface SceneFilter permits ColorAdjustFilter, CrtFilter {
    Codec<SceneFilter> CODEC = Type.CODEC.dispatch(
            "type",
            SceneFilter::type,
            Type::codec
    );

    Type type();

    enum Type {
        COLOR_ADJUST("color_adjust", ColorAdjustFilter.CODEC),
        CRT("crt", CrtFilter.CODEC);

        private static final Codec<Type> CODEC = Codec.STRING.comapFlatMap(
                Type::parse,
                type -> type.serializedName
        );

        private final String serializedName;
        private final MapCodec<? extends SceneFilter> codec;

        Type(
                String serializedName,
                MapCodec<? extends SceneFilter> codec
        ) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        public MapCodec<? extends SceneFilter> codec() {
            return codec;
        }

        private static DataResult<Type> parse(String value) {
            return Arrays.stream(values())
                    .filter(type -> type.serializedName.equals(value))
                    .findFirst()
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(
                            () -> "Unknown scene filter type '" + value + "'."
                    ));
        }
    }
}
