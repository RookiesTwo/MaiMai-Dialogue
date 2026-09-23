package top.rookiestwo.maimai_dialogue_editor.project;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/** IO-side save cursor. Each queued write uses the fingerprint published by the preceding write. */
final class ProjectSaveSession {
    record Saved(long sequence, ProjectDraft draft, String fingerprint, Instant savedAt) {}
    private final ProjectStore store;
    private final Path directory;
    private final ProjectStore.VerifiedFiles verified = new ProjectStore.VerifiedFiles();
    private String fingerprint;
    private long sequence;

    ProjectSaveSession(ProjectStore store, Path directory, String fingerprint) {
        this.store = store; this.directory = directory; this.fingerprint = fingerprint;
    }

    synchronized Saved save(ProjectDraft snapshot, boolean automatic) throws IOException {
        fingerprint = store.save(directory, snapshot, fingerprint, automatic ? verified : null);
        return new Saved(++sequence, snapshot, fingerprint, Instant.now());
    }
}
