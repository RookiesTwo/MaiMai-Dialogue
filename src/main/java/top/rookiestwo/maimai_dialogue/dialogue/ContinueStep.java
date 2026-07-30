package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.rookiestwo.maimai_dialogue.presentation.action.ActionCall;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ContinueStep(
        Optional<String> text,
        Optional<SpeakerOperation> speaker,
        List<ActionCall> actions
) {
    public static final Codec<ContinueStep> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf("text").forGetter(ContinueStep::text),
                    SpeakerOperation.CODEC.optionalFieldOf("speaker")
                            .forGetter(ContinueStep::speaker),
                    ActionCall.CODEC.listOf()
                            .optionalFieldOf("actions", List.of())
                            .forGetter(ContinueStep::actions)
            ).apply(instance, ContinueStep::new)
    );

    public ContinueStep {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(actions, "actions");
        actions = List.copyOf(actions);
    }

    public ContinueStep(
            Optional<String> text,
            Optional<SpeakerOperation> speaker
    ) {
        this(text, speaker, List.of());
    }
}
