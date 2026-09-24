package top.rookiestwo.maimai_dialogue_editor.document;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import top.rookiestwo.maimai_dialogue.presentation.action.*;
import top.rookiestwo.maimai_dialogue_editor.document.field.NumberField;
import java.math.BigDecimal;
import java.util.*;

/** Action numbers are deltas from the step's accumulated scene state, not initial placement. */
public final class ActionFields {
    private ActionFields() {}
    public static final List<String> TRACKS = List.of("x", "y", "scale", "opacity");
    public static final List<String> COMPONENTS = List.of("x", "y", "scale", "opacity", "variant", "visible", "sound", "bgm");
    public record Number(NumberField constraints, float quickMin, float quickMax, boolean required) {
        public Number(String path, float fallback, float min, float max, boolean integer, float quickMin, float quickMax, boolean required) {
            this(new NumberField(path, fallback, min, max, integer), quickMin, quickMax, required);
        }
        public String path() { return constraints.name(); }
        public float fallback() { return constraints.fallback(); }
        public float min() { return constraints.minimum(); }
        public float max() { return constraints.maximum(); }
        public boolean integer() { return constraints.integer(); }
        public JsonPrimitive parse(String raw) {
            if (raw.isBlank()) return required ? new JsonPrimitive(fallback()) : null;
            var number = new BigDecimal(raw.strip());
            if (!Float.isFinite(number.floatValue()) || number.doubleValue() < min() || number.doubleValue() > max())
                throw new IllegalArgumentException("Out of range");
            if (integer()) number.intValueExact();
            return new JsonPrimitive(number);
        }
    }
    public static Number time(String path, float fallback) { return new Number(path, fallback, 0, 60000, true, 0, 3000, false); }
    public static Number fraction(String path, float fallback, boolean required) { return new Number(path, fallback, 0, 1, false, 0, 1, required); }
    public static Number delta(String path, String track) { return new Number(path, 0, -Float.MAX_VALUE, Float.MAX_VALUE, false, -1,
            track.equals("scale") ? 4 : track.equals("opacity") ? 1 : Float.NaN, true); }
    public static Number volume(String path) { return new Number(path, 1, 0, 1, false, 0, 1, false); }
    public static Number pitch(String path) { return new Number(path, 1, .5f, 2, false, .5f, 2, false); }
    public static JsonElement get(JsonElement root, String path) {
        JsonElement value = root;
        for (String part : path.split("\\.")) {
            if (value instanceof JsonObject object) value = object.get(part);
            else if (value instanceof JsonArray array && part.matches("\\d+")) {
                int index = Integer.parseInt(part); value = index < array.size() ? array.get(index) : null;
            } else return null;
        }
        return value;
    }
    public static String text(JsonElement root, String path, String fallback) {
        var value = get(root, path); return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }
    public static void set(JsonObject root, String path, JsonElement value) {
        String[] parts = path.split("\\."); JsonElement parent = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (parent instanceof JsonObject object) {
                if (!object.has(parts[i])) object.add(parts[i], new JsonObject());
                parent = object.get(parts[i]);
            } else if (parent instanceof JsonArray array) parent = array.get(Integer.parseInt(parts[i]));
            else return;
        }
        if (parent instanceof JsonObject object) {
            if (value == null) object.remove(parts[parts.length - 1]); else object.add(parts[parts.length - 1], value);
        }
    }
    public static Map<String, String> errors(JsonElement value) {
        var errors = new LinkedHashMap<String, String>();
        if (!(value instanceof JsonObject data)) { errors.put("", "Expected an action object"); return errors; }
        for (String track : TRACKS) if (data.has(track)) {
            var frames = data.get(track);
            if (frames instanceof JsonArray array) for (int i = 0; i < array.size(); i++) {
                String prefix = track + "[" + i + "]";
                var frame = array.get(i);
                for (String field : List.of("at", "value")) {
                    var number = frame instanceof JsonObject object ? object.get(field) : null;
                    try {
                        if (number == null || !number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber()
                                || !Float.isFinite(number.getAsFloat())) throw new IllegalArgumentException();
                    } catch (RuntimeException invalid) { errors.put(prefix + "." + field, "Expected a finite number"); }
                }
            }
            check(errors, track, frames, NumericTrack.CODEC);
        }
        if (data.has("variant")) check(errors, "variant", data.get("variant"), VariantChange.CODEC);
        if (data.has("visible")) check(errors, "visible", data.get("visible"), VisibilityChange.CODEC);
        if (data.has("sound")) check(errors, "sound", data.get("sound"), top.rookiestwo.maimai_dialogue.audio.SoundSpec.CODEC);
        if (data.has("bgm")) check(errors, "bgm", data.get("bgm"), top.rookiestwo.maimai_dialogue.audio.BgmOperation.CODEC);
        if (errors.isEmpty()) check(errors, "", data, SceneAction.CODEC);
        return errors;
    }
    private static void check(Map<String, String> errors, String path, JsonElement value, Codec<?> codec) {
        try { codec.parse(JsonOps.INSTANCE, value).error().ifPresent(error -> errors.put(path, error.message())); }
        catch (RuntimeException error) { errors.put(path, String.valueOf(error.getMessage())); }
    }
    public static JsonObject preset(String id) {
        var action = new JsonObject();
        String track = switch (id) { case "move_x" -> "x"; case "move_y" -> "y"; case "scale" -> "scale"; case "fade_in", "fade_out" -> "opacity"; default -> ""; };
        if (!track.isEmpty()) {
            var frames = new JsonArray(); var end = new JsonObject();
            end.addProperty("at", 1); end.addProperty("value", id.equals("fade_out") ? -1 : id.equals("fade_in") ? 1 : .1);
            frames.add(end); action.add(track, frames);
        } else if (id.equals("show") || id.equals("hide")) {
            var change = new JsonObject(); change.addProperty("value", id.equals("show")); action.add("visible", change); action.addProperty("duration_ms", 0);
        } else if (id.equals("sound")) {
            var sound = new JsonObject(); sound.addProperty("sound", ""); action.add("sound", sound);
        } else if (id.equals("stop_bgm")) {
            var bgm = new JsonObject(); bgm.addProperty("type", "stop"); action.add("bgm", bgm);
        }
        return action;
    }
}
