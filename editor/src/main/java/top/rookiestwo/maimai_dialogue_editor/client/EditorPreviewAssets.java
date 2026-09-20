package top.rookiestwo.maimai_dialogue_editor.client;

import icyllis.modernui.graphics.BitmapFactory;
import icyllis.modernui.graphics.Image;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueImageSource;
import top.rookiestwo.maimai_dialogue_editor.material.*;
import top.rookiestwo.maimai_dialogue_editor.project.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/** UI-thread-owned, project-scoped assets. No global packs, ImageStore mutation or sound registry changes. */
public final class EditorPreviewAssets implements MaterialWorkspace.PreviewAssets {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final long CACHE_BYTES = 128L * 1024 * 1024;
    private final Executor io, ui;
    private final LinkedHashMap<String, CompletableFuture<Image>> images = new LinkedHashMap<>(16, .75f, true);
    private MaterialSnapshot snapshot = MaterialSnapshot.EMPTY;
    private long revision;
    private Consumer<String> loadFailure = ignored -> {};

    public EditorPreviewAssets(Executor io, Executor ui) { this.io = io; this.ui = ui; }
    public void setLoadFailure(Consumer<String> listener) { loadFailure = Objects.requireNonNull(listener); }

    @Override public CompletableFuture<String> refresh(ProjectDraft draft) {
        long expected = ++revision;
        return CompletableFuture.supplyAsync(() -> {
            try { return MaterialSnapshot.prepare(draft); }
            catch (IOException failure) { throw new CompletionException(failure); }
        }, io).thenApplyAsync(prepared -> {
            if (revision != expected) throw new CancellationException();
            snapshot = prepared;
            Set<String> retained = new HashSet<>();
            snapshot.images().values().forEach(blob -> retained.add(blob.id()));
            images.entrySet().removeIf(entry -> {
                if (retained.contains(entry.getKey())) return false;
                release(entry.getValue()); return true;
            });
            return snapshot.namespace();
        }, ui);
    }

    public DialogueImageSource openImages() { return new Images(snapshot); }
    public DialogueImageSource openImages(MaterialSnapshot source) { return new Images(source); }
    public MaterialSnapshot snapshot() { return snapshot; }

    @Override public void clear() {
        ++revision;
        snapshot = MaterialSnapshot.EMPTY;
        images.values().forEach(this::release);
        images.clear();
    }

    private void release(CompletableFuture<Image> future) {
        future.thenAccept(image -> ui.execute(image::close));
    }

    private CompletableFuture<Image> image(ProjectBlob blob) {
        var cached = images.get(blob.id());
        if (cached != null) return cached;
        var decoded = CompletableFuture.supplyAsync(() -> {
            try {
                byte[] bytes = blob.read();
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (IOException failure) { throw new CompletionException(failure); }
        }, io);
        var uploaded = decoded.thenApplyAsync(bitmap -> {
            try (bitmap) {
                Image result = Image.createTextureFromBitmap(bitmap);
                if (result == null) throw new CompletionException(new IOException("Failed to upload " + blob.id()));
                return result;
            }
        }, ui);
        images.put(blob.id(), uploaded);
        uploaded.whenCompleteAsync((image, failure) -> {
            if (failure != null) images.remove(blob.id(), uploaded);
            // Let waiting consumers clone their handle before an LRU eviction releases the cache's handle.
            ui.execute(this::trim);
        }, ui);
        return uploaded;
    }

    private void trim() {
        long bytes = images.values().stream().filter(f -> f.isDone() && !f.isCompletedExceptionally())
                .map(f -> f.getNow(null)).filter(Objects::nonNull)
                .mapToLong(image -> (long)image.getWidth() * image.getHeight() * 4).sum();
        var iterator = images.entrySet().iterator();
        while (bytes > CACHE_BYTES && iterator.hasNext()) {
            var future = iterator.next().getValue();
            if (!future.isDone() || future.isCompletedExceptionally()) continue;
            Image image = future.getNow(null);
            bytes -= (long)image.getWidth() * image.getHeight() * 4;
            iterator.remove(); image.close();
        }
    }

    /** Each consumer owns cloned handles, so refreshing/evicting the cache cannot invalidate a visible frame. */
    private final class Images implements DialogueImageSource {
        private final MaterialSnapshot source;
        private final Map<ResourceLocation, CompletableFuture<Image>> borrowed = new HashMap<>();
        private boolean closed;
        Images(MaterialSnapshot source) { this.source = source; }

        @Override public DialogueImageSource fork() { return new Images(source); }

        @Override public void load(ResourceLocation id, Consumer<Image> ready) {
            if (closed) return;
            if (!source.owns(id)) { DialogueImageSource.RESOURCES.load(id, ready); return; }
            ProjectBlob blob = source.images().get(id);
            if (blob == null) { ready.accept(null); return; }
            var future = borrowed.computeIfAbsent(id, ignored -> image(blob).thenApply(image -> {
                if (closed) throw new CancellationException();
                return image.clone();
            }));
            BiConsumer<Image, Throwable> completion = (image, failure) -> {
                if (closed) return;
                if (failure != null) {
                    LOGGER.error("Failed to load editor image {}", id, failure);
                    if (source.namespace().equals(snapshot.namespace()) && snapshot.images().get(id) == blob) {
                        Throwable cause = failure;
                        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                        loadFailure.accept(id + ": " + cause.getMessage());
                    }
                }
                ready.accept(image);
            };
            if (future.isDone()) future.whenComplete(completion);
            else future.whenCompleteAsync(completion, ui);
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            borrowed.values().forEach(EditorPreviewAssets.this::release);
            borrowed.clear();
        }
    }
}
