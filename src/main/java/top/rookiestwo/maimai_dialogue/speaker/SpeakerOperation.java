package top.rookiestwo.maimai_dialogue.speaker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.Arrays;

public sealed interface SpeakerOperation permits SetSpeaker, HideSpeaker {
    Codec<SpeakerOperation> CODEC = Type.CODEC.dispatch(
            "type",
            SpeakerOperation::type,
            Type::codec
    );

    Type type();

    enum Type {
        SET("set", SetSpeaker.CODEC),
        HIDE("hide", HideSpeaker.CODEC);

        private static final Codec<Type> CODEC = Codec.STRING.comapFlatMap(
                Type::parse,
                Type::serializedName
        );

        private final String serializedName;
        private final MapCodec<? extends SpeakerOperation> codec;

        Type(String serializedName, MapCodec<? extends SpeakerOperation> codec) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        public String serializedName() {
            return serializedName;
        }

        public MapCodec<? extends SpeakerOperation> codec() {
            return codec;
        }

        private static DataResult<Type> parse(String value) {
            return Arrays.stream(values())
                    .filter(type -> type.serializedName.equals(value))
                    .findFirst()
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(
                            () -> "Unknown speaker operation type '" + value + "'."
                    ));
        }
    }
}
