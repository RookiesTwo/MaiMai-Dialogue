package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

public record DialogueOption(
        String text,
        OptionIcon icon,
        OptionTarget target
) {
    private static final Codec<String> NON_BLANK_TEXT = Codec.STRING.comapFlatMap(
            text -> text.isBlank()
                    ? DataResult.error(() -> "Option text must not be blank.")
                    : DataResult.success(text),
            text -> text
    );

    public static final Codec<DialogueOption> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    NON_BLANK_TEXT.fieldOf("text").forGetter(DialogueOption::text),
                    OptionIcon.CODEC.optionalFieldOf("icon", OptionIcon.NONE)
                            .forGetter(DialogueOption::icon),
                    OptionTarget.CODEC.fieldOf("target").forGetter(DialogueOption::target)
            ).apply(instance, DialogueOption::new)
    );

    public DialogueOption {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(target, "target");
        if (text.isBlank()) {
            throw new IllegalArgumentException("Option text must not be blank.");
        }
    }
}
