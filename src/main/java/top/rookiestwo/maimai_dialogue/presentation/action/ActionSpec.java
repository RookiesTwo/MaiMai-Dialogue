package top.rookiestwo.maimai_dialogue.presentation.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Objects;

public sealed interface ActionSpec
        permits ActionSpec.Reference, ActionSpec.Inline {
    Codec<ActionSpec> CODEC = Type.CODEC.dispatch(
            "type",
            ActionSpec::type,
            Type::codec
    );

    Type type();

    record Reference(ResourceLocation id) implements ActionSpec {
        private static final MapCodec<Reference> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("id")
                                .forGetter(Reference::id)
                ).apply(instance, Reference::new));

        public Reference {
            Objects.requireNonNull(id, "id");
        }

        @Override
        public Type type() {
            return Type.REFERENCE;
        }
    }

    record Inline(SceneAction action) implements ActionSpec {
        private static final MapCodec<Inline> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        SceneAction.CODEC.fieldOf("action")
                                .forGetter(Inline::action)
                ).apply(instance, Inline::new));

        public Inline {
            Objects.requireNonNull(action, "action");
        }

        @Override
        public Type type() {
            return Type.INLINE;
        }
    }

    enum Type {
        REFERENCE("reference", Reference.CODEC),
        INLINE("inline", Inline.CODEC);

        private static final Codec<Type> CODEC =
                Codec.STRING.comapFlatMap(
                        Type::parse,
                        type -> type.serializedName
                );

        private final String serializedName;
        private final MapCodec<? extends ActionSpec> codec;

        Type(
                String serializedName,
                MapCodec<? extends ActionSpec> codec
        ) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        public MapCodec<? extends ActionSpec> codec() {
            return codec;
        }

        private static DataResult<Type> parse(String value) {
            return Arrays.stream(values())
                    .filter(type -> type.serializedName.equals(value))
                    .findFirst()
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(
                            () -> "Unknown action definition type '"
                                    + value + "'."
                    ));
        }
    }
}
