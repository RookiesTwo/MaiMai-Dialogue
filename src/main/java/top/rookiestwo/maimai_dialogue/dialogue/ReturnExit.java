package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.MapCodec;

public record ReturnExit() implements DialogueExit {
    public static final ReturnExit INSTANCE = new ReturnExit();
    public static final MapCodec<ReturnExit> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public Type type() {
        return Type.RETURN;
    }
}
