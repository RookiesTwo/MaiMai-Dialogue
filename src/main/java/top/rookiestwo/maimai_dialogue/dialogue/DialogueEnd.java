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
        DialogueExit exit
) {
    public static final Codec<DialogueEnd> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    DialogueText.optionalFieldOf("text")
                            .forGetter(DialogueEnd::text),
                    DialogueStep.TYPEWRITER_INTERVAL_CODEC.optionalFieldOf(
                                    "typewriter_interval_ms",
                                    DialogueStep.DEFAULT_TYPEWRITER_INTERVAL_MS
                            )
                            .forGetter(DialogueEnd::typewriterIntervalMs),
                    SpeakerOperation.CODEC.optionalFieldOf("speaker")
                            .forGetter(DialogueEnd::speaker),
                    SceneActionCall.CODEC.listOf()
                            .optionalFieldOf("actions", List.of())
                            .forGetter(DialogueEnd::actions),
                    DialogueExit.CODEC.fieldOf("exit").forGetter(DialogueEnd::exit)
            ).apply(instance, DialogueEnd::new)
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
            Optional<SpeakerOperation> speaker,
            DialogueExit exit
    ) {
        this(
                text,
                DialogueStep.DEFAULT_TYPEWRITER_INTERVAL_MS,
                speaker,
                List.of(),
                exit
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
                exit
        );
    }
}
