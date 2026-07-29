package top.rookiestwo.maimai_dialogue.speaker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

public record SpeakerDefinition(String name) {
    private static final Codec<String> NAME_CODEC = Codec.STRING.validate(
            value -> value.isBlank()
                    ? DataResult.error(() -> "Speaker name must not be blank.")
                    : DataResult.success(value)
    );

    public static final Codec<SpeakerDefinition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            NAME_CODEC.fieldOf("name")
                                    .forGetter(SpeakerDefinition::name)
                    ).apply(instance, SpeakerDefinition::new)
            );

    public SpeakerDefinition {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "Speaker name must not be blank."
            );
        }
    }
}
