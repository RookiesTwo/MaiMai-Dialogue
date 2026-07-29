package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.Optional;

public record ContinueStep(
        Optional<String> text,
        Optional<SpeakerOperation> speaker
) {
    public static final Codec<ContinueStep> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf("text").forGetter(ContinueStep::text),
                    SpeakerOperation.CODEC.optionalFieldOf("speaker")
                            .forGetter(ContinueStep::speaker)
            ).apply(instance, ContinueStep::new)
    );

    public ContinueStep {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(speaker, "speaker");
    }
}
