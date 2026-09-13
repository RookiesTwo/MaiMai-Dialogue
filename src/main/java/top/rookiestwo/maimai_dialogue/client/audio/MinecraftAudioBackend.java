package top.rookiestwo.maimai_dialogue.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import top.rookiestwo.maimai_dialogue.audio.SoundSpec;

public final class MinecraftAudioBackend implements AudioBackend {
    @Override
    public boolean available(ResourceLocation sound) {
        var event = Minecraft.getInstance().getSoundManager().getSoundEvent(sound);
        return event != null && event.getWeight() > 0;
    }

    @Override
    public Voice play(SoundSpec spec, boolean music, boolean loop, float volume) {
        var sound = new ManagedSound(spec, music, volume);
        Minecraft.getInstance().getSoundManager().play(sound);
        return new Voice() {
            private long started = System.nanoTime();
            private boolean observedActive;
            private boolean repeat = loop;

            @Override public void volume(float value) { sound.setVolume(value); }
            @Override public void looping(boolean value) { repeat = value; }
            @Override public void stop() {
                sound.finish();
                Minecraft.getInstance().getSoundManager().stop(sound);
            }
            @Override public boolean active() {
                if (sound.isStopped()) return false;
                if (!sound.startedOnChannel && System.nanoTime() - started >= 10_000_000_000L) {
                    // 解码失败或音频设备不可用时不能永久占用 BGM 和原版音乐抑制状态。
                    stop();
                    return false;
                }
                boolean active = Minecraft.getInstance().getSoundManager().isActive(sound);
                observedActive |= active;
                if (!active && sound.startedOnChannel && repeat && (!music || musicAudible())) {
                    // 在曲目边界续播，允许同曲修改 loop 而不重启正在播放的部分。
                    sound.startedOnChannel = false;
                    observedActive = false;
                    started = System.nanoTime();
                    Minecraft.getInstance().getSoundManager().play(sound);
                    return true;
                }
                // 声音文件异步解码时给播放队列一个短暂启动窗口。
                return active || (!observedActive && System.nanoTime() - started < 1_000_000_000L);
            }
        };
    }

    static void soundStarted(SoundInstance instance) {
        if (instance instanceof ManagedSound sound) sound.startedOnChannel = true;
    }

    @Override
    public boolean musicAudible() {
        var options = Minecraft.getInstance().options;
        return options.getSoundSourceVolume(SoundSource.MASTER) > 0
                && options.getSoundSourceVolume(SoundSource.MUSIC) > 0;
    }

    private static final class ManagedSound extends AbstractTickableSoundInstance {
        private volatile boolean startedOnChannel;

        private ManagedSound(SoundSpec spec, boolean music, float initialVolume) {
            super(SoundEvent.createVariableRangeEvent(spec.sound()), music ? SoundSource.MUSIC : SoundSource.MASTER,
                    SoundInstance.createUnseededRandom());
            volume = initialVolume;
            pitch = spec.pitch();
            looping = false;
            delay = 0;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override public void tick() {}
        @Override public boolean canStartSilent() { return true; }
        private void setVolume(float value) { volume = value; }
        private void finish() { stop(); }
    }
}
