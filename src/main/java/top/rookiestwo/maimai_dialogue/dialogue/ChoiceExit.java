package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Objects;

public record ChoiceExit(List<DialogueOption> options) implements DialogueExit {
    private static final Codec<List<DialogueOption>> NON_EMPTY_OPTIONS =
            DialogueOption.CODEC.listOf().comapFlatMap(
                    options -> options.isEmpty()
                            ? DataResult.error(
                                    () -> "ChoiceExit must contain at least one option."
                            )
                            : DataResult.success(List.copyOf(options)),
                    List::copyOf
            );

    public static final MapCodec<ChoiceExit> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    NON_EMPTY_OPTIONS.fieldOf("options").forGetter(ChoiceExit::options)
            ).apply(instance, ChoiceExit::new)
    );

    public ChoiceExit {
        Objects.requireNonNull(options, "options");
        if (options.isEmpty()) {
            throw new IllegalArgumentException(
                    "ChoiceExit must contain at least one option."
            );
        }
        options = List.copyOf(options);
    }

    @Override
    public Type type() {
        return Type.OPTIONS;
    }
}
