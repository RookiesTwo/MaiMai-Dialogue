package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.rookiestwo.maimai_dialogue.presentation.action.ActionCall;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record EndStep(
        Optional<String> text,
        Optional<SpeakerOperation> speaker,
        List<ActionCall> actions,
        Exit exit
) {
    public static final Codec<EndStep> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf("text").forGetter(EndStep::text),
                    SpeakerOperation.CODEC.optionalFieldOf("speaker")
                            .forGetter(EndStep::speaker),
                    ActionCall.CODEC.listOf()
                            .optionalFieldOf("actions", List.of())
                            .forGetter(EndStep::actions),
                    Exit.CODEC.fieldOf("exit").forGetter(EndStep::exit)
            ).apply(instance, EndStep::new)
    );

    public EndStep {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(exit, "exit");
        actions = List.copyOf(actions);
    }

    public EndStep(
            Optional<String> text,
            Optional<SpeakerOperation> speaker,
            Exit exit
    ) {
        this(text, speaker, List.of(), exit);
    }
}
