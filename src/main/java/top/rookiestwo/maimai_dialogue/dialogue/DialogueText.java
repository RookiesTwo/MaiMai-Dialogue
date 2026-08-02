package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

public record DialogueText(List<String> variants) {
    private static final Codec<List<String>> RANDOM_CODEC =
            Codec.STRING.listOf().validate(variants -> variants.isEmpty()
                    ? DataResult.error(
                            () -> "Dialogue text variants must not be empty."
                    )
                    : DataResult.success(variants));

    public static final Codec<DialogueText> CODEC = Codec.either(
            Codec.STRING,
            RANDOM_CODEC
    ).xmap(
            value -> value.map(DialogueText::fixed, DialogueText::new),
            text -> text.variants.size() == 1
                    ? Either.left(text.variants.getFirst())
                    : Either.right(text.variants)
    );

    public DialogueText {
        Objects.requireNonNull(variants, "variants");
        if (variants.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dialogue text variants must not be empty."
            );
        }
        variants = List.copyOf(variants);
    }

    public static DialogueText fixed(String text) {
        return new DialogueText(List.of(Objects.requireNonNull(text, "text")));
    }

    // 区分缺失字段与显式 JSON null，避免把无效正文静默当作缺省值。
    public static MapCodec<Optional<DialogueText>> optionalFieldOf(
            String name
    ) {
        Objects.requireNonNull(name, "name");
        return new MapCodec<>() {
            @Override
            public <T> DataResult<Optional<DialogueText>> decode(
                    DynamicOps<T> ops,
                    MapLike<T> input
            ) {
                Optional<T> value = input.entries()
                        .filter(entry -> ops.getStringValue(entry.getFirst())
                                .result()
                                .filter(name::equals)
                                .isPresent())
                        .map(Pair::getSecond)
                        .findFirst();
                if (value.isEmpty()) {
                    return DataResult.success(Optional.empty());
                }
                return CODEC.parse(ops, value.orElseThrow()).map(Optional::of);
            }

            @Override
            public <T> RecordBuilder<T> encode(
                    Optional<DialogueText> input,
                    DynamicOps<T> ops,
                    RecordBuilder<T> prefix
            ) {
                return input.isPresent()
                        ? prefix.add(name, CODEC.encodeStart(
                                ops,
                                input.orElseThrow()
                        ))
                        : prefix;
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }
        };
    }

    public String select(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        return variants.get(random.nextInt(variants.size()));
    }
}
