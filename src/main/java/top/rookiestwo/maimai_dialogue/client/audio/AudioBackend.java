package top.rookiestwo.maimai_dialogue.client.audio;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.audio.SoundSpec;

// 隔离 Minecraft 声音实例，让调度与淡化可使用假播放器测试。
public interface AudioBackend {
    boolean available(ResourceLocation sound);
    Voice play(SoundSpec sound, boolean music, boolean loop, float initialVolume);
    default boolean musicAudible() { return true; }

    interface Voice {
        void volume(float volume);
        void looping(boolean loop);
        boolean active();
        void stop();
    }
}
