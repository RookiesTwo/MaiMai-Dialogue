package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneActionCall;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DialogueEnd(
        Optional<DialogueText> text,
        int typewriterIntervalMs,
        Optional<SpeakerOperation> speaker,
        List<SceneActionCall> actions,
        DialogueExit exit,
        boolean usesDefaultTypewriterInterval
) {
    public static final Codec<DialogueEnd> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    DialogueText.optionalFieldOf("text")
                            .forGetter(DialogueEnd::text),
                    DialogueStep.TYPEWRITER_INTERVAL_CODEC.optionalFieldOf(
                                    "typewriter_interval_ms"
                            )
                            .forGetter(end -> end.usesDefaultTypewriterInterval()
                                    ? Optional.empty()
                                    : Optional.of(end.typewriterIntervalMs())
                            ),
                    SpeakerOperation.CODEC.optionalFieldOf("speaker")
                            .forGetter(DialogueEnd::speaker),
                    SceneActionCall.CODEC.listOf()
                            .optionalFieldOf("actions", List.of())
                            .forGetter(DialogueEnd::actions),
                    DialogueExit.CODEC.fieldOf("exit").forGetter(DialogueEnd::exit)
            ).apply(instance, (text, interval, speaker, actions, exit) ->
                    new DialogueEnd(
                            text,
                            interval.orElse(
                                    DialogueStep.DEFAULT_TYPEWRITER_INTERVAL_MS
                            ),
                            speaker,
                            actions,
                            exit,
                            interval.isEmpty()
                    )
            )
    );

    public DialogueEnd {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(exit, "exit");
        if (typewriterIntervalMs < 0
                || typewriterIntervalMs
                > DialogueStep.MAX_TYPEWRITER_INTERVAL_MS) {
            throw new IllegalArgumentException(
                    "typewriterIntervalMs must be between 0 and 1000."
            );
        }
        actions = List.copyOf(actions);
    }

    public DialogueEnd(
            Optional<DialogueText> text,
            int typewriterIntervalMs,
            Optional<SpeakerOperation> speaker,
            List<SceneActionCall> actions,
            DialogueExit exit
    ) {
        this(text, typewriterIntervalMs, speaker, actions, exit, false);
    }

    public DialogueEnd(
            Optional<DialogueText> text,
            Optional<SpeakerOperation> speaker,
            DialogueExit exit
    ) {
        this(
                text,
                DialogueStep.DEFAULT_TYPEWRITER_INTERVAL_MS,
                speaker,
                List.of(),
                exit,
                true
        );
    }

    public DialogueEnd(
            Optional<DialogueText> text,
            Optional<SpeakerOperation> speaker,
            List<SceneActionCall> actions,
            DialogueExit exit
    ) {
        this(
                text,
                DialogueStep.DEFAULT_TYPEWRITER_INTERVAL_MS,
                speaker,
                actions,
                exit,
                true
        );
    }

    public int resolveTypewriterIntervalMs(int clientDefault) {
        if (clientDefault < 0
                || clientDefault > DialogueStep.MAX_TYPEWRITER_INTERVAL_MS) {
            throw new IllegalArgumentException(
                    "clientDefault must be between 0 and 1000."
            );
        }
        return usesDefaultTypewriterInterval
                ? clientDefault
                : typewriterIntervalMs;
    }
}
