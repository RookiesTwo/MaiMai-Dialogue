package top.rookiestwo.maimai_dialogue_editor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;
import top.rookiestwo.maimai_dialogue.audio.*;
import top.rookiestwo.maimai_dialogue.client.audio.DialogueAudioManager;
import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;
import top.rookiestwo.maimai_dialogue.client.scene.AudioCue;
import top.rookiestwo.maimai_dialogue.client.session.*;
import top.rookiestwo.maimai_dialogue_editor.MaiMaiDialogueEditor;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialSnapshot;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** One cancellable preview scope. Only client-thread tasks touch the manager or the sound device. */
@EventBusSubscriber(modid = MaiMaiDialogueEditor.MOD_ID, value = Dist.CLIENT)
public final class EditorDialogueAudio implements AutoCloseable {
    private static final Set<EditorDialogueAudio> ACTIVE = new HashSet<>();
    private final Consumer<String> diagnostic;
    private final Runnable changed;
    private volatile boolean closed;
    private EditorProjectAudioBackend backend;
    private DialogueAudioManager manager;
    private long sampleStarted = -1;
    private int sampleCharacters;
    private boolean musicSample;

    public EditorDialogueAudio(MaterialSnapshot assets, Executor io, Consumer<String> diagnostic, Runnable changed) {
        this.diagnostic = diagnostic; this.changed = changed;
        Minecraft.getInstance().execute(() -> {
            if (closed) return;
            backend = new EditorProjectAudioBackend(assets, io, diagnostic);
            manager = new DialogueAudioManager(backend, ClientConfig::audio, Util::getMillis, diagnostic);
            manager.open(); ACTIVE.add(this);
        });
    }
    private void dispatch(Consumer<DialogueAudioManager> action) {
        Minecraft.getInstance().execute(() -> {
            if (closed || manager == null) return;
            try { action.accept(manager); }
            catch (RuntimeException failure) { diagnostic.accept(String.valueOf(failure.getMessage())); close(); }
        });
    }
    public boolean active() { return !closed; }
    public void render(DialogueScreenState state, List<DialogueSessionEffect.ApplyBgm> operations) {
        dispatch(audio -> {
            operations.forEach(effect -> audio.applyBgm(effect.operation(), effect.key()));
            audio.render(state);
        });
    }
    public void frame(long generation, long token, int elapsed) { dispatch(audio -> audio.frame(generation, token, elapsed)); }
    public void reveal(long generation, long token, int end, boolean audible) { dispatch(audio -> audio.reveal(generation, token, end, audible)); }
    public void audition(BgmOperation operation) {
        dispatch(audio -> { musicSample = true; audio.applyBgm(operation, new AudioCue.Key(0, -1)); });
    }
    public void audition(TypewriterSound sound) {
        dispatch(audio -> {
            audio.render(new DialogueScreenState(1, Optional.empty(), Optional.empty(), Optional.empty(), PlaybackPhase.READY,
                    false, Optional.empty(), false, false, 0, Optional.empty(), Optional.of("Preview"), Optional.empty(),
                    List.of(), List.of(), false, false, sound));
            sampleStarted = Util.getMillis(); sampleCharacters = 0;
        });
    }
    private void tick() {
        if (closed || manager == null) return;
        try {
            if (sampleStarted >= 0) {
                if (Util.getMillis() - sampleStarted >= 1500) { close(); return; }
                manager.reveal(1, Long.MIN_VALUE, ++sampleCharacters, true);
            }
            manager.tick(); backend.tick();
            if (musicSample && !backend.playing()) close();
        } catch (RuntimeException failure) { diagnostic.accept(String.valueOf(failure.getMessage())); close(); }
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        Minecraft.getInstance().execute(() -> {
            ACTIVE.remove(this);
            if (manager != null) manager.close();
            if (backend != null) backend.close();
            changed.run();
        });
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) { List.copyOf(ACTIVE).forEach(EditorDialogueAudio::tick); }
    @SubscribeEvent public static void music(SelectMusicEvent event) {
        if (ACTIVE.stream().anyMatch(audio -> !audio.closed && audio.manager.suppressVanillaMusic())) event.overrideMusic(null);
    }
    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) { List.copyOf(ACTIVE).forEach(EditorDialogueAudio::close); }
    public static void reloaded(SoundEngineLoadEvent event) {
        Minecraft.getInstance().execute(() -> {
            for (var audio : List.copyOf(ACTIVE)) {
                // Old OpenAL buffer IDs belong to the destroyed device, not its replacement.
                audio.backend.deviceLost(); audio.close();
            }
        });
    }
}
