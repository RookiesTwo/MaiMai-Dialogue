package top.rookiestwo.maimai_dialogue_editor.project;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Blocking disk operations. Immutable resource/index revisions are committed by replacing one small manifest. */
public final class ProjectStore {
    public static final String FILE_NAME = "project.maimai.json";
    private static final int MANIFEST_LIMIT = 1024 * 1024;
    private static final int CONTENT_LIMIT = 16 * 1024 * 1024;
    private final Path projectsDirectory;

    public ProjectStore(Path projectsDirectory) {
        this.projectsDirectory = projectsDirectory.toAbsolutePath().normalize();
    }
    public Path root() { return projectsDirectory; }

    public record Entry(Path directory, String name, String namespace, long modifiedMillis, String errorReason) {
        public boolean canOpen() { return errorReason == null; }
    }
    public record SavedCopy(Path directory, String fingerprint) {}
    public record Loaded(ProjectDraft draft, String fingerprint) {}

    /** Only metadata and the compact index are parsed here; resource bodies stay unloaded. */
    public Loaded open(Path directory) throws IOException {
        validateDirectory(directory);
        byte[] bytes = ProjectJson.read(directory.resolve(FILE_NAME), MANIFEST_LIMIT);
        JsonObject manifest = manifest(bytes);
        String indexHash = ProjectIndex.hash(manifest.get("resource_index"));
        Path indexPath = managedPath(directory, "indexes/" + indexHash + ".json");
        JsonElement index = readRevision(indexPath, indexHash);
        manifest.remove("resource_index");
        ProjectDraft draft = ProjectIndex.decode(manifest, index, (key, hash) -> () ->
                ProjectJson.parse(readRevisionBytes(resourcePath(directory, key, hash), hash)),
                (id, size) -> blob(directory, id, size));
        return new Loaded(draft, ProjectJson.hash(bytes));
    }

    /** A null expected fingerprint means a new project. Failed writes never publish a partial index. */
    public String save(Path directory, ProjectDraft draft, String expectedFingerprint) throws IOException {
        return save(directory, draft, expectedFingerprint, null);
    }

    String save(Path directory, ProjectDraft draft, String expectedFingerprint, VerifiedFiles verified) throws IOException {
        validateDirectory(directory);
        Path target = directory.resolve(FILE_NAME);
        checkUnchanged(target, expectedFingerprint);
        Files.createDirectories(directory);
        validateDirectory(directory);
        for (ProjectBlob blob : draft.blobs().values()) {
            Path path = managedPath(directory, "media/" + blob.id());
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
                verifyForSave(path, blob.id().substring(0, 64), ProjectBlob.MAX_BYTES, verified);
            else writeRevision(directory, path, blob.read(), blob.id().substring(0, 64), ProjectBlob.MAX_BYTES);
        }
        for (var entry : draft.entries().entrySet()) {
            ProjectResource resource = entry.getValue();
            Path path = resourcePath(directory, entry.getKey(), resource.fingerprint());
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                // Verify bytes without parsing/retaining unchanged JSON bodies in memory.
                verifyForSave(path, resource.fingerprint(), CONTENT_LIMIT, verified);
            } else {
                resource.load();
                writeRevision(directory, path, ProjectJson.bytes(resource.copy()), resource.fingerprint());
            }
        }
        byte[] indexBytes = ProjectJson.bytes(ProjectIndex.encode(draft));
        String indexHash = ProjectJson.hash(indexBytes);
        writeRevision(directory, managedPath(directory, "indexes/" + indexHash + ".json"), indexBytes, indexHash);
        JsonObject manifest = draft.metadata();
        manifest.addProperty("resource_index", indexHash);
        byte[] bytes = ProjectJson.bytes(manifest);
        if (bytes.length > MANIFEST_LIMIT) throw new ProjectException("too_large");
        Path temporary = Files.createTempFile(directory, ".maimai-save-", ".tmp");
        try {
            writeForced(temporary, bytes);
            validateDirectory(directory);
            checkUnchanged(target, expectedFingerprint);
            if (expectedFingerprint == null) Files.move(temporary, target);
            else Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        for (ProjectBlob blob : draft.blobs().values()) blob.useStoredCopy(() ->
                readRevisionBytes(managedPath(directory, "media/" + blob.id()), blob.id().substring(0, 64), ProjectBlob.MAX_BYTES));
        return ProjectJson.hash(bytes);
    }

    private void writeRevision(Path directory, Path target, byte[] bytes, String hash) throws IOException {
        writeRevision(directory, target, bytes, hash, CONTENT_LIMIT);
    }
    private void writeRevision(Path directory, Path target, byte[] bytes, String hash, int limit) throws IOException {
        if (bytes.length > limit) throw new ProjectException("too_large");
        if (!ProjectJson.hash(bytes).equals(hash)) throw new ProjectException("invalid_format");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            readRevisionBytes(target, hash, limit);
            return;
        }
        Files.createDirectories(target.getParent());
        managedPath(directory, directory.toAbsolutePath().normalize().relativize(target).toString());
        Path temporary = Files.createTempFile(target.getParent(), ".maimai-save-", ".tmp");
        try {
            writeForced(temporary, bytes);
            // No replacement: immutable revisions may be shared by another in-flight save.
            try { Files.move(temporary, target); }
            catch (FileAlreadyExistsException concurrent) { readRevisionBytes(target, hash, limit); }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void writeForced(Path path, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }
    private static byte[] readRevisionBytes(Path path, String hash) throws IOException {
        return readRevisionBytes(path, hash, CONTENT_LIMIT);
    }
    private static byte[] readRevisionBytes(Path path, String hash, int limit) throws IOException {
        byte[] bytes = ProjectJson.read(path, limit);
        if (!ProjectJson.hash(bytes).equals(hash)) throw new ProjectException("external_change");
        return bytes;
    }

    /** Per-open-project cache for frequent background checkpoints; manual saves still fully verify. */
    static final class VerifiedFiles {
        private final java.util.Map<Path, Stamp> files = new java.util.HashMap<>();
    }
    private record Stamp(String hash, long size, java.nio.file.attribute.FileTime modified,
                         java.nio.file.attribute.FileTime created, Object fileKey) {}
    private static Stamp stamp(Path path, String hash) throws IOException {
        var attributes = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) throw new ProjectException("external_change");
        return new Stamp(hash, attributes.size(), attributes.lastModifiedTime(), attributes.creationTime(), attributes.fileKey());
    }
    private static void verifyForSave(Path path, String hash, int limit, VerifiedFiles verified) throws IOException {
        if (verified == null) { readRevisionBytes(path, hash, limit); return; }
        Stamp before = stamp(path, hash);
        if (before.equals(verified.files.get(path))) return;
        readRevisionBytes(path, hash, limit);
        if (!before.equals(stamp(path, hash))) throw new ProjectException("external_change");
        verified.files.put(path, before);
    }
    public ProjectBlob storeBlob(Path directory, byte[] bytes, String extension) throws IOException {
        String hash = ProjectJson.hash(bytes);
        String id = hash + "." + extension;
        if (!ProjectBlob.validId(id) || bytes.length == 0) throw new ProjectException("invalid_format");
        validateDirectory(directory);
        Files.createDirectories(directory);
        writeRevision(directory, managedPath(directory, "media/" + id), bytes, hash, ProjectBlob.MAX_BYTES);
        return blob(directory, id, bytes.length);
    }
    private ProjectBlob blob(Path directory, String id, long size) {
        return new ProjectBlob(id, size, () ->
                readRevisionBytes(managedPath(directory, "media/" + id), id.substring(0, 64), ProjectBlob.MAX_BYTES));
    }
    private static JsonElement readRevision(Path path, String hash) throws IOException {
        return ProjectJson.parse(readRevisionBytes(path, hash));
    }
    private Path resourcePath(Path directory, ResourceKey key, String hash) throws IOException {
        return managedPath(directory, "resources/" + key.kind().directory() + "/" + hash + ".json");
    }
    private static JsonObject manifest(byte[] bytes) throws IOException {
        JsonElement parsed = ProjectJson.parse(bytes);
        if (!(parsed instanceof JsonObject object)) throw new ProjectException("invalid_format");
        ProjectDraft.validateMetadata(object);
        ProjectIndex.hash(object.get("resource_index"));
        if (object.has("resources")) throw new ProjectException("invalid_format");
        return object;
    }

    /** Direct children only, allocated by the application; existing directories/links are occupied. */
    public Path allocateDirectory(String seed) throws IOException {
        checkRoot();
        String base = ProjectNames.suggestNamespace(seed);
        Path candidate = projectsDirectory.resolve(base);
        for (int suffix = 2; Files.exists(candidate, LinkOption.NOFOLLOW_LINKS); suffix++)
            candidate = projectsDirectory.resolve(base + "_" + suffix);
        validateDirectory(candidate);
        return candidate;
    }
    public SavedCopy saveCopy(ProjectDraft draft) throws IOException {
        Path directory = allocateDirectory(draft.namespace() + "_copy");
        return new SavedCopy(directory, save(directory, draft, null));
    }

    /** Listing projects never reads their indexes or resource files. */
    public List<Entry> listProjects() throws IOException {
        checkRoot();
        if (!Files.exists(projectsDirectory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        List<Entry> entries = new ArrayList<>();
        try (var children = Files.list(projectsDirectory)) {
            for (Path directory : children.toList()) {
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) continue;
                Path file = directory.resolve(FILE_NAME);
                try { validateDirectory(directory); }
                catch (IOException exception) { continue; }
                if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) continue;
                long modified = 0;
                try {
                    modified = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis();
                    JsonObject metadata = manifest(ProjectJson.read(file, MANIFEST_LIMIT));
                    entries.add(new Entry(directory, metadata.get("name").getAsString(),
                            metadata.get("namespace").getAsString(), modified, null));
                } catch (IOException exception) {
                    String reason = exception instanceof ProjectException error ? error.reason() : "io";
                    entries.add(new Entry(directory, directory.getFileName().toString(), "", modified, reason));
                }
            }
        }
        entries.sort(Comparator.comparingLong(Entry::modifiedMillis).reversed()
                .thenComparing(entry -> entry.directory().getFileName().toString()));
        return List.copyOf(entries);
    }

    void checkRoot() throws IOException {
        if (Files.isSymbolicLink(projectsDirectory)) throw new ProjectException("outside_projects");
        if (Files.exists(projectsDirectory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(projectsDirectory, LinkOption.NOFOLLOW_LINKS))
            throw new ProjectException("not_directory");
    }
    void validateDirectory(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!projectsDirectory.equals(normalized.getParent())) throw new ProjectException("outside_projects");
        checkRoot();
        if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(normalized)) throw new ProjectException("outside_projects");
            if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) throw new ProjectException("not_directory");
            if (!projectsDirectory.toRealPath().equals(normalized.toRealPath().getParent()))
                throw new ProjectException("outside_projects");
        }
        Path file = normalized.resolve(FILE_NAME);
        if (Files.isSymbolicLink(file)) throw new ProjectException("outside_projects");
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                && !file.toRealPath().getParent().equals(normalized.toRealPath()))
            throw new ProjectException("outside_projects");
    }

    /** Generated relative paths only. Check every ancestor to reject symlinks and Windows junction escapes. */
    Path managedPath(Path directory, String relative) throws IOException {
        validateDirectory(directory);
        Path root = directory.toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root)) throw new ProjectException("outside_projects");
        Path cursor = root;
        for (Path part : root.relativize(target)) {
            cursor = cursor.resolve(part);
            if (Files.isSymbolicLink(cursor)) throw new ProjectException("outside_projects");
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                    && !cursor.toRealPath().startsWith(root.toRealPath()))
                throw new ProjectException("outside_projects");
        }
        return target;
    }
    private static void checkUnchanged(Path target, String expected) throws IOException {
        if (expected == null) {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new ProjectException("already_exists");
        } else if (!Files.isRegularFile(target)
                || !ProjectJson.hash(ProjectJson.read(target, MANIFEST_LIMIT)).equals(expected))
            throw new ProjectException("external_change");
    }
}
