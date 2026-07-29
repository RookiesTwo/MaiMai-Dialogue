package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.MapCodec;

public record HideSpeaker() implements SpeakerOperation {
    public static final HideSpeaker INSTANCE = new HideSpeaker();
    public static final MapCodec<HideSpeaker> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public Type type() {
        return Type.HIDE;
    }
}
