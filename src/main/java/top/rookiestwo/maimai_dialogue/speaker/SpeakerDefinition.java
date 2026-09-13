package top.rookiestwo.maimai_dialogue.speaker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.Optional;
import top.rookiestwo.maimai_dialogue.audio.TypewriterSound;

public record SpeakerDefinition(String name, Optional<TypewriterSound> typewriterSound) {
    private static final Codec<String> NAME_CODEC = Codec.STRING.validate(
            value -> value.isBlank()
                    ? DataResult.error(() -> "Speaker name must not be blank.")
                    : DataResult.success(value)
    );

    public static final Codec<SpeakerDefinition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            NAME_CODEC.fieldOf("name")
                                    .forGetter(SpeakerDefinition::name),
                            TypewriterSound.CODEC.optionalFieldOf("typewriter_sound").forGetter(SpeakerDefinition::typewriterSound)
                    ).apply(instance, SpeakerDefinition::new)
            );

    public SpeakerDefinition {
        Objects.requireNonNull(typewriterSound, "typewriterSound");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "Speaker name must not be blank."
            );
        }
    }

    public SpeakerDefinition(String name) { this(name, Optional.empty()); }
}
