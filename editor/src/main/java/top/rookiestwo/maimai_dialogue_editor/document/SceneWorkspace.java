package top.rookiestwo.maimai_dialogue_editor.document;

import top.rookiestwo.maimai_dialogue.presentation.DialogueBoxLayout;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialPack;
import java.util.*;
import java.util.function.Consumer;

/** Scene draft edits and local cursors. No Views, runtime mutation or editor metadata in resource JSON. */
public final class SceneWorkspace {
    public static final List<NumberField> BOX_NUMBERS = List.of(
            new NumberField("x", DialogueBoxLayout.DEFAULT.x(), 0, 1),
            new NumberField("y", DialogueBoxLayout.DEFAULT.y(), 0, 1),
            new NumberField("width", DialogueBoxLayout.DEFAULT.width(), Float.MIN_VALUE, 1),
            new NumberField("max_height", DialogueBoxLayout.DEFAULT.maxHeight(), Float.MIN_VALUE, 1));

    public enum Part { BACKGROUND, OBJECT, FILTER, BOX }
    public record NumberField(String name, float fallback, float minimum, float maximum, boolean integer) {
        public NumberField(String name, float fallback, float minimum, float maximum) {
            this(name, fallback, minimum, maximum, false);
        }
    }
    public static final List<NumberField> OBJECT_NUMBERS = List.of(
            new NumberField("x", .5f, -Float.MAX_VALUE, Float.MAX_VALUE),
            new NumberField("y", .5f, -Float.MAX_VALUE, Float.MAX_VALUE),
            new NumberField("scale", 1, Float.MIN_VALUE, Float.MAX_VALUE),
            new NumberField("opacity", 1, 0, 1),
            new NumberField("z_index", 0, Integer.MIN_VALUE, Integer.MAX_VALUE, true),
            new NumberField("scale_x", 1, Float.MIN_VALUE, Float.MAX_VALUE),
            new NumberField("scale_y", 1, Float.MIN_VALUE, Float.MAX_VALUE));
    public static final List<NumberField> COLOR_NUMBERS = List.of(
            new NumberField("brightness", 0, -100, 100), new NumberField("contrast", 0, -100, 100), new NumberField("saturation", 0, -100, 100));
    public static final List<NumberField> CRT_NUMBERS = List.of(
            new NumberField("curvature", .08f, 0, 1), new NumberField("scanline_strength", .22f, 0, 1),
            new NumberField("mask_strength", .12f, 0, 1), new NumberField("chromatic_aberration", 1, 0, 4),
            new NumberField("vignette", .18f, 0, 1), new NumberField("noise", .025f, 0, 1),
            new NumberField("flicker", .01f, 0, 1), new NumberField("bloom", .1f, 0, 1),
            new NumberField("edge_feather", 0, 0, 1));
    private final ProjectWorkspace project;
    private final Runnable changed;
    private final Map<ResourceKey, String> objects = new HashMap<>();
    private final Map<String, String> variants = new HashMap<>();
    private long generation = -1;
    private long assetRequest;
    private final Map<Part, Long> assetRequests = new EnumMap<>(Part.class);
    public record Transform(String objectId, float x, float y, float scaleX, float scaleY) {}
    private record Drag(ProjectDraft before, ResourceKey key, long project, Transform origin, Transform position) {}
    private Drag drag;
    private ContentWorkspace.Snapshot dragSnapshot;
    private Consumer<Boolean> liveChanged = immediate -> {};
    private NumberDrag numberDrag;
    public record NumberPreview(Part part, String objectId, String field, float value) {}

    /** Preview-only changes never notify the project or create draft/history revisions. */
    public void setLiveListener(Consumer<Boolean> listener) { liveChanged = Objects.requireNonNull(listener); }
    public NumberPreview numberPreview() {
        if (numberDrag != null && !numberDrag.valid()) numberDrag = null;
        return numberDrag == null || numberDrag.value == null ? null : new NumberPreview(numberDrag.part,
                numberDrag.object, numberDrag.field.name(), numberDrag.value.floatValue());
    }
    public NumberDrag beginNumberDrag(Part part, NumberField field) {
        endNumberDrag(false);
        if (!active() || drag != null || field.integer()) return null;
        if (part != Part.BOX && part(part) == null) return null;
        project.endEdit();
        return numberDrag = new NumberDrag(part, field);
    }
    public void endNumberDrag(boolean commit) { if (numberDrag != null) numberDrag.finish(commit); }

    /** Bound to the original document and object; stale View callbacks cannot edit a new selection. */
    public final class NumberDrag {
        private final ProjectDraft before = project.draft();
        private final long owner = project.projectGeneration();
        private final ResourceKey key = snapshot().key();
        private final String object = objectId();
        private final Part part;
        private final NumberField field;
        private final String original;
        private java.math.BigDecimal value;
        private NumberDrag(Part part, NumberField field) {
            this.part = part; this.field = field;
            original = text(part == Part.BOX ? box() : part(part), field.name(), Float.toString(field.fallback()));
        }
        private boolean valid() {
            return numberDrag == this && owner == project.projectGeneration() && before == project.draft()
                    && active() && key.equals(snapshot().key()) && (part != Part.OBJECT || object.equals(objectId()));
        }
        public boolean update(String text) {
            if (!valid()) return false;
            try {
                var next = new java.math.BigDecimal(text);
                float value = next.floatValue();
                if (!Float.isFinite(value) || value < field.minimum() || value > field.maximum()) return false;
                this.value = next;
                liveChanged.accept(false); return true;
            } catch (NumberFormatException invalid) { return false; }
        }
        public void finish(boolean commit) {
            if (numberDrag != this) return;
            boolean valid = valid();
            // Flush the pointer's final value before the committed draft starts asynchronous validation.
            if (commit && valid && value != null) liveChanged.accept(true);
            numberDrag = null;
            if (commit && valid && value != null && !sameNumber(original, value)) {
                if (part == Part.BOX) setBoxNumber(field, value.toPlainString());
                else setNumber(part, field, value.toPlainString());
                project.endEdit();
            }
            liveChanged.accept(false);
        }
    }
    private static boolean sameNumber(String original, java.math.BigDecimal value) {
        try { return value.compareTo(new java.math.BigDecimal(original)) == 0; }
        catch (NumberFormatException invalid) { return false; }
    }

    public SceneWorkspace(ProjectWorkspace project, Runnable changed) {
        this.project = project; this.changed = changed;
    }
    public boolean acceptsResource(ResourceKey key) { return key != null && key.kind() == ResourceKind.SCENE; }
    public ContentWorkspace.Snapshot snapshot() {
        if (generation != project.projectGeneration()) {
            generation = project.projectGeneration(); objects.clear(); variants.clear(); drag = null; dragSnapshot = null;
        }
        var state = project.content().snapshot();
        if (drag != null && (drag.before != project.draft() || drag.project != generation || !drag.key.equals(state.key())
                || !drag.key.equals(project.resources().selection().resource())
                || !drag.position.objectId.equals(objects.get(drag.key)) || !project.content().active())) drag = null;
        if (drag == null) { dragSnapshot = null; return state; }
        if (dragSnapshot != null) return dragSnapshot;
        JsonObject preview = state.data().deepCopy();
        var object = part(preview, Part.OBJECT, drag.position.objectId);
        if (object == null) { drag = null; return state; }
        writeTransform(object, drag.origin, drag.position);
        dragSnapshot = new ContentWorkspace.Snapshot(state.key(), preview, state.cursor());
        return dragSnapshot;
    }
    public boolean active() {
        var state = snapshot();
        return project.content().active() && state.key() != null && state.key().kind() == ResourceKind.SCENE
                && state.key().equals(project.resources().selection().resource()) && state.data() != null;
    }
    public static JsonObject object(JsonElement value) { return value instanceof JsonObject data ? data : null; }
    public static String text(JsonObject data, String field, String fallback) {
        JsonElement value = data == null ? null : data.get(field);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }
    public JsonObject data() { return snapshot().data(); }
    public JsonObject objectMap() { var data = data(); return data == null ? null : object(data.get("visual_objects")); }
    public String objectId() {
        var state = snapshot(); var map = objectMap();
        if (map == null || map.isEmpty()) return "";
        String selected = objects.get(state.key());
        return selected != null && (selected.isEmpty() || map.has(selected)) ? selected : map.keySet().iterator().next();
    }
    public void selectObject(String id) {
        if (!active() || objectMap() == null || (!id.isEmpty() && !objectMap().has(id))) return;
        endNumberDrag(false);
        drag = null;
        project.endEdit(); objects.put(snapshot().key(), id); changed.run();
    }
    public Transform dragPosition() { snapshot(); return drag == null ? null : drag.position; }
    public boolean beginPositionDrag(String id) {
        if (!active() || drag != null || !id.equals(objectId())) return false;
        endNumberDrag(false);
        JsonObject object = part(Part.OBJECT);
        if (object == null) return false;
        try {
            float x = Float.parseFloat(text(object, "x", "0.5")), y = Float.parseFloat(text(object, "y", "0.5"));
            float scaleX = Float.parseFloat(text(object, "scale_x", "1")), scaleY = Float.parseFloat(text(object, "scale_y", "1"));
            if (!Float.isFinite(x) || !Float.isFinite(y) || !validScale(scaleX) || !validScale(scaleY)) return false;
            project.endEdit();
            var key = snapshot().key(); objects.put(key, id);
            var origin = new Transform(id, x, y, scaleX, scaleY);
            drag = new Drag(project.draft(), key, generation, origin, origin);
            dragSnapshot = null;
            changed.run(); return true;
        } catch (NumberFormatException invalid) { return false; }
    }
    /** Transient normalized deltas. A gesture does not write drafts or history until it is finished. */
    public void movePositionDrag(float dx, float dy) {
        snapshot(); if (drag == null || !Float.isFinite(dx) || !Float.isFinite(dy)) return;
        float x = drag.origin.x + dx, y = drag.origin.y + dy;
        if (!Float.isFinite(x) || !Float.isFinite(y)) return;
        updateDrag(new Transform(drag.origin.objectId, x, y, drag.origin.scaleX, drag.origin.scaleY));
    }
    /** Absolute preview transform computed from the gesture's initial pointer and opposite handle. */
    public void resizeDrag(float x, float y, float scaleX, float scaleY) {
        snapshot(); if (drag == null || !Float.isFinite(x) || !Float.isFinite(y) || !validScale(scaleX) || !validScale(scaleY)) return;
        var object = part(object(drag.before.resource(drag.key)), Part.OBJECT, drag.origin.objectId);
        float scale;
        try { scale = Float.parseFloat(text(object, "scale", "1")); } catch (NumberFormatException invalid) { return; }
        if (!validScale(scale * scaleX) || !validScale(scale * scaleY)) return;
        updateDrag(new Transform(drag.origin.objectId, x, y, scaleX, scaleY));
    }
    private static boolean validScale(float value) { return Float.isFinite(value) && value > 0; }
    private void updateDrag(Transform next) {
        if (next.equals(drag.position)) return;
        drag = new Drag(drag.before, drag.key, drag.project, drag.origin, next);
        dragSnapshot = null;
        changed.run();
    }
    public void endPositionDrag(boolean commit) {
        snapshot(); Drag finished = drag; drag = null;
        if (finished == null) return;
        project.endEdit();
        if (commit && !finished.origin.equals(finished.position)) {
            JsonObject data = object(finished.before.resource(finished.key));
            var object = part(data, Part.OBJECT, finished.position.objectId);
            writeTransform(object, finished.origin, finished.position);
            project.editAsset(finished.key, data, null);
        }
        project.endEdit(); changed.run();
    }
    private static void writeTransform(JsonObject object, Transform origin, Transform next) {
        if (origin.x != next.x) object.addProperty("x", new java.math.BigDecimal(Float.toString(next.x)));
        if (origin.y != next.y) object.addProperty("y", new java.math.BigDecimal(Float.toString(next.y)));
        if (origin.scaleX != next.scaleX) object.addProperty("scale_x", new java.math.BigDecimal(Float.toString(next.scaleX)));
        if (origin.scaleY != next.scaleY) object.addProperty("scale_y", new java.math.BigDecimal(Float.toString(next.scaleY)));
    }
    public JsonObject part(Part part) { return part(data(), part, objectId()); }
    private static JsonObject part(JsonObject data, Part part, String objectId) {
        if (data == null) return null;
        return switch (part) {
            case BACKGROUND -> object(data.get("background"));
            case FILTER -> object(data.get("filter"));
            case BOX -> object(data.get("dialogue_box"));
            case OBJECT -> { var map = object(data.get("visual_objects")); yield map == null ? null : object(map.get(objectId)); }
        };
    }
    private String firstImage() {
        return project.resources().catalog().keys().stream().filter(key -> key.kind() == ResourceKind.IMAGE)
                .sorted(Comparator.comparing(ResourceKey::path)).map(key -> MaterialPack.imageId(key, project.draft().namespace()))
                .findFirst().orElse("");
    }
    private JsonObject inlineSource() {
        var data = new JsonObject(); var values = new JsonObject(); values.addProperty("default", firstImage());
        data.add("variants", values); data.addProperty("initial_variant", "default"); return data;
    }
    public void setBackground(boolean enabled) {
        assetRequests.put(Part.BACKGROUND, ++assetRequest);
        edit(null, data -> { if (enabled) { if (!data.has("background")) {
                var background = new JsonObject(); background.addProperty("asset", "");
                background.addProperty("initial_variant", "default"); data.add("background", background);
            } }
            else data.remove("background"); });
    }
    public void addObject(boolean copy) {
        if (!active()) return;
        String current = objectId();
        edit(null, data -> {
            JsonObject map = object(data.get("visual_objects"));
            if (map == null) { if (data.has("visual_objects")) return; map = new JsonObject(); data.add("visual_objects", map); }
            if (copy && !map.has(current)) return;
            String id = unused(map, copy ? current + "_copy" : "object");
            map.add(id, copy ? map.get(current).deepCopy() : inlineSource()); objects.put(snapshot().key(), id);
        });
    }
    public void deleteObject() {
        String selected = objectId();
        edit(null, data -> { var map = object(data.get("visual_objects")); if (map != null) map.remove(selected); });
    }
    public String renameObject(String name) {
        String selected = objectId(); var map = objectMap();
        if (!active() || map == null || !map.has(selected) || name.equals(selected)) return "";
        if (!validName(name) || Set.of("background", "dialogue").contains(name)) return "scene.invalid_id";
        if (map.has(name)) return "scene.duplicate_id";
        edit(null, data -> {
            data.add("visual_objects", renamed(object(data.get("visual_objects")), selected, name));
            objects.put(snapshot().key(), name);
        });
        return "";
    }
    public void setSource(String mode) {
        if (!Set.of("asset", "inline").contains(mode)) return;
        editPart(Part.OBJECT, null, data -> {
            if (mode.equals("asset")) { data.remove("variants"); data.addProperty("asset", ""); }
            else { data.remove("asset"); var source = inlineSource(); data.add("variants", source.get("variants"));
                data.add("initial_variant", source.get("initial_variant")); }
        });
    }
    public void setAsset(String id) {
        setAsset(Part.OBJECT, id);
    }
    public void setAsset(Part part, String id) {
        if (part != Part.OBJECT && part != Part.BACKGROUND) return;
        if (!active()) return;
        long request = ++assetRequest;
        assetRequests.put(part, request);
        long projectGeneration = project.projectGeneration();
        id = id.strip();
        String requested = id;
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location != null && location.getNamespace().equals(project.draft().namespace())) {
            var asset = new ResourceKey(ResourceKind.VISUAL_ASSET, location.getPath());
            var owner = snapshot().key(); String selected = objectId();
            if (project.draft().revision(asset) != null && !project.resources().whenLoaded(asset, () -> {
                if (Objects.equals(assetRequests.get(part), request) && projectGeneration == project.projectGeneration()
                        && active() && owner.equals(snapshot().key()) && (part == Part.BACKGROUND || selected.equals(objectId())))
                    setAsset(part, requested);
            })) return;
        }
        editPart(part, "asset", data -> {
            data.remove("variants"); if (part == Part.BACKGROUND) data.remove("sampling");
            data.addProperty("asset", requested);
            JsonObject values = assetVariants(requested);
            if (values != null && !values.isEmpty() && !values.has(text(data, "initial_variant", "")))
                data.addProperty("initial_variant", values.keySet().iterator().next());
        });
    }
    private JsonObject assetVariants(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !location.getNamespace().equals(project.draft().namespace())) return null;
        var key = new ResourceKey(ResourceKind.VISUAL_ASSET, location.getPath());
        if (!project.draft().isLoaded(key)) return null;
        JsonObject asset = object(project.draft().resource(key)); return asset == null ? null : object(asset.get("variants"));
    }
    public JsonObject variantMap(Part part) {
        JsonObject data = part(part);
        if (data == null) return null;
        return data.has("asset") ? assetVariants(text(data, "asset", "")) : object(data.get("variants"));
    }
    private String variantKey(Part part) { return snapshot().key() + "/" + part + "/" + (part == Part.OBJECT ? objectId() : ""); }
    public String variant(Part part) {
        JsonObject map = variantMap(part);
        if (map == null || map.isEmpty()) return "";
        String selected = variants.get(variantKey(part));
        return selected != null && map.has(selected) ? selected : map.keySet().iterator().next();
    }
    public void selectVariant(Part part, String name) {
        if (!active() || variantMap(part) == null || !variantMap(part).has(name)) return;
        project.endEdit(); variants.put(variantKey(part), name); changed.run();
    }
    public void addVariant(Part part) {
        editPart(part, null, data -> {
            var map = object(data.get("variants")); if (map == null || data.has("asset")) return;
            String name = unused(map, "variant"); map.addProperty(name, firstImage()); variants.put(variantKey(part), name);
            if (map.size() == 1) data.addProperty("initial_variant", name);
        });
    }
    public String renameVariant(Part part, String name) {
        var map = variantMap(part); String old = variant(part);
        if (!active() || map == null || !map.has(old) || name.equals(old)) return "";
        if (!validName(name)) return "scene.invalid_id";
        if (map.has(name)) return "scene.duplicate_id";
        editPart(part, null, data -> {
            if (data.has("asset")) return;
            data.add("variants", renamed(object(data.get("variants")), old, name));
            if (text(data, "initial_variant", part == Part.BACKGROUND ? "default" : "").equals(old))
                data.addProperty("initial_variant", name);
            variants.put(variantKey(part), name);
        }); return "";
    }
    public void deleteVariant(Part part) {
        String old = variant(part);
        editPart(part, null, data -> {
            var map = object(data.get("variants")); if (map == null || data.has("asset")) return;
            map.remove(old);
            if (text(data, "initial_variant", part == Part.BACKGROUND ? "default" : "").equals(old))
                data.addProperty("initial_variant", map.isEmpty() ? "" : map.keySet().iterator().next());
        });
    }
    public void setVariantImage(Part part, String image) {
        String selected = variant(part);
        editPart(part, "variants/" + selected, data -> {
            var map = object(data.get("variants")); if (map != null && !data.has("asset") && map.has(selected)) map.addProperty(selected, image);
        });
    }
    public void setFilter(String type) {
        if (!Set.of("none", "color_adjust", "crt").contains(type)) return;
        edit(null, data -> {
            if (type.equals("none")) data.remove("filter");
            else { JsonObject filter = object(data.get("filter"));
                if (filter == null) filter = new JsonObject();
                filter.addProperty("type", type); data.add("filter", filter); }
        });
    }
    public void setText(Part part, String field, String value) {
        editPart(part, field, data -> {
            if (value.isEmpty() && Set.of("sampling", "tint").contains(field)) data.remove(field);
            else if (field.equals("visible")) data.addProperty(field, Boolean.parseBoolean(value));
            else data.addProperty(field, value);
        });
    }
    public String setNumber(Part part, NumberField field, String text) {
        if (text.isBlank()) { editPart(part, field.name(), data -> data.remove(field.name())); return ""; }
        Number number;
        try {
            if (field.integer()) number = Integer.valueOf(text);
            else { float value = Float.parseFloat(text);
                if (!Float.isFinite(value) || value < field.minimum() || value > field.maximum()) return "scene.invalid_number";
                // Preserve the entered decimal in the draft; runtime conversion still uses float.
                number = new java.math.BigDecimal(text.strip()); }
        } catch (NumberFormatException failure) { return "scene.invalid_number"; }
        editPart(part, field.name(), data -> data.addProperty(field.name(), number)); return "";
    }
    public JsonObject box() { return data() == null ? null : object(data().get("dialogue_box")); }
    public String setBoxNumber(SceneWorkspace.NumberField field, String text) {
        if (!active()) return "";
        java.math.BigDecimal number = null;
        if (!text.isBlank()) {
            try {
                float value = Float.parseFloat(text);
                if (!Float.isFinite(value) || value < field.minimum() || value > field.maximum()) return "scene.invalid_number";
                number = new java.math.BigDecimal(text.strip());
            } catch (NumberFormatException failure) { return "scene.invalid_number"; }
        }
        var value = number;
        edit("box/" + field.name(), next -> editBox(next, box -> {
            if (value == null) box.remove(field.name()); else box.addProperty(field.name(), value);
        }));
        return "";
    }
    public void setBoxAnchor(String value) {
        if (Arrays.stream(top.rookiestwo.maimai_dialogue.presentation.visual.VisualAnchor.values())
                .noneMatch(anchor -> anchor.serializedName().equals(value))) return;
        edit("box/anchor", next -> editBox(next, box -> box.addProperty("anchor", value)));
    }
    public void resetBox() { project.endEdit(); edit(null, next -> next.remove("dialogue_box")); project.endEdit(); }
    private static void editBox(JsonObject next, Consumer<JsonObject> operation) {
        JsonObject box = SceneWorkspace.object(next.get("dialogue_box"));
        if (box == null) box = new JsonObject();
        operation.accept(box);
        if (box.isEmpty()) next.remove("dialogue_box"); else next.add("dialogue_box", box);
    }
    public void setTheme(String value) {
        edit("theme", next -> { if (value.isBlank()) next.remove("theme"); else next.addProperty("theme", value.strip()); });
    }

    private void editPart(Part part, String group, Consumer<JsonObject> mutation) {
        String id = objectId();
        edit(group == null ? null : part + "/" + id + "/" + group, data -> {
            var target = part(data, part, id); if (target != null) mutation.accept(target);
        });
    }
    private void edit(String group, Consumer<JsonObject> mutation) {
        if (!active()) return;
        drag = null;
        var state = snapshot(); JsonObject next = state.data().deepCopy(); mutation.accept(next);
        if (!next.equals(state.data())) {
            project.editAsset(state.key(), next, group);
        }
    }
    private static boolean validName(String name) { return name.matches("[a-z0-9_-]+"); }
    private static String unused(JsonObject map, String seed) {
        String name = seed; for (int i = 2; map.has(name); i++) name = seed + "_" + i; return name;
    }
    private static JsonObject renamed(JsonObject map, String old, String name) {
        var result = new JsonObject(); map.entrySet().forEach(e -> result.add(e.getKey().equals(old) ? name : e.getKey(), e.getValue())); return result;
    }
}
