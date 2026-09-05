package top.rookiestwo.maimai_dialogue.dialogue.branch;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record DialogueTarget(ResourceLocation dialogue) implements OptionTarget {
    public static final MapCodec<DialogueTarget> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("dialogue")
                            .forGetter(DialogueTarget::dialogue)
            ).apply(instance, DialogueTarget::new)
    );

    public DialogueTarget {
        Objects.requireNonNull(dialogue, "dialogue");
    }

    @Override
    public Type type() {
        return Type.DIALOGUE;
    }
}
