package top.rookiestwo.maimai_dialogue_editor.client;

import com.mojang.blaze3d.audio.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import top.rookiestwo.maimai_dialogue.audio.SoundSpec;
import top.rookiestwo.maimai_dialogue.client.audio.*;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialSnapshot;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectBlob;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;

/** Client-thread backend on the game's device, with project blobs and read-only external sound events. */
public final class EditorProjectAudioBackend implements AudioBackend, AutoCloseable {
    private final MaterialSnapshot assets;
    private final Executor io;
    private final Consumer<String> diagnostic;
    private final MinecraftAudioBackend external = new MinecraftAudioBackend();
    private record Clip(Object key, ProjectBlob.Reader reader, boolean stream, float volume, float pitch) {}
    private final Map<Object, CompletableFuture<SoundBuffer>> buffers = new HashMap<>();
    private final List<ProjectVoice> voices = new ArrayList<>();
    private final Set<ResourceLocation> invalidSounds = new HashSet<>();
    private boolean closed;

    public EditorProjectAudioBackend(MaterialSnapshot assets, Executor io, Consumer<String> diagnostic) {
        this.assets = assets; this.io = io; this.diagnostic = diagnostic;
    }
    @Override public boolean available(ResourceLocation sound) {
        return !closed && !invalidSounds.contains(sound)
                && (assets.owns(sound) ? !assets.sounds().getOrDefault(sound, List.of()).isEmpty() : external.available(sound));
    }
    @Override public boolean musicAudible() { return external.musicAudible(); }
    @Override public Voice play(SoundSpec sound, boolean music, boolean loop, float volume) {
        if (!available(sound.sound())) throw new IllegalStateException("Missing sound: " + sound.sound());
        Clip file;
        if (assets.owns(sound.sound())) {
            var files = assets.sounds().get(sound.sound());
            var selected = files.get(ThreadLocalRandom.current().nextInt(files.size()));
            file = new Clip(selected.blob(), selected.blob()::read, selected.stream(), 1, 1);
        } else {
            var random = net.minecraft.util.RandomSource.create();
            var selected = Minecraft.getInstance().getSoundManager().getSoundEvent(sound.sound()).getSound(random);
            var resources = Minecraft.getInstance().getResourceManager();
            file = new Clip(selected.getPath(), () -> {
                try (var input = resources.open(selected.getPath())) { return input.readAllBytes(); }
            }, selected.shouldStream(), selected.getVolume().sample(random), selected.getPitch().sample(random));
        }
        var voice = new ProjectVoice(file, sound, music, loop, volume);
        voices.add(voice); voice.start(); return voice;
    }
    private static EditorSoundAccess.Engine engine() {
        return (EditorSoundAccess.Engine)((EditorSoundAccess)Minecraft.getInstance().getSoundManager()).maimaiEditor$soundEngine();
    }
    private void discard(SoundBuffer buffer) {
        Minecraft.getInstance().execute(() -> {
            var channels = engine().maimaiEditor$channels();
            // Release stopped channels before deleting buffers that may still be bound to them.
            channels.scheduleTick();
            channels.executeOnChannels(ignored -> buffer.discardAlBuffer());
        });
    }
    public void tick() { voices.removeIf(voice -> !voice.active()); }
    public boolean playing() { return !voices.isEmpty(); }
    public void deviceLost() { buffers.clear(); }
    @Override public void close() {
        if (closed) return;
        closed = true; voices.forEach(ProjectVoice::stop); voices.clear();
        buffers.values().forEach(future -> future.thenAccept(this::discard));
        buffers.clear();
    }

    private final class ProjectVoice implements Voice {
        final Clip file;
        final SoundSpec spec;
        final boolean music, streaming;
        final long requested = System.nanoTime();
        volatile boolean stopped, attached, repeat;
        volatile float gain;
        volatile ChannelAccess.ChannelHandle handle;
        volatile ClipStream stream;
        ProjectVoice(Clip file, SoundSpec spec, boolean music, boolean loop, float gain) {
            this.file = file; this.spec = spec; this.music = music; repeat = loop; this.gain = gain;
            streaming = music || file.stream();
        }
        void start() {
            if (!engine().maimaiEditor$loaded()) { fail(new IOException("Audio device is unavailable")); return; }
            CompletableFuture<?> prepared;
            if (streaming) prepared = CompletableFuture.supplyAsync(() -> {
                try {
                    var decoded = new ClipStream(file.reader().read());
                    stream = decoded;
                    if (stopped) decoded.close();
                    return decoded;
                } catch (IOException failure) { throw new CompletionException(failure); }
            }, io);
            else prepared = buffers.computeIfAbsent(file.key(), blob -> CompletableFuture.supplyAsync(() -> {
                try (var input = new JOrbisAudioStream(new ByteArrayInputStream(file.reader().read()))) {
                    return new SoundBuffer(input.readAll(), input.getFormat());
                } catch (IOException failure) { throw new CompletionException(failure); }
            }, io));
            prepared.whenComplete((decoded, failure) -> Minecraft.getInstance().execute(() -> {
                if (stopped || closed) { closeStream(); return; }
                if (failure != null) { invalidSounds.add(spec.sound()); fail(failure); return; }
                if (!engine().maimaiEditor$loaded()) { fail(new IOException("Audio device is unavailable")); return; }
                float initialVolume = categoryVolume();
                engine().maimaiEditor$channels().createHandle(streaming ? Library.Pool.STREAMING : Library.Pool.STATIC)
                        .whenComplete((created, allocationFailure) -> {
                    if (created == null || allocationFailure != null) {
                        fail(allocationFailure == null ? new IOException("No audio channel available") : allocationFailure); return;
                    }
                    handle = created;
                    created.execute(channel -> {
                        if (stopped) { channel.stop(); closeStream(); return; }
                        try {
                            channel.setRelative(true); channel.disableAttenuation(); channel.setPitch(Math.clamp(spec.pitch() * file.pitch(), .5f, 2));
                            channel.setVolume(initialVolume); channel.setLooping(!streaming && repeat);
                            if (streaming) channel.attachBufferStream((AudioStream)decoded);
                            else channel.attachStaticBuffer((SoundBuffer)decoded);
                            channel.play(); attached = true;
                        } catch (RuntimeException error) { fail(error); }
                    });
                });
            }));
        }
        private float categoryVolume() {
            // MASTER is applied by the shared listener, so do not multiply it twice.
            return Math.clamp(gain * file.volume() * (music ? Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC) : 1), 0, 1);
        }
        @Override public void volume(float volume) {
            gain = volume;
            var current = handle; float adjusted = categoryVolume();
            if (!stopped && current != null) current.execute(channel -> { if (!stopped) channel.setVolume(adjusted); });
        }
        @Override public void looping(boolean loop) {
            repeat = loop;
            var current = handle;
            if (!streaming && !stopped && current != null) current.execute(channel -> { if (!stopped) channel.setLooping(loop); });
        }
        @Override public boolean active() {
            if (stopped) return false;
            if (handle != null && handle.isStopped()) { stop(); return false; }
            if (!attached && System.nanoTime() - requested > 10_000_000_000L) {
                fail(new IOException("Audio channel did not start")); return false;
            }
            return true;
        }
        @Override public void stop() {
            stopped = true;
            var current = handle;
            if (current != null) current.execute(Channel::stop);
            closeStream();
        }
        private void closeStream() {
            var current = stream;
            if (current != null) CompletableFuture.runAsync(() -> {
                try { current.close(); } catch (IOException ignored) { }
            });
        }
        private void fail(Throwable failure) {
            if (stopped) return;
            stop();
            Minecraft.getInstance().execute(() -> {
                if (!closed) diagnostic.accept(spec.sound() + ": " + failure.getMessage());
            });
        }
        /** Repeat is sampled at the track boundary, so changing it does not restart the current clip. */
        private final class ClipStream implements AudioStream {
            final byte[] bytes;
            final AudioFormat format;
            JOrbisAudioStream decoder;
            boolean released;
            ClipStream(byte[] bytes) throws IOException {
                this.bytes = bytes; decoder = new JOrbisAudioStream(new ByteArrayInputStream(bytes)); format = decoder.getFormat();
            }
            public AudioFormat getFormat() { return format; }
            public synchronized ByteBuffer read(int size) throws IOException {
                if (released) return ByteBuffer.allocate(0);
                ByteBuffer data = decoder.read(size);
                if (!data.hasRemaining() && repeat && !stopped) {
                    decoder.close(); decoder = new JOrbisAudioStream(new ByteArrayInputStream(bytes)); data = decoder.read(size);
                }
                return data;
            }
            public synchronized void close() throws IOException { if (!released) { released = true; decoder.close(); } }
        }
    }
}
