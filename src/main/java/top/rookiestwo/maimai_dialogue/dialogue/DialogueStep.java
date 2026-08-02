package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneActionCall;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DialogueStep(
        Optional<DialogueText> text,
        int typewriterIntervalMs,
        Optional<SpeakerOperation> speaker,
        List<SceneActionCall> actions,
        boolean usesDefaultTypewriterInterval
) {
    public static final int DEFAULT_TYPEWRITER_INTERVAL_MS = 30;
    public static final int MAX_TYPEWRITER_INTERVAL_MS = 1_000;
    static final Codec<Integer> TYPEWRITER_INTERVAL_CODEC =
            Codec.INT.validate(value -> isValidTypewriterInterval(value)
                    ? DataResult.success(value)
                    : DataResult.error(() ->
                            "typewriter_interval_ms must be between 0 and 1000."
                    ));

    public static final Codec<DialogueStep> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    DialogueText.optionalFieldOf("text")
                            .forGetter(DialogueStep::text),
                    TYPEWRITER_INTERVAL_CODEC.optionalFieldOf(
                                    "typewriter_interval_ms"
                            )
                            .forGetter(step ->
                                    step.usesDefaultTypewriterInterval()
                                            ? Optional.empty()
                                            : Optional.of(
                                                    step.typewriterIntervalMs()
                                            )
                            ),
                    SpeakerOperation.CODEC.optionalFieldOf("speaker")
                            .forGetter(DialogueStep::speaker),
                    SceneActionCall.CODEC.listOf()
                            .optionalFieldOf("actions", List.of())
                            .forGetter(DialogueStep::actions)
            ).apply(instance, (text, interval, speaker, actions) ->
                    new DialogueStep(
                            text,
                            interval.orElse(DEFAULT_TYPEWRITER_INTERVAL_MS),
                            speaker,
                            actions,
                            interval.isEmpty()
                    )
            )
    );

    public DialogueStep {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(actions, "actions");
        if (!isValidTypewriterInterval(typewriterIntervalMs)) {
            throw new IllegalArgumentException(
                    "typewriterIntervalMs must be between 0 and 1000."
            );
        }
        actions = List.copyOf(actions);
    }

    public DialogueStep(
            Optional<DialogueText> text,
            int typewriterIntervalMs,
            Optional<SpeakerOperation> speaker,
            List<SceneActionCall> actions
    ) {
        this(text, typewriterIntervalMs, speaker, actions, false);
    }

    public DialogueStep(
            Optional<DialogueText> text,
            Optional<SpeakerOperation> speaker
    ) {
        this(
                text,
                DEFAULT_TYPEWRITER_INTERVAL_MS,
                speaker,
                List.of(),
                true
        );
    }

    public DialogueStep(
            Optional<DialogueText> text,
            Optional<SpeakerOperation> speaker,
            List<SceneActionCall> actions
    ) {
        this(
                text,
                DEFAULT_TYPEWRITER_INTERVAL_MS,
                speaker,
                actions,
                true
        );
    }

    public int resolveTypewriterIntervalMs(int clientDefault) {
        return usesDefaultTypewriterInterval
                ? validateClientDefault(clientDefault)
                : typewriterIntervalMs;
    }

    private static int validateClientDefault(int value) {
        if (!isValidTypewriterInterval(value)) {
            throw new IllegalArgumentException(
                    "clientDefault must be between 0 and 1000."
            );
        }
        return value;
    }

    private static boolean isValidTypewriterInterval(int value) {
        return value >= 0 && value <= MAX_TYPEWRITER_INTERVAL_MS;
    }
}
