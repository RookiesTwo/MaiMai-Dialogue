package top.rookiestwo.maimai_dialogue_editor.document;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectResource;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceTree;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static top.rookiestwo.maimai_dialogue_editor.document.DialogueDraft.*;

/** Basic document editing. Cursor/history bookkeeping never enters the resource JSON. */
public final class ContentWorkspace {
    public record Cursor(int step, int option) {}
    private record Navigation(ResourceKey resource, Cursor cursor) {}
    public record Snapshot(ResourceKey key, JsonObject data, Cursor cursor) {}
    private final Supplier<ProjectDraft> current;
    private final ResourceWorkspace resources;
    private final BiConsumer<ProjectDraft, String> edit;
    private final Runnable endEdit;
    private final Runnable changed;
    private final Map<ResourceKey, Cursor> cursors = new HashMap<>();
    // History owns snapshots; these weak keys do not extend their lifetime or persist navigation to disk.
    private final Map<ProjectDraft, Navigation> navigation = new WeakHashMap<>();
    private ProjectDraft seen;
    private ResourceKey seenResource;
    private ResourceKey dataKey;
    private ProjectResource dataRevision;
    private JsonObject dataCache;

    public ContentWorkspace(Supplier<ProjectDraft> current, ResourceWorkspace resources,
                            BiConsumer<ProjectDraft, String> edit, Runnable endEdit, Runnable changed) {
        this.current = current;
        this.resources = resources;
        this.edit = edit;
        this.endEdit = endEdit;
        this.changed = changed;
    }

    public boolean active() { return resources.active() && resources.form() == ResourceWorkspace.Form.NONE; }
    public Snapshot snapshot() {
        ResourceKey key = resources.opened();
        ProjectDraft draft = current.get();
        if (key == null || draft == null) {
            dataKey = null;
            dataRevision = null;
            dataCache = null;
            return new Snapshot(null, null, new Cursor(END, -1));
        }
        ProjectResource revision = draft.revision(key);
        if (!key.equals(dataKey) || revision != dataRevision) {
            dataCache = object(draft.resource(key));
            dataKey = key;
            dataRevision = revision;
        }
        JsonObject data = dataCache;
        Cursor cursor = cursors.getOrDefault(key, new Cursor(END, 0));
        if (draft != seen && key.equals(seenResource)) {
            Navigation saved = navigation.get(draft);
            if (saved != null && saved.resource().equals(key)) cursor = saved.cursor();
        }
        JsonArray steps = array(data, "steps");
        int step = cursor.step() >= 0 && steps != null && cursor.step() < steps.size() ? cursor.step() : END;
        JsonArray options = options(data);
        int option = options == null || options.isEmpty() ? -1 : Math.clamp(cursor.option(), 0, options.size() - 1);
        cursor = new Cursor(step, option);
        cursors.put(key, cursor);
        resources.reconcileStep(key, step);
        seen = draft;
        seenResource = key;
        return new Snapshot(key, data, cursor);
    }

    /** Called on explicit browser navigation, before rebinding Views. Does not edit the document. */
    public void acceptBrowserSelection() {
        ResourceTree.Node selected = resources.selection();
        if (!selected.isStep() || !selected.owner().equals(resources.opened())) return;
        Cursor previous = cursors.getOrDefault(selected.owner(), new Cursor(END, 0));
        Cursor cursor = new Cursor(selected.stepIndex(), previous.option());
        cursors.put(selected.owner(), cursor);
        seen = current.get();
        seenResource = selected.owner();
        navigation.put(seen, new Navigation(selected.owner(), cursor));
    }

    public boolean canAddStep() {
        Snapshot state = snapshot();
        return editable(state, ResourceKind.DIALOGUE) && array(state.data(), "steps") != null;
    }
    public boolean canModifyStep() { return canAddStep() && snapshot().cursor().step() != END; }
    public boolean canMoveStep(int delta) {
        if (!canModifyStep()) return false;
        Snapshot state = snapshot();
        int to = state.cursor().step() + delta;
        return Math.abs(delta) == 1 && to >= 0 && to < array(state.data(), "steps").size();
    }

    public void reset() {
        cursors.clear();
        navigation.clear();
        seen = null;
        seenResource = null;
        dataKey = null;
        dataRevision = null;
        dataCache = null;
    }

    public void selectStep(int index) {
        Snapshot state = snapshot();
        if (!editable(state, ResourceKind.DIALOGUE)) return;
        JsonArray steps = array(state.data(), "steps");
        if (index != END && (steps == null || index < 0 || index >= steps.size())) return;
        select(state, new Cursor(index, state.cursor().option()));
    }

    public void selectOption(int index) {
        Snapshot state = snapshot();
        if (!editable(state, ResourceKind.DIALOGUE)) return;
        JsonArray options = options(state.data());
        if (options == null || index < 0 || index >= options.size()) return;
        select(state, new Cursor(END, index));
    }

    private void select(Snapshot state, Cursor cursor) {
        endEdit.run();
        cursors.put(state.key(), cursor);
        navigation.put(current.get(), new Navigation(state.key(), cursor));
        notifyChange(state.key(), true);
    }

    public void editScene(String value) {
        Snapshot state = snapshot();
        if (!editable(state, ResourceKind.DIALOGUE)) return;
        state.data().addProperty("scene", value.strip());
        write(state, "scene", state.cursor());
    }

    public void editSpeakerName(String value) {
        Snapshot state = snapshot();
        if (!editable(state, ResourceKind.SPEAKER)) return;
        state.data().addProperty("name", value);
        write(state, "name", state.cursor());
    }

    public void addStep() { insertStep(false); }
    public void copyStep() { insertStep(true); }
    private void insertStep(boolean copy) {
        Snapshot state = snapshot();
        if (!editable(state, ResourceKind.DIALOGUE)) return;
        JsonArray steps = array(state.data(), "steps");
        int selected = state.cursor().step();
        if (steps == null || (copy && selected == END)) return;
        int at = selected == END ? steps.size() : selected + 1;
        insert(steps, at, copy ? steps.get(selected).deepCopy() : newStep());
        state.data().add("steps", steps);
        write(state, null, new Cursor(at, -1));
    }
    public void deleteStep() {
        Snapshot state = snapshot();
        if (!editable(state, ResourceKind.DIALOGUE) || state.cursor().step() == END) return;
        JsonArray steps = array(state.data(), "steps");
        if (steps == null) return;
        int index = state.cursor().step();
        steps.remove(index);
        write(state, null, new Cursor(index < steps.size() ? index : END, -1));
    }
    public void moveStep(int delta) {
        if (Math.abs(delta) != 1) return;
        Snapshot state = snapshot();
        if (!editable(state, ResourceKind.DIALOGUE)) return;
        JsonArray steps = array(state.data(), "steps");
        int from = state.cursor().step(), to = from + delta;
        if (steps == null || from < 0 || to < 0 || to >= steps.size()) return;
        move(steps, from, to);
        write(state, null, new Cursor(to, -1));
    }
    public void createEnd() {
        Snapshot state = snapshot();
        if (!editable(state, ResourceKind.DIALOGUE) || state.data().has("end")) return;
        state.data().add("end", newEnd());
        write(state, null, new Cursor(END, -1));
    }

    public void setTextMode(String mode) {
        editNode(null, node -> {
            JsonElement text = node.get("text");
            if (text != null && !isString(text)) return; // Preserve random/unsupported text until its editor is available.
            if (mode.equals("absent")) node.remove("text");
            else if (mode.equals("plain") && !node.has("text")) node.addProperty("text", "");
        });
    }
    public void editText(String text) {
        editNode("text", node -> { if (isString(node.get("text"))) node.addProperty("text", text); });
    }
    public void setSpeakerMode(String mode) {
        if (!List.of("inherit", "set", "hide").contains(mode)) return;
        editNode(null, node -> {
            if (mode.equals("inherit")) { node.remove("speaker"); return; }
            JsonObject operation = object(node.get("speaker"));
            if (operation == null) operation = new JsonObject();
            operation.addProperty("type", mode);
            if (mode.equals("hide")) operation.remove("id");
            else if (!operation.has("id")) operation.addProperty("id", "");
            node.add("speaker", operation);
        });
    }
    public void editSpeakerId(String id) {
        editNode("speaker.id", node -> {
            JsonObject operation = object(node.get("speaker"));
            if ("set".equals(string(operation, "type"))) operation.addProperty("id", id);
        });
    }
    private void editNode(String group, Consumer<JsonObject> mutation) {
        Snapshot state = snapshot();
        if (!editable(state, ResourceKind.DIALOGUE)) return;
        JsonObject node = node(state.data(), state.cursor().step());
        if (node == null) return;
        mutation.accept(node);
        write(state, group, state.cursor());
    }

    public void setExitType(String type) {
        if (!List.of("return", "dialogue", "options").contains(type)) return;
        editNode(null, node -> {
            if (snapshot().cursor().step() != END) return;
            JsonObject exit = object(node.get("exit"));
            if (exit == null) exit = new JsonObject();
            exit.addProperty("type", type);
            if (!type.equals("dialogue")) exit.remove("dialogue");
            else if (!exit.has("dialogue")) exit.addProperty("dialogue", "");
            if (!type.equals("options")) exit.remove("options");
            else if (!exit.has("options")) exit.add("options", new JsonArray());
            node.add("exit", exit);
        });
    }
    public void editExitDialogue(String id) {
        editNode("exit.dialogue", node -> {
            JsonObject exit = object(node.get("exit"));
            if (snapshot().cursor().step() == END && "dialogue".equals(string(exit, "type"))) exit.addProperty("dialogue", id);
        });
    }
    public void addOption() { insertOption(false); }
    public void copyOption() { insertOption(true); }
    private void insertOption(boolean copy) {
        Snapshot state = snapshot();
        if (!optionsEditable(state)) return;
        JsonArray options = options(state.data());
        if (options == null || (copy && state.cursor().option() < 0)) return;
        int at = state.cursor().option() < 0 ? options.size() : state.cursor().option() + 1;
        insert(options, at, copy ? options.get(state.cursor().option()).deepCopy() : newOption());
        exit(state.data()).add("options", options);
        write(state, null, new Cursor(END, at));
    }
    public void deleteOption() {
        Snapshot state = snapshot();
        if (!optionsEditable(state) || state.cursor().option() < 0) return;
        JsonArray options = options(state.data());
        if (options == null) return;
        options.remove(state.cursor().option());
        write(state, null, new Cursor(END, Math.min(state.cursor().option(), options.size() - 1)));
    }
    public void moveOption(int delta) {
        if (Math.abs(delta) != 1) return;
        Snapshot state = snapshot();
        if (!optionsEditable(state)) return;
        JsonArray options = options(state.data());
        int from = state.cursor().option(), to = from + delta;
        if (options == null || from < 0 || to < 0 || to >= options.size()) return;
        move(options, from, to);
        write(state, null, new Cursor(END, to));
    }
    public void editOptionText(String text) { editOption("option.text", option -> option.addProperty("text", text)); }
    public void setOptionIcon(String icon) {
        if (!List.of("none", "question", "exclamation", "dialogue").contains(icon)) return;
        editOption(null, option -> {
            if (icon.equals("none")) option.remove("icon");
            else option.addProperty("icon", icon);
        });
    }
    public void setOptionTarget(String type) {
        if (!List.of("return", "close", "dialogue").contains(type)) return;
        editOption(null, option -> {
            JsonObject target = object(option.get("target"));
            if (target == null) target = new JsonObject();
            target.addProperty("type", type);
            if (!type.equals("dialogue")) target.remove("dialogue");
            else if (!target.has("dialogue")) target.addProperty("dialogue", "");
            option.add("target", target);
        });
    }
    public void editOptionDialogue(String id) {
        editOption("option.target.dialogue", option -> {
            JsonObject target = object(option.get("target"));
            if ("dialogue".equals(string(target, "type"))) target.addProperty("dialogue", id);
        });
    }
    private void editOption(String group, Consumer<JsonObject> mutation) {
        Snapshot state = snapshot();
        if (!optionsEditable(state)) return;
        JsonObject option = option(state.data(), state.cursor().option());
        if (option == null) return;
        mutation.accept(option);
        write(state, group, state.cursor());
    }

    private boolean editable(Snapshot state, ResourceKind kind) {
        return active() && state.key() != null && state.key().kind() == kind && state.data() != null;
    }
    private boolean optionsEditable(Snapshot state) {
        return editable(state, ResourceKind.DIALOGUE) && state.cursor().step() == END
                && "options".equals(string(exit(state.data()), "type"));
    }
    private void write(Snapshot state, String group, Cursor cursor) {
        ProjectDraft before = current.get();
        ProjectDraft after = before.withResource(state.key(), state.data());
        if (before.equals(after)) return;
        dataKey = state.key();
        dataRevision = after.revision(state.key());
        navigation.put(before, new Navigation(state.key(), state.cursor()));
        edit.accept(after, group == null ? null : "content/" + state.key() + "/" + state.cursor() + "/" + group);
        cursors.put(state.key(), cursor);
        navigation.put(after, new Navigation(state.key(), cursor));
        seen = after;
        seenResource = state.key();
        notifyChange(state.key(), false);
    }
    private void notifyChange(ResourceKey key, boolean reveal) {
        if (key.kind() == ResourceKind.DIALOGUE) resources.focusStep(key, cursors.get(key).step(), reveal);
        else if (!key.equals(resources.selection().resource())) {
            resources.open(key);
            return;
        }
        changed.run();
    }
}
