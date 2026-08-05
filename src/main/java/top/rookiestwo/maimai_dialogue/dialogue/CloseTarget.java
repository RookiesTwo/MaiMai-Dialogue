package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.MapCodec;

public record CloseTarget() implements OptionTarget {
    public static final CloseTarget INSTANCE = new CloseTarget();
    public static final MapCodec<CloseTarget> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public Type type() {
        return Type.CLOSE;
    }
}
