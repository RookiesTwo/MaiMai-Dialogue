package top.rookiestwo.maimai_dialogue_editor.material;

import com.google.gson.JsonObject;
import net.minecraft.client.sounds.JOrbisAudioStream;
import top.rookiestwo.maimai_dialogue_editor.project.*;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Source-file IO and format probing. All calls run on the editor IO executor. */
public final class MaterialFiles {
    public record Imported(ProjectBlob blob, JsonObject data) {}
    public record ImportRequest(Path source, ResourceKey key) {
        public ImportRequest { Objects.requireNonNull(source); Objects.requireNonNull(key); }
    }
    /** Build one complete edit on the IO thread; a failure never publishes a partial batch to the document. */
    public static ProjectDraft importBatch(ProjectStore store, Path directory, ProjectDraft before,
                                           List<ImportRequest> requests, boolean replace) throws IOException {
        if (requests.isEmpty() || (replace && requests.size() != 1)) throw new IOException("Invalid import batch");
        Set<ResourceKey> targets = new HashSet<>();
        for (ImportRequest request : requests) {
            ResourceKey key = request.key();
            if (!supported(request.source()) || !key.kind().material() || kind(request.source()) != key.kind()
                    || !MaterialPack.portable(key.path()) || !before.hasResourceGroup(key.kind()) || !targets.add(key))
                throw new IOException("Invalid import target: " + key.path());
            if (replace ? before.revision(key) == null : before.revision(key) != null)
                throw new IOException("Import target changed: " + key.path());
        }
        ProjectDraft result = before;
        for (ImportRequest request : requests) {
            try {
                Imported imported = importFile(store, directory, request.source());
                JsonObject data = imported.data();
                ResourceKey key = request.key();
                if (replace) {
                    before.load(key);
                    if (before.resource(key) instanceof JsonObject previous) {
                        JsonObject merged = previous.deepCopy();
                        for (var entry : data.entrySet()) {
                            if (key.kind() == ResourceKind.SOUND && Set.of("event", "stream").contains(entry.getKey())
                                    && previous.has(entry.getKey())) continue;
                            merged.add(entry.getKey(), entry.getValue());
                        }
                        data = merged;
                    }
                } else if (key.kind() == ResourceKind.SOUND) {
                    data.addProperty("event", key.path());
                }
                result = result.withBlob(imported.blob()).withResource(key, data);
            } catch (IOException | RuntimeException failure) {
                throw new IOException(request.source().getFileName() + ": " + failure.getMessage(), failure);
            }
        }
        return result;
    }
    /** Native filters are a convenience, not validation. Check paths off the UI thread before confirmation. */
    public static List<Path> validateSelection(List<Path> paths, ResourceKind replacementKind) throws IOException {
        if (paths.isEmpty()) return List.of();
        Set<Path> files = new LinkedHashSet<>();
        for (Path source : paths) {
            Path path = source.toAbsolutePath().normalize();
            if (!Files.isRegularFile(path) || !supported(path)) throw new IOException("PNG / OGG Vorbis: " + path.getFileName());
            if (replacementKind != null && kind(path) != replacementKind) throw new IOException("Invalid replacement type: " + path.getFileName());
            files.add(path);
        }
        if (replacementKind != null && files.size() != 1) throw new IOException("Select one replacement file");
        return List.copyOf(files);
    }
    public static boolean supported(Path path) { return Set.of("png", "ogg").contains(extension(path)); }
    public static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    public static ResourceKind kind(Path path) { return extension(path).equals("png") ? ResourceKind.IMAGE : ResourceKind.SOUND; }

    public static Imported importFile(ProjectStore store, Path project, Path source) throws IOException {
        if (!Files.isRegularFile(source) || !supported(source)) throw new IOException("PNG / OGG Vorbis required");
        if (Files.size(source) > ProjectBlob.MAX_BYTES) throw new ProjectException("too_large");
        byte[] bytes;
        try (var input = Files.newInputStream(source)) {
            bytes = input.readNBytes(ProjectBlob.MAX_BYTES + 1);
        }
        if (bytes.length > ProjectBlob.MAX_BYTES) throw new ProjectException("too_large");
        JsonObject data = new JsonObject();
        String extension = extension(source);
        if (extension.equals("png")) {
            byte[] signature = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
            if (bytes.length < 24 || !Arrays.equals(signature, Arrays.copyOf(bytes, 8))) throw new IOException("Invalid PNG");
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IOException("Invalid PNG");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > 16384 || height > 16384 || (long)width * height > 32 * 1024 * 1024)
                        throw new IOException("Image dimensions exceed 16384 px / 32 MP");
                    reader.read(0).flush();
                    data.addProperty("width", width);
                    data.addProperty("height", height);
                } finally { reader.dispose(); }
            }
        } else {
            try (var audio = new JOrbisAudioStream(new ByteArrayInputStream(bytes))) {
                var format = audio.getFormat();
                if (format.getChannels() < 1 || format.getChannels() > 2) throw new IOException("Mono/stereo OGG Vorbis required");
                boolean decoded = false;
                while (audio.readChunk(sample -> {})) decoded = true;
                if (!decoded) throw new IOException("Empty OGG Vorbis");
                data.addProperty("channels", format.getChannels());
                data.addProperty("sample_rate", (int)format.getSampleRate());
                data.addProperty("event", "");
                data.addProperty("stream", false);
            }
        }
        ProjectBlob blob = store.storeBlob(project, bytes, extension);
        data.addProperty("blob", blob.id());
        data.addProperty("bytes", bytes.length);
        return new Imported(blob, data);
    }
}
