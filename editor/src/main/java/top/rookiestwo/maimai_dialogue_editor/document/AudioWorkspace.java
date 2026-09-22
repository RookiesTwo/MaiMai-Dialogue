package top.rookiestwo.maimai_dialogue_editor.document;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import top.rookiestwo.maimai_dialogue.audio.*;
import top.rookiestwo.maimai_dialogue_editor.project.*;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/** Audio drafts retain omitted fields, explicit silence and unrecognized fields until edited. */
public final class AudioWorkspace {
    public record Target(ResourceKey key, int step, boolean bgm) {}
    public record Number(String name, float fallback, float minimum, float maximum, boolean integer, int quickMaximum) {
        public JsonPrimitive parse(String raw) {
            if (raw.isBlank()) return null;
            var value = new BigDecimal(raw.strip());
            if (value.compareTo(BigDecimal.valueOf(minimum)) < 0 || value.compareTo(BigDecimal.valueOf(maximum)) > 0)
                throw new IllegalArgumentException("Out of range");
            if (integer) value.intValueExact();
            return new JsonPrimitive(value);
        }
        public SceneWorkspace.NumberField control() { return new SceneWorkspace.NumberField(name, fallback, minimum, maximum, integer); }
    }
    public static final Number BGM_VOLUME = new Number("volume", 1, 0, 1, false, 1);
    public static final Number FADE = new Number("fade_ms", 500, 0, 60000, true, 3000);
    public static final Number TYPING_VOLUME = new Number("volume", .15f, 0, 1, false, 1);
    public static final Number PITCH = new Number("pitch", 1, .5f, 2, false, 2);
    public static final Number INTERVAL = new Number("min_interval_ms", 50, 0, 60000, true, 200);
    private final ProjectWorkspace project;
    private Gesture gesture;
    public AudioWorkspace(ProjectWorkspace project) { this.project = project; }

    public Target target() {
        var selected = project.resources().selection();
        var key = selected.owner();
        if (key == null || !key.equals(project.resources().opened()) || project.content().snapshot().data() == null) return null;
        if (key.kind() == ResourceKind.DIALOGUE) {
            if (selected.isStep()) return new Target(key, selected.stepIndex(), false);
            if (selected.type() == ResourceTree.Type.RESOURCE) return new Target(key, -2, true);
        }
        return key.kind() == ResourceKind.SPEAKER && selected.type() == ResourceTree.Type.RESOURCE ? new Target(key, -2, false) : null;
    }
    public boolean active() { return project.content().active() && target() != null; }
    private JsonObject owner(JsonObject root, Target target) { return target.step() == -2 ? root : DialogueDraft.node(root, target.step()); }
    private String property(Target target) { return target.bgm() ? "bgm" : "typewriter_sound"; }
    public JsonElement value() {
        var target = target();
        var owner = target == null ? null : owner(project.content().snapshot().data(), target);
        return owner == null ? null : owner.get(property(target));
    }
    public String mode() {
        var value = value(); var target = target();
        if (value == null) return "inherit";
        if (target != null && target.bgm()) return DialogueDraft.string(DialogueDraft.object(value), "type");
        if (value.isJsonObject()) return "custom";
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && !value.getAsBoolean() ? "silent" : "invalid";
    }
    public String field(String name, String fallback) { return SceneWorkspace.text(DialogueDraft.object(value()), name, fallback); }
    public String number(Number field) {
        if (gesture != null && gesture.valid() && gesture.field.equals(field)) return gesture.raw;
        return field(field.name(), new BigDecimal(Float.toString(field.fallback())).stripTrailingZeros().toPlainString());
    }
    public String sound() { return field("sound", target() != null && !target().bgm() ? TypewriterSound.DEFAULT_SOUND.toString() : ""); }
    public void mode(String mode) {
        if (!active()) return;
        var target = target();
        if (!(target.bgm() ? List.of("inherit", "play", "stop") : List.of("inherit", "custom", "silent")).contains(mode)) return;
        endGesture(false); project.endEdit();
        mutate(null, node -> {
            String property = property(target);
            if (mode.equals("inherit")) { node.remove(property); return; }
            if (mode.equals("silent")) { node.addProperty(property, false); return; }
            var object = DialogueDraft.object(node.get(property));
            if (object == null) object = new JsonObject();
            if (target.bgm()) {
                object.addProperty("type", mode);
                if (mode.equals("stop")) { object.remove("sound"); object.remove("volume"); object.remove("loop"); }
                else if (!object.has("sound")) object.addProperty("sound", "");
            }
            node.add(property, object);
        });
        project.endEdit();
    }
    private void objectEdit(String field, Consumer<JsonObject> mutation) {
        if (!active() || !(value() instanceof JsonObject)) return;
        var target = target();
        mutate(property(target) + "/" + field, node -> mutation.accept(node.getAsJsonObject(property(target))));
    }
    public void sound(String id) {
        if (!List.of("play", "custom").contains(mode())) return;
        objectEdit("sound", value -> {
            if (id.isBlank() && !target().bgm()) value.remove("sound"); else value.addProperty("sound", id.strip());
        });
    }
    public void loop(boolean value) {
        if (!"play".equals(mode())) return;
        objectEdit("loop", object -> { if (value) object.remove("loop"); else object.addProperty("loop", false); });
    }
    public String number(Number field, String raw) {
        if (!active()) return "";
        JsonPrimitive value;
        try { value = field.parse(raw); } catch (RuntimeException invalid) { return "audio.invalid_number"; }
        objectEdit(field.name(), object -> { if (value == null) object.remove(field.name()); else object.add(field.name(), value); });
        return "";
    }
    private void mutate(String group, Consumer<JsonObject> mutation) {
        var target = target();
        if (!active() || target == null) return;
        var data = project.content().snapshot().data().deepCopy(); var node = owner(data, target);
        if (node == null) return;
        mutation.accept(node);
        if (!data.equals(project.content().snapshot().data())) project.editAsset(target.key(), data,
                group == null ? null : "audio/" + target.step() + "/" + group);
    }
    public Optional<BgmOperation> bgm() { return Optional.ofNullable(value()).map(value -> BgmOperation.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()); }
    public TypewriterSound typing() {
        // An inherited Step is auditioned by playing the Dialogue, where speaker state is available.
        return value() == null ? TypewriterSound.DEFAULT : TypewriterSound.CODEC.parse(JsonOps.INSTANCE, value()).getOrThrow();
    }
    public boolean editing() { return gesture != null && gesture.valid(); }
    public EditGesture beginGesture(Number field) {
        endGesture(false); project.endEdit();
        return active() && value() instanceof JsonObject ? gesture = new Gesture(field) : null;
    }
    public void endGesture(boolean commit) { if (gesture != null) gesture.finish(commit); }
    private final class Gesture implements EditGesture {
        final ProjectDraft before = project.draft();
        final long generation = project.projectGeneration();
        final Target target = target();
        final Number field;
        String raw;
        boolean changed;
        Gesture(Number field) { this.field = field; raw = number(field); }
        boolean valid() { return gesture == this && before == project.draft() && generation == project.projectGeneration()
                && active() && Objects.equals(target, target()); }
        @Override public boolean update(String value) {
            if (!valid()) return false;
            try { field.parse(value); } catch (RuntimeException invalid) { return false; }
            raw = value; changed = true; return true;
        }
        @Override public void finish(boolean commit) {
            if (gesture != this) return;
            boolean valid = valid(); gesture = null;
            if (commit && valid && changed) { number(field, raw); project.endEdit(); }
        }
    }
}
