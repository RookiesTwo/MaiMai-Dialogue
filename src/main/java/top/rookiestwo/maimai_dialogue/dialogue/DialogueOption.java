package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public record DialogueOption(
        String text,
        OptionIcon icon,
        List<String> commands,
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
    private static final Codec<List<String>> COMMANDS = Codec.either(
            COMMAND,
            COMMAND.listOf().validate(commands -> commands.isEmpty()
                    ? DataResult.error(
                            () -> "Option commands must not be empty."
                    )
                    : DataResult.success(commands))
    ).xmap(
            value -> value.map(List::of, List::copyOf),
            commands -> commands.size() == 1
                    ? Either.left(commands.getFirst())
                    : Either.right(commands)
    );

    public static final Codec<DialogueOption> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    NON_BLANK_TEXT.fieldOf("text").forGetter(DialogueOption::text),
                    OptionIcon.CODEC.optionalFieldOf("icon", OptionIcon.NONE)
                            .forGetter(DialogueOption::icon),
                    optionalCommandsField()
                            .forGetter(DialogueOption::commands),
                    OptionTarget.CODEC.fieldOf("target").forGetter(DialogueOption::target)
            ).apply(instance, DialogueOption::new)
    );

    public DialogueOption(
            String text,
            OptionIcon icon,
            Optional<String> command,
            OptionTarget target
    ) {
        this(
                text,
                icon,
                Objects.requireNonNull(command, "command")
                        .map(List::of)
                        .orElseGet(List::of),
                target
        );
    }

    public DialogueOption(
            String text,
            OptionIcon icon,
            OptionTarget target
    ) {
        this(text, icon, List.of(), target);
    }

    public DialogueOption {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(target, "target");
        if (text.isBlank()) {
            throw new IllegalArgumentException("Option text must not be blank.");
        }
        commands = commands.stream()
                .map(DialogueOption::requireValidCommand)
                .toList();
    }

    // 区分字段缺失与显式 JSON null，并兼容单条字符串和指令数组。
    private static MapCodec<List<String>> optionalCommandsField() {
        return new MapCodec<>() {
            @Override
            public <T> DataResult<List<String>> decode(
                    DynamicOps<T> ops,
                    MapLike<T> input
            ) {
                Optional<T> value = input.entries()
                        .filter(entry -> ops.getStringValue(entry.getFirst())
                                .result()
                                .filter("command"::equals)
                                .isPresent())
                        .map(Pair::getSecond)
                        .findFirst();
                if (value.isEmpty()) {
                    return DataResult.success(List.of());
                }
                return COMMANDS.parse(ops, value.orElseThrow());
            }

            @Override
            public <T> RecordBuilder<T> encode(
                    List<String> input,
                    DynamicOps<T> ops,
                    RecordBuilder<T> prefix
            ) {
                return input.isEmpty()
                        ? prefix
                        : prefix.add(
                                "command",
                                COMMANDS.encodeStart(ops, input)
                        );
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString("command"));
            }
        };
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
