package top.rookiestwo.maimai_dialogue_editor.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Blocking disk operations. Call only from the editor IO executor. */
public final class ProjectStore {
    public static final String FILE_NAME = "project.maimai.json";
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .serializeNulls().create();
    private final Path projectsDirectory;

    public ProjectStore(Path projectsDirectory) {
        this.projectsDirectory = projectsDirectory.toAbsolutePath().normalize();
    }

    public record Entry(Path directory, String name, String namespace, long modifiedMillis, String errorReason) {
        public boolean canOpen() { return errorReason == null; }
    }

    public record SavedCopy(Path directory, String fingerprint) {
    }

    public record Loaded(ProjectDraft draft, String fingerprint) {
    }

    public Loaded open(Path directory) throws IOException {
        validateDirectory(directory);
        byte[] bytes = read(directory.resolve(FILE_NAME));
        try (JsonReader reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
            reader.setLenient(false);
            JsonElement json = JSON.getAdapter(JsonElement.class).read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new ProjectException("invalid_format");
            ProjectDraft draft = ProjectDraft.fromJson(json);
            return new Loaded(draft, fingerprint(bytes));
        } catch (JsonParseException | IllegalStateException | MalformedJsonException | java.io.EOFException exception) {
            throw new ProjectException("invalid_format");
        }
    }

    /** A null expected fingerprint means a new file: never overwrite another project. */
    public String save(Path directory, ProjectDraft draft, String expectedFingerprint) throws IOException {
        validateDirectory(directory);
        byte[] bytes = (JSON.toJson(draft.toJson()) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            throw new ProjectException("too_large");
        }
        Path target = directory.resolve(FILE_NAME);
        checkUnchanged(target, expectedFingerprint);
        Files.createDirectories(directory);
        validateDirectory(directory);
        Path temporary = Files.createTempFile(directory, ".maimai-save-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            validateDirectory(directory);
            checkUnchanged(target, expectedFingerprint);
            if (expectedFingerprint == null) {
                // No REPLACE_EXISTING: if another creator wins the race, keep their file intact.
                Files.move(temporary, target);
            } else {
                // If atomic replacement is unsupported, fail and keep the original instead of truncating it.
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return fingerprint(bytes);
    }

    /** 目录由程序分配；既有文件、空目录和链接都视为已占用。新建草稿不立即写盘。 */
    public Path allocateDirectory(String seed) throws IOException {
        checkRoot();
        String base = ProjectNames.suggestNamespace(seed);
        Path candidate = projectsDirectory.resolve(base);
        for (int suffix = 2; Files.exists(candidate, LinkOption.NOFOLLOW_LINKS); suffix++) {
            candidate = projectsDirectory.resolve(base + "_" + suffix);
        }
        validateDirectory(candidate);
        return candidate;
    }

    public SavedCopy saveCopy(ProjectDraft draft) throws IOException {
        Path directory = allocateDirectory(draft.namespace() + "_copy");
        return new SavedCopy(directory, save(directory, draft, null));
    }

    /** 只扫描固定根目录的直接子项目；使用项目文件修改时间，而非目录创建时间。 */
    public List<Entry> listProjects() throws IOException {
        checkRoot();
        if (!Files.exists(projectsDirectory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        List<Entry> entries = new ArrayList<>();
        try (var children = Files.list(projectsDirectory)) {
            for (Path directory : children.toList()) {
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) continue;
                Path file = directory.resolve(FILE_NAME);
                try {
                    validateDirectory(directory);
                } catch (IOException exception) {
                    // 不沿符号链接或 junction 读取范围外的项目。
                    continue;
                }
                if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) continue;
                long modified = 0;
                try {
                    modified = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis();
                    Loaded loaded = open(directory);
                    entries.add(new Entry(directory, loaded.draft().name(), loaded.draft().namespace(), modified, null));
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

    private void checkRoot() throws IOException {
        if (Files.isSymbolicLink(projectsDirectory)) throw new ProjectException("outside_projects");
        if (Files.exists(projectsDirectory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(projectsDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new ProjectException("not_directory");
        }
    }

    private void validateDirectory(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!projectsDirectory.equals(normalized.getParent())) throw new ProjectException("outside_projects");
        checkRoot();
        if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(normalized)) throw new ProjectException("outside_projects");
            if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) throw new ProjectException("not_directory");
            if (!projectsDirectory.toRealPath().equals(normalized.toRealPath().getParent())) {
                throw new ProjectException("outside_projects");
            }
        }
        Path file = normalized.resolve(FILE_NAME);
        if (Files.isSymbolicLink(file)) throw new ProjectException("outside_projects");
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                && !file.toRealPath().getParent().equals(normalized.toRealPath())) {
            throw new ProjectException("outside_projects");
        }
    }

    private static void checkUnchanged(Path target, String expected) throws IOException {
        if (expected == null) {
            if (Files.exists(target)) {
                throw new ProjectException("already_exists");
            }
        } else if (!Files.isRegularFile(target) || !fingerprint(read(target)).equals(expected)) {
            throw new ProjectException("external_change");
        }
    }

    private static byte[] read(Path path) throws IOException {
        if (Files.size(path) > MAX_BYTES) {
            throw new ProjectException("too_large");
        }
        // Bound the read even if a different process grows the file after size().
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new ProjectException("too_large");
            }
            return bytes;
        }
    }

    private static String fingerprint(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
