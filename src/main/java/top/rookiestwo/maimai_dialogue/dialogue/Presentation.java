package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record Presentation(ResourceLocation theme) {
    public static final Codec<Presentation> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("theme").forGetter(Presentation::theme)
            ).apply(instance, Presentation::new)
    );

    public Presentation {
        Objects.requireNonNull(theme, "theme");
    }
}
