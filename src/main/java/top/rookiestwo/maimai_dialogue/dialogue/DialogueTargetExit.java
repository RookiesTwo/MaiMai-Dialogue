package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record DialogueTargetExit(ResourceLocation dialogue)
        implements DialogueExit {
    public static final MapCodec<DialogueTargetExit> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("dialogue")
                            .forGetter(DialogueTargetExit::dialogue)
            ).apply(instance, DialogueTargetExit::new));

    public DialogueTargetExit {
        Objects.requireNonNull(dialogue, "dialogue");
    }

    @Override
    public Type type() {
        return Type.DIALOGUE;
    }
}
