package top.rookiestwo.maimai_dialogue_editor.document;

import com.google.gson.*;
import top.rookiestwo.maimai_dialogue_editor.project.*;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.util.*;
import java.util.function.Consumer;
import static top.rookiestwo.maimai_dialogue_editor.document.DialogueDraft.*;

/** Action resources and per-step calls share one field editor, but never share mutable draft objects. */
public final class ActionWorkspace {
    public record Context(ResourceKey resource, int step) { public boolean standalone() { return resource.kind() == ResourceKind.ACTION; } }
    private record Selection(Context context, int call) {}
    private final ProjectWorkspace project;
    private final Runnable changed;
    private final Map<Context, Integer> selections = new HashMap<>();
    private final Map<ProjectDraft, Selection> history = new WeakHashMap<>();
    public record PreviewContext(String scene, String target) {}
    private final Map<ResourceKey, PreviewContext> previewContexts = new HashMap<>();
    private ProjectDraft seen;
    private Context seenContext;
    private long seenNavigation = -1;
    private long projectGeneration = -1;
    private Gesture gesture;
    public ActionWorkspace(ProjectWorkspace project, Runnable changed) { this.project = project; this.changed = changed; }
    public Context context() {
        if (projectGeneration != project.projectGeneration()) {
            projectGeneration = project.projectGeneration(); selections.clear(); history.clear(); previewContexts.clear(); gesture = null;
            seen = null; seenContext = null; seenNavigation = -1;
        }
        var selected = project.resources().selection(); var key = selected.owner();
        if (key == null || !key.equals(project.resources().opened()) || project.content().snapshot().data() == null) return null;
        if (key.kind() == ResourceKind.ACTION && selected.type() == ResourceTree.Type.RESOURCE) return new Context(key, -2);
        return selected.isStep() ? new Context(key, selected.stepIndex()) : null;
    }
    public boolean active() { return project.content().active() && context() != null; }
    /** Editor-only context, retained across View rebuilds and never serialized into the action. */
    public PreviewContext previewContext() {
        var context = context();
        return context == null ? new PreviewContext("", "dialogue") : previewContexts.getOrDefault(context.resource(), new PreviewContext("", "dialogue"));
    }
    public void previewScene(String scene) {
        if (!active() || !context().standalone()) return;
        previewContexts.put(context().resource(), new PreviewContext(scene, "dialogue")); changed.run();
    }
    public void previewTarget(String target) {
        if (!active() || !context().standalone()) return;
        previewContexts.put(context().resource(), new PreviewContext(previewContext().scene(), target)); changed.run();
    }
    public JsonObject data() { return project.content().snapshot().data(); }
    public JsonObject node() { var context = context(); return context == null ? null : context.standalone() ? data() : DialogueDraft.node(data(), context.step()); }
    public JsonArray calls() { return context() == null || context().standalone() ? null : array(node(), "actions"); }
    public int selected() {
        var context = context(); var calls = calls();
        long navigation = project.resources().selectionRevision();
        boolean navigated = navigation != seenNavigation || !Objects.equals(seenContext, context);
        int index = navigated ? -1 : selections.getOrDefault(context, -1);
        if (!navigated && seen != project.draft()) {
            var previous = history.get(project.draft()); if (previous != null && previous.context().equals(context)) index = previous.call();
        }
        index = calls == null || calls.isEmpty() ? -1 : Math.clamp(index, -1, calls.size() - 1);
        if (context != null) selections.put(context, index);
        seen = project.draft(); seenContext = context; seenNavigation = navigation;
        return index;
    }
    public boolean inspecting() {
        int selected = selected(); var context = context();
        return context != null && (context.standalone() || selected >= 0);
    }
    public JsonObject call() { var calls = calls(); int index = selected(); return calls != null && index >= 0 ? object(calls.get(index)) : null; }
    public JsonObject definition() {
        var context = context(); if (context == null) return null;
        if (context.standalone()) return data();
        var spec = object(get(call(), "action")); return "inline".equals(string(spec, "type")) ? object(get(spec, "action")) : null;
    }
    public String mode() { return context() != null && context().standalone() ? "standalone" : string(object(get(call(), "action")), "type"); }
    public String text(boolean call, String path, String fallback) {
        if (gesture != null && gesture.valid() && gesture.call == call && gesture.field.path().equals(path)) return gesture.raw;
        return ActionFields.text(call ? call() : definition(), path, fallback);
    }
    public boolean audioOnly() {
        var definition = definition();
        return definition != null && (definition.has("sound") || definition.has("bgm"))
                && List.of("x", "y", "scale", "opacity", "variant", "visible").stream().noneMatch(definition::has);
    }
    public void select(int index) {
        if (!active() || calls() == null || index < -1 || index >= calls().size() || index == selected()) return;
        endGesture(true); project.endEdit(); selections.put(context(), index); seen = project.draft(); seenContext = context();
        history.put(project.draft(), new Selection(context(), index)); changed.run();
    }
    public boolean canAdd() { return active() && !context().standalone() && node() != null && calls() != null; }
    public void add(String preset) {
        if (!canAdd()) return;
        var before = context(); int index = calls().size();
        editRoot(null, root -> {
            var node = DialogueDraft.node(root, before.step()); var calls = array(node, "actions");
            var call = new JsonObject(); var spec = typed("inline"); var definition = ActionFields.preset(preset);
            spec.add("action", definition); call.add("action", spec);
            if (preset.equals("reference")) { spec.addProperty("type", "reference"); spec.remove("action"); spec.addProperty("id", ""); }
            calls.add(call); node.add("actions", calls);
        }, index);
    }
    public void copy() {
        if (!canAdd() || selected() < 0) return;
        int index = selected(); var context = context();
        editRoot(null, root -> { var calls = array(DialogueDraft.node(root, context.step()), "actions"); insert(calls, index + 1, calls.get(index).deepCopy()); }, index + 1);
    }
    public void delete() {
        if (!canAdd() || selected() < 0) return;
        int index = selected(); var context = context();
        editRoot(null, root -> {
            var node = DialogueDraft.node(root, context.step()); var calls = array(node, "actions"); calls.remove(index);
            if (calls.isEmpty()) node.remove("actions");
        }, Math.max(0, index - 1));
    }
    public void move(int delta) {
        if (!canAdd()) return; int from = selected(), to = from + delta;
        if (Math.abs(delta) != 1 || from < 0 || to < 0 || to >= calls().size()) return;
        var context = context(); editRoot(null, root -> DialogueDraft.move(array(DialogueDraft.node(root, context.step()), "actions"), from, to), to);
    }
    public void mode(String mode, JsonObject referenced) {
        if (!active() || call() == null || !List.of("inline", "reference").contains(mode) || mode.equals(mode())) return;
        edit(true, null, call -> {
            var spec = object(call.get("action")); if (spec == null) spec = new JsonObject();
            spec.addProperty("type", mode);
            if (mode.equals("inline")) { spec.remove("id"); spec.add("action", referenced == null ? new JsonObject() : referenced.deepCopy()); }
            else { spec.remove("action"); spec.addProperty("id", ""); }
            call.add("action", spec);
        });
    }
    public void extract() {
        if (!active() || context().standalone() || definition() == null) return;
        endGesture(true); project.endEdit();
        var context = context();
        project.resources().beginExtract(InlineResource.action(project.draft(), context.resource(), context.step(), selected()),
                context.resource().path() + "_action");
    }
    public void set(boolean call, String path, JsonElement value) {
        edit(call, path, object -> ActionFields.set(object, path, value == null ? null : value.deepCopy()));
    }
    public String number(boolean call, ActionFields.Number field, String raw) {
        JsonPrimitive value;
        try { value = field.parse(raw); } catch (RuntimeException invalid) { return "action.invalid_number"; }
        set(call, field.path(), value); return "";
    }
    public void enabled(String field, boolean enabled) {
        if (definition() == null || !ActionFields.COMPONENTS.contains(field)) return;
        if (!enabled) { set(false, field, null); return; }
        if (definition().has(field)) return;
        JsonElement value;
        if (ActionFields.TRACKS.contains(field)) {
            var frames = new JsonArray(); var end = new JsonObject(); end.addProperty("at", 1); end.addProperty("value", 0); frames.add(end); value = frames;
        } else {
            var object = new JsonObject();
            switch (field) {
                case "variant" -> object.addProperty("value", "");
                case "visible" -> object.addProperty("value", true);
                case "sound" -> object.addProperty("sound", "");
                case "bgm" -> { object.addProperty("type", "play"); object.addProperty("sound", ""); }
                default -> { return; }
            }
            value = object;
        }
        set(false, field, value);
    }
    public void bgmMode(String mode) {
        if (!List.of("play", "stop").contains(mode)) return;
        edit(false, null, action -> {
            var bgm = object(action.get("bgm")); if (bgm == null) return;
            bgm.addProperty("type", mode);
            if (mode.equals("stop")) { bgm.remove("sound"); bgm.remove("volume"); bgm.remove("loop"); }
            else if (!bgm.has("sound")) bgm.addProperty("sound", "");
        });
    }
    public String addFrameAt(String track, int timeMs, int delayMs, JsonObject referenced) {
        if (!active() || editing()) return "unavailable";
        boolean reference = mode().equals("reference");
        JsonObject definition = reference ? referenced : definition();
        if (definition == null) return "unavailable";
        if (!ActionFields.errors(definition).isEmpty()) return "invalid";
        ActionKeyframes.Insertion insertion;
        try {
            var action = top.rookiestwo.maimai_dialogue.presentation.action.SceneAction.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE, definition).getOrThrow();
            insertion = ActionKeyframes.plan(action, track, timeMs, delayMs);
        } catch (RuntimeException invalid) { return "invalid"; }
        if (!insertion.valid()) return insertion.error();
        if (reference) {
            var copy = definition.deepCopy(); ActionKeyframes.insert(copy, track, insertion);
            edit(true, null, call -> {
                var spec = call.getAsJsonObject("action"); spec.remove("id"); spec.addProperty("type", "inline"); spec.add("action", copy);
            });
        } else edit(false, null, action -> ActionKeyframes.insert(action, track, insertion));
        return "";
    }
    public void applyPreset(String id) {
        edit(false, null, action -> {
            for (String key : List.of("duration_ms", "easing", "blocking", "x", "y", "scale", "opacity", "variant", "visible", "sound", "bgm")) action.remove(key);
            ActionFields.preset(id).entrySet().forEach(entry -> action.add(entry.getKey(), entry.getValue()));
        });
    }
    public void deleteFrame(String track, int index) {
        edit(false, null, action -> {
            var frames = array(action, track);
            if (frames != null && index >= 0 && index < frames.size()) { frames.remove(index); if (frames.isEmpty()) action.remove(track); }
        });
    }
    private void edit(boolean call, String field, Consumer<JsonObject> mutation) {
        if (!active() || (call ? call() : definition()) == null) return;
        var context = context(); int index = selected();
        editRoot(field == null ? null : (call ? "call/" : "definition/") + field, root -> {
            var target = root;
            if (!context.standalone()) {
                target = object(array(DialogueDraft.node(root, context.step()), "actions").get(index));
                if (!call) target = object(get(object(get(target, "action")), "action"));
            }
            if (target != null) mutation.accept(target);
        }, index);
    }
    private void editRoot(String group, Consumer<JsonObject> mutation, int nextIndex) {
        if (!active()) return;
        endGesture(false); var context = context(); int previous = selected(); var before = project.draft();
        var copy = data().deepCopy(); mutation.accept(copy);
        if (copy.equals(data())) return;
        if (group == null) project.endEdit();
        history.put(before, new Selection(context, previous)); selections.put(context, nextIndex);
        // Publish the selection before notifying Views, without navigating away from the Step.
        seen = null;
        project.editAsset(context.resource(), copy, group == null ? null : "action/" + context.step() + "/" + previous + "/" + group);
        selections.put(context, nextIndex); history.put(project.draft(), new Selection(context, nextIndex));
        seen = project.draft(); seenContext = context;
        if (group == null) project.endEdit();
    }
    public boolean editing() { return gesture != null && gesture.valid(); }
    public EditGesture beginGesture(boolean call, ActionFields.Number field) {
        endGesture(false); project.endEdit();
        return active() && (call ? call() : definition()) != null ? gesture = new Gesture(call, field) : null;
    }
    public void endGesture(boolean commit) { if (gesture != null) gesture.finish(commit); }
    private final class Gesture implements EditGesture {
        final ProjectDraft before = project.draft(); final long generation = project.projectGeneration();
        final Context context = context(); final int index = selected(); final boolean call; final ActionFields.Number field;
        String raw; boolean updated;
        Gesture(boolean call, ActionFields.Number field) { this.call = call; this.field = field; raw = text(call, field.path(), Float.toString(field.fallback())); }
        boolean valid() { return gesture == this && generation == project.projectGeneration() && before == project.draft()
                && active() && Objects.equals(context, context()) && index == selected(); }
        public boolean update(String raw) { if (!valid()) return false;
            try { field.parse(raw); } catch (RuntimeException invalid) { return false; } this.raw = raw; updated = true; return true; }
        public void finish(boolean commit) { if (gesture != this) return; boolean valid = valid(); gesture = null;
            if (commit && valid && updated) { number(call, field, raw); project.endEdit(); } }
    }
}
