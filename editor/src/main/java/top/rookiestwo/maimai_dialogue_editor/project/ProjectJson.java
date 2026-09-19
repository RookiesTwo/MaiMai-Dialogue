package top.rookiestwo.maimai_dialogue_editor.project;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Shared strict JSON and content fingerprints for the editor's versioned local store. */
final class ProjectJson {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    static byte[] bytes(JsonElement value) { return (JSON.toJson(value) + "\n").getBytes(StandardCharsets.UTF_8); }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static JsonElement parse(byte[] bytes) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
            reader.setLenient(false);
            JsonElement value = JSON.getAdapter(JsonElement.class).read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new ProjectException("invalid_format");
            return value;
        } catch (JsonParseException | IllegalStateException | com.google.gson.stream.MalformedJsonException | java.io.EOFException exception) {
            throw new ProjectException("invalid_format");
        }
    }
    static byte[] read(Path path, int limit) throws IOException {
        if (Files.size(path) > limit) throw new ProjectException("too_large");
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw new ProjectException("too_large");
            return bytes;
        }
    }
}
