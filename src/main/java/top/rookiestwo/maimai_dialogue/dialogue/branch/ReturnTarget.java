package top.rookiestwo.maimai_dialogue.dialogue.branch;

import com.mojang.serialization.MapCodec;

public record ReturnTarget() implements OptionTarget {
    public static final ReturnTarget INSTANCE = new ReturnTarget();
    public static final MapCodec<ReturnTarget> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public Type type() {
        return Type.RETURN;
    }
}
