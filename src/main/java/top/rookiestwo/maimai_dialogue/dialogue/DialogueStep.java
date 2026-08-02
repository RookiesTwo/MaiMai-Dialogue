package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneActionCall;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DialogueStep(
        Optional<DialogueText> text,
        Optional<SpeakerOperation> speaker,
        List<SceneActionCall> actions
) {
    public static final Codec<DialogueStep> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    DialogueText.optionalFieldOf("text")
                            .forGetter(DialogueStep::text),
                    SpeakerOperation.CODEC.optionalFieldOf("speaker")
                            .forGetter(DialogueStep::speaker),
                    SceneActionCall.CODEC.listOf()
                            .optionalFieldOf("actions", List.of())
                            .forGetter(DialogueStep::actions)
            ).apply(instance, DialogueStep::new)
    );

    public DialogueStep {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(actions, "actions");
        actions = List.copyOf(actions);
    }

    public DialogueStep(
            Optional<DialogueText> text,
            Optional<SpeakerOperation> speaker
    ) {
        this(text, speaker, List.of());
    }
}
