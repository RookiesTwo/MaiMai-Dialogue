package top.rookiestwo.maimai_dialogue.client.scene;

import top.rookiestwo.maimai_dialogue.audio.BgmOperation;
import top.rookiestwo.maimai_dialogue.audio.SoundSpec;
import java.util.Optional;

public record AudioCue(long token, int index, int delayMs, Optional<SoundSpec> sound, Optional<BgmOperation> bgm) {
    public Key key() { return new Key(token, index); }
    public record Key(long token, int index) {}
}
