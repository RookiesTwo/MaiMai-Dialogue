package top.rookiestwo.maimai_dialogue.client.audio;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.audio.BgmOperation;
import top.rookiestwo.maimai_dialogue.audio.SoundSpec;
import top.rookiestwo.maimai_dialogue.client.config.AudioPreferences;
import top.rookiestwo.maimai_dialogue.client.scene.AudioCue;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class DialogueAudioManager {
    private final AudioBackend backend;
    private final Supplier<AudioPreferences> preferences;
    private final LongSupplier clock;
    private final Consumer<String> diagnostic;
    private final Set<ResourceLocation> missingSounds = new HashSet<>();
    private final Set<AudioCue.Key> appliedBgm = new HashSet<>();
    private final List<OneShot> oneShots = new ArrayList<>();
    private DialogueScreenState state;
    private List<AudioCue> cues = List.of();
    private int nextCue;
    private long token = Long.MIN_VALUE;
    private int revealedEnd;
    private long lastTypingTime = Long.MIN_VALUE;
    private boolean open;
    private boolean fastForward;
    private boolean historyOpen;
    private boolean reloading;
    private MusicVoice current;
    private MusicVoice outgoing;
    private BgmOperation desiredBgm;

    public DialogueAudioManager(AudioBackend backend, Supplier<AudioPreferences> preferences,
                                LongSupplier clock, Consumer<String> diagnostic) {
        this.backend = backend;
        this.preferences = preferences;
        this.clock = clock;
        this.diagnostic = diagnostic;
    }

    public void open() {
        close();
        open = true;
    }

    // 会话内 token 唯一；重绘同一步不会重新调度已经发出的声音。
    public void render(DialogueScreenState next) {
        if (!open) return;
        long nextToken = next.scenePlayback().map(playback -> playback.token()).orElse(Long.MIN_VALUE);
        if (token != nextToken) {
            token = nextToken;
            cues = next.scenePlayback().map(playback -> playback.audioCues()).orElse(List.of());
            nextCue = 0;
            revealedEnd = 0;
            lastTypingTime = Long.MIN_VALUE;
        }
        state = next;
        if (next.playbackSkipped()) nextCue = cues.size();
    }

    public void frame(long generation, long playbackToken, int elapsedMs) {
        if (!matches(generation, playbackToken) || state.playbackSkipped()) return;
        while (nextCue < cues.size() && cues.get(nextCue).delayMs() <= elapsedMs) {
            AudioCue cue = cues.get(nextCue++);
            cue.bgm().ifPresent(operation -> applyBgm(operation, cue.key()));
            if (!reloading) cue.sound().ifPresent(sound -> playOneShot(sound, false));
        }
    }

    public void setFastForward(boolean value) { fastForward = value; }
    public void setHistoryOpen(boolean value) { historyOpen = value; }

    public void reveal(long generation, long playbackToken, int end, boolean audible) {
        if (!matches(generation, playbackToken) || end <= revealedEnd) return;
        revealedEnd = end;
        var profile = state.typewriterSound();
        if (!audible || reloading || fastForward || historyOpen || state.playbackSkipped()
                || !profile.enabled() || !preferences.get().typewriterEnabled()) return;
        long now = clock.getAsLong();
        if (lastTypingTime != Long.MIN_VALUE && now - lastTypingTime < profile.minIntervalMs()) return;
        lastTypingTime = now;
        playOneShot(profile.sound(), true);
    }

    private boolean matches(long generation, long playbackToken) {
        return open && state != null && generation == state.generation() && token == playbackToken;
    }

    // BGM 指令去重与音效调度分开，跳过结算不会回放同一条指令。
    public void applyBgm(BgmOperation operation, AudioCue.Key key) {
        if (!open || !appliedBgm.add(key)) return;
        changeBgm(operation);
    }

    private void changeBgm(BgmOperation operation) {
        if (operation.isPlay() && !available(operation.sound().orElseThrow())) return;
        desiredBgm = operation.isPlay() ? operation : null;
        if (reloading) return;
        long now = clock.getAsLong();
        if (operation.isPlay() && current != null && current.voice.active()
                && current.sound.equals(operation.sound().orElseThrow())) {
            current.voice.looping(operation.loop());
            current.fade(operation.volume(), operation.fadeMs(), now);
            return;
        }
        if (outgoing != null) outgoing.voice.stop();
        outgoing = current;
        current = null;
        if (outgoing != null) outgoing.fade(0, operation.fadeMs(), now);
        if (operation.isPlay()) {
            SoundSpec sound = new SoundSpec(operation.sound().orElseThrow(), operation.volume(), 1);
            float initial = operation.fadeMs() == 0 ? operation.volume() : 0;
            var voice = backend.play(sound, true, operation.loop(), initial * (float) preferences.get().bgmVolume());
            current = new MusicVoice(voice, sound.sound(), initial);
            current.fade(operation.volume(), operation.fadeMs(), now);
        }
        tick();
    }

    private void playOneShot(SoundSpec sound, boolean typing) {
        double multiplier = typing ? preferences.get().typewriterVolume() : preferences.get().soundVolume();
        if (sound.volume() == 0 || multiplier == 0 || !available(sound.sound())) return;
        var voice = backend.play(sound, false, false, sound.volume() * (float) multiplier);
        oneShots.add(new OneShot(voice, sound.volume(), typing));
    }

    private boolean available(ResourceLocation sound) {
        if (backend.available(sound)) return true;
        if (missingSounds.add(sound)) diagnostic.accept("Dialogue sound is missing or empty: " + sound);
        return false;
    }

    // 只按真实时间淡化；快进改变的是 cue 的触发时间。
    public void tick() {
        if (!open || reloading) return;
        long now = clock.getAsLong();
        if (current != null) {
            if (!current.voice.active()) {
                current = null;
                // Minecraft 在分类静音时可能销毁实例；保留曲目，解除静音后再建立。
                if (backend.musicAudible()) desiredBgm = null;
            } else current.voice.volume(current.gain(now) * (float) preferences.get().bgmVolume());
        }
        if (outgoing != null) {
            float gain = outgoing.gain(now);
            if (!outgoing.voice.active() || gain <= 0) {
                outgoing.voice.stop();
                outgoing = null;
            } else outgoing.voice.volume(gain * (float) preferences.get().bgmVolume());
        }
        oneShots.removeIf(shot -> !shot.voice.active());
        for (OneShot shot : oneShots) {
            double multiplier = shot.typing ? preferences.get().typewriterVolume() : preferences.get().soundVolume();
            if (shot.typing && (!preferences.get().typewriterEnabled() || fastForward || historyOpen)) multiplier = 0;
            shot.voice.volume(shot.volume * (float) multiplier);
        }
        if (current == null && desiredBgm != null && backend.musicAudible()) {
            BgmOperation resume = desiredBgm;
            desiredBgm = null;
            changeBgm(resume);
        }
    }

    public boolean suppressVanillaMusic() {
        return open && !reloading && preferences.get().bgmVolume() > 0 && backend.musicAudible()
                && (audible(current) || audible(outgoing));
    }

    private boolean audible(MusicVoice voice) {
        return voice != null && voice.voice.active() && (voice.target > 0 || voice.gain(clock.getAsLong()) > 0);
    }

    public void beginReload() {
        reloading = true;
        stopVoices();
        missingSounds.clear();
    }

    public void soundEngineReloaded() {
        if (!open || reloading) return;
        beginReload();
        finishReload();
    }

    public void finishReload() {
        if (!reloading) return;
        reloading = false;
        BgmOperation resume = desiredBgm;
        desiredBgm = null;
        if (open && resume != null) changeBgm(resume);
    }

    public void close() {
        stopVoices();
        open = false;
        state = null;
        desiredBgm = null;
        token = Long.MIN_VALUE;
        cues = List.of();
        appliedBgm.clear();
        missingSounds.clear();
        fastForward = false;
        historyOpen = false;
    }

    private void stopVoices() {
        if (current != null) current.voice.stop();
        if (outgoing != null) outgoing.voice.stop();
        current = null;
        outgoing = null;
        oneShots.forEach(shot -> shot.voice.stop());
        oneShots.clear();
    }

    // 判断渲染后的文本，不把 Markdown 标记、标点或空白当作打字声。
    public static boolean hasAudibleCharacter(String text, int start, int end) {
        for (int index = start; index < end;) {
            int point = text.codePointAt(index);
            index += Character.charCount(point);
            int type = Character.getType(point);
            if (!Character.isWhitespace(point) && !Character.isSpaceChar(point)
                    && type != Character.CONNECTOR_PUNCTUATION && type != Character.DASH_PUNCTUATION
                    && type != Character.START_PUNCTUATION && type != Character.END_PUNCTUATION
                    && type != Character.INITIAL_QUOTE_PUNCTUATION && type != Character.FINAL_QUOTE_PUNCTUATION
                    && type != Character.OTHER_PUNCTUATION && type != Character.CONTROL && type != Character.FORMAT
                    && type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK
                    && type != Character.ENCLOSING_MARK) return true;
        }
        return false;
    }

    private record OneShot(AudioBackend.Voice voice, float volume, boolean typing) {}

    private static final class MusicVoice {
        private final AudioBackend.Voice voice;
        private final ResourceLocation sound;
        private float from;
        private float target;
        private long started;
        private int duration;

        private MusicVoice(AudioBackend.Voice voice, ResourceLocation sound, float gain) {
            this.voice = voice;
            this.sound = sound;
            from = target = gain;
        }

        private float gain(long now) {
            float fraction = duration == 0 ? 1 : Math.clamp((now - started) / (float) duration, 0, 1);
            return from + (target - from) * fraction;
        }

        private void fade(float next, int ms, long now) {
            from = gain(now);
            target = next;
            started = now;
            duration = ms;
        }
    }
}
