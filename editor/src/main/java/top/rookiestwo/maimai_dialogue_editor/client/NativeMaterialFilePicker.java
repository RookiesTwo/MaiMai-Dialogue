package top.rookiestwo.maimai_dialogue_editor.client;

import net.minecraft.client.resources.language.I18n;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialFilePicker;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** TinyFD uses process-wide native buffers. Keep one dialog on a dedicated daemon, never on the render/UI/IO threads. */
public final class NativeMaterialFilePicker implements MaterialFilePicker {
    private static final ExecutorService DIALOGS = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "MaiMai-Editor-FilePicker"); thread.setDaemon(true); return thread;
    });
    private static final AtomicBoolean OPEN = new AtomicBoolean();
    private final Runnable restoreFocus;
    public NativeMaterialFilePicker(Runnable restoreFocus) { this.restoreFocus = restoreFocus; }

    @Override public CompletableFuture<List<Path>> choose(Request request) {
        if (!OPEN.compareAndSet(false, true)) return CompletableFuture.failedFuture(new IllegalStateException(
                I18n.get("gui.maimai_dialogue_editor.material.picker_busy")));
        String title = I18n.get("gui.maimai_dialogue_editor.material." + (request.multiple() ? "import" : "replace"));
        String description = request.replacementKind() == ResourceKind.IMAGE ? "PNG"
                : request.replacementKind() == ResourceKind.SOUND ? "OGG Vorbis" : "PNG / OGG Vorbis";
        String[] patterns = request.replacementKind() == ResourceKind.IMAGE ? new String[]{"*.png"}
                : request.replacementKind() == ResourceKind.SOUND ? new String[]{"*.ogg"} : new String[]{"*.png", "*.ogg"};
        return CompletableFuture.supplyAsync(() -> {
            try (var stack = MemoryStack.stackPush()) {
                var filters = stack.mallocPointer(patterns.length);
                for (String pattern : patterns) filters.put(stack.UTF8(pattern));
                filters.flip();
                String directory = request.directory() == null ? "" : request.directory().toAbsolutePath().normalize() + File.separator;
                // LWJGL enables TinyFD's UTF-8 Windows bridge; native selection uses GetOpenFileNameW.
                String result = TinyFileDialogs.tinyfd_openFileDialog(title, directory, filters, description, request.multiple());
                return decodeSelection(result);
            } finally {
                OPEN.set(false);
                restoreFocus.run();
            }
        }, DIALOGS);
    }

    static List<Path> decodeSelection(String result) {
        if (result == null || result.isEmpty()) return List.of();
        Set<Path> files = new LinkedHashSet<>();
        for (String name : result.split("\\|", -1)) {
            if (name.isEmpty()) throw new IllegalArgumentException("Invalid file selection");
            Path path = Path.of(name);
            if (!path.isAbsolute()) throw new IllegalArgumentException("Expected an absolute file path");
            files.add(path.normalize());
        }
        return List.copyOf(files);
    }
}
