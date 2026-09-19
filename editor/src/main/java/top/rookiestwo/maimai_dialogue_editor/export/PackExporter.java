package top.rookiestwo.maimai_dialogue_editor.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import top.rookiestwo.maimai_dialogue_editor.content.ProjectDefinitions;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialPack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;

/** Publishes a complete pair in one atomic directory rename, never overwrites an earlier export. */
public final class PackExporter {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);
    private final Path root;
    private final FileWriter writer;
    private final Clock clock;

    @FunctionalInterface interface FileWriter { void write(Path path, byte[] bytes) throws IOException; }
    public PackExporter(Path root) { this(root, Clock.systemDefaultZone()); }
    PackExporter(Path root, Clock clock) {
        this(root, (path, bytes) -> Files.write(path, bytes, StandardOpenOption.CREATE_NEW), clock);
    }
    PackExporter(Path root, FileWriter writer) { this(root, writer, Clock.systemDefaultZone()); }
    PackExporter(Path root, FileWriter writer, Clock clock) {
        this.root = root.toAbsolutePath().normalize(); this.writer = writer; this.clock = clock;
    }

    public Path export(ValidationReport report, int resourceFormat, int dataFormat) throws IOException {
        if (!report.valid()) throw new IllegalArgumentException("Cannot export an invalid project");
        if (!report.source().hasValidMetadata() || !ProjectValidator.portablePath(report.source().namespace()))
            throw new IllegalArgumentException("Invalid project namespace");
        for (var group : report.source().resources().entrySet()) {
            if (!group.getValue().isJsonObject()) throw new IllegalArgumentException("Invalid resource group");
            for (String path : group.getValue().getAsJsonObject().keySet()) {
                if (!ResourceKey.validPath(path) || !ProjectValidator.portablePath(path))
                    throw new IllegalArgumentException("Invalid resource path: " + path);
            }
        }
        if (resourceFormat <= 0 || dataFormat <= 0) throw new IllegalArgumentException("Invalid pack formats");
        if (Files.isSymbolicLink(root)) throw new IOException("Export directory is a symbolic link");
        Files.createDirectories(root);
        Path realRoot = root.toRealPath();
        if (!realRoot.equals(root.getParent().toRealPath().resolve(root.getFileName())))
            throw new IOException("Export directory is redirected");
        String name = safeName(report.source().name());
        String resourcePack = name + "_resourcepack";
        String dataPack = name + "_datapack";
        Path staging = Files.createTempDirectory(realRoot, ".pending-");
        try {
            write(staging.resolve(resourcePack + "/pack.mcmeta"), metadata(report.source().name(), resourceFormat));
            write(staging.resolve(dataPack + "/pack.mcmeta"), metadata(report.source().name(), dataFormat));
            MaterialPack.write(report.source(), (relative, bytes) -> writeContained(staging, resourcePack + "/" + relative, bytes));
            var resources = report.source().resources();
            for (ResourceKind kind : ResourceKind.values()) {
                var type = ProjectDefinitions.type(kind);
                if (type == null || !resources.has(kind.directory())) continue;
                for (var entry : resources.getAsJsonObject(kind.directory()).entrySet()) {
                    // Serialize once; preserve omitted fields, explicit values and option array order.
                    byte[] bytes = bytes(entry.getValue());
                    String relative = report.source().namespace() + "/" + type.directory() + "/" + entry.getKey() + ".json";
                    writeContained(staging, resourcePack + "/assets/" + relative, bytes);
                    if (kind == ResourceKind.DIALOGUE) writeContained(staging, dataPack + "/data/" + relative, bytes);
                }
            }
            return publish(staging, realRoot, name);
        } finally {
            if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
                // Only remove the temporary subtree created by this operation; never follow links.
                if (!staging.getParent().equals(realRoot)) throw new IOException("Unexpected staging path");
                try (var paths = Files.walk(staging)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static String safeName(String name) {
        String safeName = name.strip().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        // Apply the same filename rules to the dated export and its two installable packs.
        int points = safeName.codePointCount(0, safeName.length());
        if (points > 80) safeName = safeName.substring(0, safeName.offsetByCodePoints(0, 80));
        return safeName;
    }

    private Path publish(Path staging, Path realRoot, String safeName) throws IOException {
        for (LocalDateTime time = LocalDateTime.now(clock).withNano(0); ; time = time.plusSeconds(1)) {
            String directory = safeName + "_" + TIMESTAMP.format(time);
            Path target = realRoot.resolve(directory);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
            // Reserve only the name; never expose an empty final pack directory while writing.
            Path reservation = realRoot.resolve(".export-" + directory + ".lock");
            try { Files.createFile(reservation); }
            catch (FileAlreadyExistsException occupied) { continue; }
            try {
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
                // Fail closed if the filesystem cannot publish both packs atomically.
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
                return target;
            } finally {
                Files.deleteIfExists(reservation);
            }
        }
    }

    private void writeContained(Path staging, String relative, byte[] bytes) throws IOException {
        Path target = staging.resolve(relative).normalize();
        if (!target.startsWith(staging)) throw new IOException("Export path escapes temporary directory");
        write(target, bytes);
    }
    private void write(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        writer.write(target, bytes);
    }
    private static byte[] metadata(String name, int format) {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", format);
        pack.addProperty("description", name);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return bytes(root);
    }
    private static byte[] bytes(com.google.gson.JsonElement value) {
        return (JSON.toJson(value) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
