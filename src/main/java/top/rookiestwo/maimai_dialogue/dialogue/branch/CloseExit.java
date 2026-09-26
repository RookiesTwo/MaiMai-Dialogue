package top.rookiestwo.maimai_dialogue.dialogue.branch;

import com.mojang.serialization.MapCodec;

public record CloseExit() implements DialogueExit {
    public static final CloseExit INSTANCE = new CloseExit();
    public static final MapCodec<CloseExit> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public Type type() {
        return Type.CLOSE;
    }
}
