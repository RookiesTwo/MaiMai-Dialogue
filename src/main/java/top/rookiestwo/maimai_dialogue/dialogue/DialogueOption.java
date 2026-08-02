package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.Optional;

public record DialogueOption(
        String text,
        OptionIcon icon,
        Optional<String> command,
        OptionTarget target
) {
    private static final Codec<String> NON_BLANK_TEXT = Codec.STRING.comapFlatMap(
            text -> text.isBlank()
                    ? DataResult.error(() -> "Option text must not be blank.")
                    : DataResult.success(text),
            text -> text
    );
    private static final Codec<String> COMMAND = Codec.STRING.comapFlatMap(
            DialogueOption::validateCommand,
            command -> command
    );

    public static final Codec<DialogueOption> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    NON_BLANK_TEXT.fieldOf("text").forGetter(DialogueOption::text),
                    OptionIcon.CODEC.optionalFieldOf("icon", OptionIcon.NONE)
                            .forGetter(DialogueOption::icon),
                    COMMAND.optionalFieldOf("command")
                            .forGetter(DialogueOption::command),
                    OptionTarget.CODEC.fieldOf("target").forGetter(DialogueOption::target)
            ).apply(instance, DialogueOption::new)
    );

    public DialogueOption(
            String text,
            OptionIcon icon,
            OptionTarget target
    ) {
        this(text, icon, Optional.empty(), target);
    }

    public DialogueOption {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(target, "target");
        if (text.isBlank()) {
            throw new IllegalArgumentException("Option text must not be blank.");
        }
        command = command.map(DialogueOption::requireValidCommand);
    }

    private static DataResult<String> validateCommand(String command) {
        try {
            return DataResult.success(requireValidCommand(command));
        } catch (IllegalArgumentException error) {
            return DataResult.error(error::getMessage);
        }
    }

    private static String requireValidCommand(String command) {
        Objects.requireNonNull(command, "command");
        String normalized = command.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Option command must not be blank."
            );
        }
        if (normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "Option command must contain exactly one line."
            );
        }
        return normalized;
    }
}
