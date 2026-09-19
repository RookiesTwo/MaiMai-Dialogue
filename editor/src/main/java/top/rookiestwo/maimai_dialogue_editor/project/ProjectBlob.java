package top.rookiestwo.maimai_dialogue_editor.project;

import java.io.IOException;
import java.util.Objects;

/** An immutable binary revision backed by project storage, never retained in undo snapshots as byte arrays. */
public final class ProjectBlob {
    public static final int MAX_BYTES = 64 * 1024 * 1024;
    @FunctionalInterface public interface Reader { byte[] read() throws IOException; }
    private final String id;
    private final long size;
    private volatile Reader reader;

    public ProjectBlob(String id, long size, Reader reader) {
        if (!validId(id) || size <= 0 || size > MAX_BYTES) throw new IllegalArgumentException("Invalid media blob");
        this.id = id; this.size = size; this.reader = Objects.requireNonNull(reader);
    }
    public static boolean validId(String id) { return id != null && id.matches("[0-9a-f]{64}\\.(png|ogg)"); }
    public String id() { return id; }
    public long size() { return size; }
    /** After a successful save-as, shared snapshots may use the verified copy without retaining binary bytes. */
    void useStoredCopy(Reader reader) { this.reader = Objects.requireNonNull(reader); }
    public byte[] read() throws IOException {
        byte[] bytes = reader.read();
        if (bytes.length != size || !ProjectJson.hash(bytes).equals(id.substring(0, 64)))
            throw new ProjectException("external_change");
        return bytes;
    }
}
