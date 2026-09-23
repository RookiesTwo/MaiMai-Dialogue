package top.rookiestwo.maimai_dialogue_editor.project;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.*;

/** Small, optional state files. Call only on the editor IO executor. */
public final class EditorSessionStore {
    public static final String PROJECT_FILE = "editor-state.json";
    public static final String SESSION_FILE = "editor-session.json";
    private static final int LIMIT = 1024 * 1024;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final ProjectStore projects;

    public EditorSessionStore(ProjectStore projects) { this.projects = projects; }

    public EditorSessionState read(Path directory) {
        try {
            Path file = projects.managedPath(directory, PROJECT_FILE);
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return EditorSessionState.defaults();
            var state = ProjectJson.JSON.fromJson(ProjectJson.parse(ProjectJson.read(file, LIMIT)), EditorSessionState.class);
            return state == null ? EditorSessionState.defaults() : state;
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Cannot restore editor state from {}", directory, failure);
            return EditorSessionState.defaults();
        }
    }

    public void write(Path directory, EditorSessionState state) throws IOException {
        Path target = projects.managedPath(directory, PROJECT_FILE);
        // Usage preferences must never create or save an unsaved project draft.
        if (!Files.isRegularFile(directory.resolve(ProjectStore.FILE_NAME), LinkOption.NOFOLLOW_LINKS)) return;
        write(target, ProjectJson.bytes(ProjectJson.JSON.toJsonTree(state)),
                () -> projects.managedPath(directory, PROJECT_FILE));
    }

    public Path lastProject() {
        try {
            Path file = sessionFile();
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null;
            var json = ProjectJson.parse(ProjectJson.read(file, LIMIT)).getAsJsonObject();
            if (json.get("version").getAsInt() != 1 || !json.has("last_project") || json.get("last_project").isJsonNull()) return null;
            String name = json.get("last_project").getAsString();
            if (name.isBlank() || name.equals(".") || name.equals("..") || name.contains("/")
                    || name.contains("\\") || name.contains(":")) return null;
            Path directory = projects.root().resolve(name);
            projects.validateDirectory(directory);
            return Files.isRegularFile(directory.resolve(ProjectStore.FILE_NAME), LinkOption.NOFOLLOW_LINKS) ? directory : null;
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Cannot restore last editor project", failure);
            return null;
        }
    }

    public void remember(Path directory) throws IOException {
        if (directory != null) {
            projects.validateDirectory(directory);
            if (!Files.isRegularFile(directory.resolve(ProjectStore.FILE_NAME), LinkOption.NOFOLLOW_LINKS)) return;
        }
        Path target = sessionFile();
        Files.createDirectories(projects.root());
        sessionFile();
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.addProperty("last_project", directory == null ? null : directory.getFileName().toString());
        write(target, ProjectJson.bytes(json), this::sessionFile);
    }

    private Path sessionFile() throws IOException {
        projects.checkRoot();
        Path target = projects.root().resolve(SESSION_FILE);
        if (Files.isSymbolicLink(target) || Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !target.toRealPath().getParent().equals(projects.root().toRealPath()))
            throw new ProjectException("outside_projects");
        return target;
    }

    private static void write(Path target, byte[] bytes, Check check) throws IOException {
        if (bytes.length > LIMIT) throw new ProjectException("too_large");
        Path temporary = Files.createTempFile(target.getParent(), ".maimai-editor-", ".tmp");
        try {
            ProjectStore.writeForced(temporary, bytes);
            check.run();
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    @FunctionalInterface private interface Check { Path run() throws IOException; }
}
