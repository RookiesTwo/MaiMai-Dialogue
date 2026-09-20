package top.rookiestwo.maimai_dialogue_editor.client;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.*;
import net.minecraft.sounds.SoundSource;
import top.rookiestwo.maimai_dialogue_editor.preview.*;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectBlob;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFormat;

/** Owns one streaming channel on Minecraft's device, without sounds.json or a global resource reload. */
public final class EditorAudioPreview implements AudioPreviewSession.Backend {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final Executor io, ui;
    public EditorAudioPreview(Executor io, Executor ui) { this.io = io; this.ui = ui; }

    @Override public AudioPreviewSession.Voice start(ProjectBlob blob, AudioPreviewSession.Listener listener) {
        Playback playback = new Playback(listener);
        CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes = blob.read();
                OwnedStream stream = new OwnedStream(new JOrbisAudioStream(new ByteArrayInputStream(bytes)));
                playback.stream = stream;
                double duration = OggPreviewInfo.duration(bytes, stream.getFormat().getSampleRate());
                if (playback.cancelled.get()) { stream.close(); return; }
                Minecraft.getInstance().execute(() -> {
                    try { playback.attach(duration); }
                    catch (RuntimeException failure) { playback.fail(failure); }
                });
            } catch (Exception failure) { playback.fail(failure); }
        }, io);
        return playback;
    }

    private final class Playback implements AudioPreviewSession.Voice {
        private final AudioPreviewSession.Listener listener;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean polling = new AtomicBoolean();
        private volatile ChannelAccess.ChannelHandle handle;
        private volatile OwnedStream stream;
        private volatile boolean paused;
        // Only the sound executor reads/writes the playback clock.
        private volatile long elapsed;
        private long started;
        private boolean clockRunning;
        Playback(AudioPreviewSession.Listener listener) { this.listener = listener; }

        void attach(double duration) {
            if (cancelled.get()) { closeStream(); return; }
            var engine = ((EditorSoundAccess)Minecraft.getInstance().getSoundManager()).maimaiEditor$soundEngine();
            var access = (EditorSoundAccess.Engine)engine;
            if (!access.maimaiEditor$loaded()) { fail(new IOException("Audio device is unavailable")); return; }
            float volume = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.RECORDS);
            access.maimaiEditor$channels().createHandle(Library.Pool.STREAMING).whenComplete((created, failure) -> {
                if (failure != null || created == null) {
                    fail(failure == null ? new IOException("No audio channel available") : failure); return;
                }
                handle = created;
                if (cancelled.get()) { created.execute(Channel::stop); closeStream(); return; }
                created.execute(channel -> {
                    if (cancelled.get()) { channel.stop(); closeStream(); return; }
                    try {
                        channel.setRelative(true); channel.disableAttenuation(); channel.setPitch(1);
                        channel.setLooping(false); channel.setVolume(volume);
                        channel.attachBufferStream(stream); channel.play();
                        started = System.nanoTime(); clockRunning = true;
                        if (paused) { channel.pause(); updateClock(false); }
                        ui.execute(() -> { if (!cancelled.get()) listener.ready(duration); });
                    } catch (RuntimeException failure1) { fail(failure1); }
                });
            });
        }
        private void updateClock(boolean playing) {
            long now = System.nanoTime();
            if (clockRunning) elapsed += Math.max(0, now - started);
            started = now; clockRunning = playing;
        }
        @Override public void pause(boolean value) {
            paused = value;
            var current = handle;
            if (current != null) current.execute(channel -> {
                if (cancelled.get()) return;
                if (value) channel.pause(); else channel.unpause();
                updateClock(channel.playing());
            });
        }
        @Override public void poll() {
            if (cancelled.get() || !polling.compareAndSet(false, true)) return;
            Minecraft.getInstance().execute(() -> {
                var current = handle;
                if (cancelled.get() || current == null) { polling.set(false); return; }
                if (current.isStopped()) {
                    polling.set(false);
                    ui.execute(() -> { if (!cancelled.get()) listener.progress(elapsed / 1_000_000_000.0, true); });
                    return;
                }
                float volume = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.RECORDS);
                current.execute(channel -> {
                    polling.set(false);
                    if (cancelled.get()) return;
                    channel.setVolume(volume);
                    if (paused) channel.pause();
                    updateClock(channel.playing());
                    double position = elapsed / 1_000_000_000.0;
                    boolean finished = channel.stopped();
                    ui.execute(() -> { if (!cancelled.get()) listener.progress(position, finished); });
                });
                // execute can discard a task if the device released its channel before the sound thread runs it.
                polling.set(false);
            });
        }
        @Override public void stop() {
            cancelled.set(true);
            var current = handle;
            if (current != null) current.execute(Channel::stop);
            closeStream();
        }
        private void closeStream() {
            var owned = stream;
            if (owned != null) CompletableFuture.runAsync(() -> {
                try { owned.close(); } catch (IOException failure) { LOGGER.debug("Closing preview audio", failure); }
            });
        }
        private void fail(Throwable failure) {
            LOGGER.error("Editor audio preview failed", failure);
            stop();
            ui.execute(() -> listener.failed(String.valueOf(failure.getMessage())));
        }
    }

    /** Cancellation and Minecraft channel destruction can both close the same decoder. */
    private static final class OwnedStream implements AudioStream {
        private final AudioStream delegate;
        private boolean closed;
        OwnedStream(AudioStream delegate) { this.delegate = delegate; }
        public AudioFormat getFormat() { return delegate.getFormat(); }
        public synchronized ByteBuffer read(int size) throws IOException { return closed ? null : delegate.read(size); }
        public synchronized void close() throws IOException { if (!closed) { closed = true; delegate.close(); } }
    }
}
