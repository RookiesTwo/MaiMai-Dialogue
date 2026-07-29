package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.Optional;

public record EndStep(
        Optional<String> text,
        Optional<SpeakerOperation> speaker,
        Exit exit
) {
    public static final Codec<EndStep> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf("text").forGetter(EndStep::text),
                    SpeakerOperation.CODEC.optionalFieldOf("speaker")
                            .forGetter(EndStep::speaker),
                    Exit.CODEC.fieldOf("exit").forGetter(EndStep::exit)
            ).apply(instance, EndStep::new)
    );

    public EndStep {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(exit, "exit");
    }
}
