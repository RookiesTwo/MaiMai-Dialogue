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
    public record Cursor(int step, int option, int variant) {
        public Cursor(int step, int option) { this(step, option, 0); }
    }
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
        var text = get(node(data, step), "text");
        int variants = text != null && text.isJsonArray() ? text.getAsJsonArray().size() : 0;
        cursor = new Cursor(step, option, variants == 0 ? 0 : Math.clamp(cursor.variant(), 0, variants - 1));
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
        write(state, "scene", state.cursor(), !resources.selection().isStep());
    }

    public void editSpeakerName(String value) {
        editTextField(ContentTextField.NAME, value);
    }

    public void editTextField(ContentTextField field, String value) {
        Snapshot state = snapshot();
        if (!active() || state.key() == null || state.data() == null) return;
        if (field.apply(state.key().kind(), state.data(), state.cursor(), value))
            write(state, field.group(), state.cursor(), field == ContentTextField.REQUIRES || field == ContentTextField.SKIP_SUMMARY);
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
        if (!List.of("absent", "plain", "random").contains(mode)) return;
        editNode(null, node -> {
            JsonElement text = node.get("text");
            if (text != null && !isString(text) && !text.isJsonArray()) return;
            if (mode.equals("absent")) node.remove("text");
            else if (mode.equals("plain") && !isString(text)) {
                var values = text != null && text.isJsonArray() ? text.getAsJsonArray() : new JsonArray();
                node.addProperty("text", values.isEmpty() ? "" : string(values.get(Math.min(snapshot().cursor().variant(), values.size() - 1))));
            } else if (mode.equals("random") && (text == null || !text.isJsonArray())) {
                var values = new JsonArray(); values.add(string(text)); node.add("text", values);
            }
        });
    }
    public void selectVariant(int index) {
        var state = snapshot(); var values = get(node(state.data(), state.cursor().step()), "text");
        if (!editable(state, ResourceKind.DIALOGUE) || values == null || !values.isJsonArray()
                || index < 0 || index >= values.getAsJsonArray().size()) return;
        select(state, new Cursor(state.cursor().step(), state.cursor().option(), index));
    }
    public void changeVariants(String operation) {
        var state = snapshot(); var values = get(node(state.data(), state.cursor().step()), "text");
        if (!editable(state, ResourceKind.DIALOGUE) || values == null || !values.isJsonArray()) return;
        var array = values.getAsJsonArray(); int index = state.cursor().variant();
        switch (operation) {
            case "add" -> { array.add(""); index = array.size() - 1; }
            case "delete" -> { if (array.isEmpty()) return; array.remove(index); index = Math.max(0, index - 1); }
            case "up", "down" -> {
                int next = index + (operation.equals("up") ? -1 : 1);
                if (next < 0 || next >= array.size()) return;
                move(array, index, next); index = next;
            }
            default -> { return; }
        }
        write(state, null, new Cursor(state.cursor().step(), state.cursor().option(), index));
    }
    public void rootOption(String field, boolean enabled) {
        if (!List.of("requires", "skip_summary", "must_complete").contains(field)) return;
        var state = snapshot(); if (!editable(state, ResourceKind.DIALOGUE)) return;
        if (!enabled) state.data().remove(field);
        else if (field.equals("must_complete")) state.data().addProperty(field, true);
        else if (!state.data().has(field)) state.data().addProperty(field, "");
        write(state, null, state.cursor(), true);
    }
    public void intervalDefault(boolean inherited) {
        editNode(null, node -> {
            if (inherited) node.remove("typewriter_interval_ms");
            else if (!node.has("typewriter_interval_ms")) node.addProperty("typewriter_interval_ms", 30);
        });
    }
    public String editInterval(String value) {
        int number;
        try { number = new java.math.BigDecimal(value).intValueExact(); }
        catch (RuntimeException invalid) { return "edit.invalid_interval"; }
        if (number < 0 || number > 1000) return "edit.invalid_interval";
        editNode("typewriter_interval_ms", node -> node.addProperty("typewriter_interval_ms", number)); return "";
    }
    public record CommandEdit(ResourceKey resource, ProjectResource revision, int option, int index, String initial) {}

    /** A detached list also keeps malformed imported entries available for individual repair/removal. */
    public JsonArray commands() {
        var state = snapshot();
        return commandArray(get(option(state.data(), state.cursor().option()), "command"));
    }

    private static JsonArray commandArray(JsonElement value) {
        if (value != null && value.isJsonArray()) return value.getAsJsonArray().deepCopy();
        var result = new JsonArray();
        if (value != null) result.add(value.deepCopy());
        return result;
    }

    /** Merely opening/cancelling the native editor must not add an empty command or history entry. */
    public CommandEdit beginCommandEdit(int index) {
        var state = snapshot();
        if (!optionsEditable(state) || option(state.data(), state.cursor().option()) == null) return null;
        var values = commands();
        if (index < -1 || index >= values.size()) return null;
        String initial = index < 0 ? "" : isString(values.get(index)) ? string(values.get(index)) : values.get(index).toString();
        return new CommandEdit(state.key(), current.get().revision(state.key()), state.cursor().option(), index, initial);
    }

    public boolean completeCommandEdit(CommandEdit request, String command) {
        var state = snapshot();
        if (request == null || command == null || request.index() < -1 || !optionsEditable(state) || !request.resource().equals(state.key())
                || request.option() != state.cursor().option() || current.get().revision(state.key()) != request.revision()
                || !DialogueFields.error("command", new com.google.gson.JsonPrimitive(command)).isEmpty()) return false;
        var values = commands();
        if (request.index() < 0) values.add(command.strip());
        else if (request.index() < values.size()) values.set(request.index(), new com.google.gson.JsonPrimitive(command.strip()));
        else return false;
        option(state.data(), state.cursor().option()).add("command", values);
        endEdit.run(); write(state, null, state.cursor()); return true;
    }

    public void deleteCommand(int index) {
        var state = snapshot(); var values = commands();
        if (!optionsEditable(state) || index < 0 || index >= values.size()) return;
        values.remove(index);
        var option = option(state.data(), state.cursor().option());
        if (values.isEmpty()) option.remove("command"); else option.add("command", values);
        endEdit.run(); write(state, null, state.cursor());
    }

    public void moveCommand(int index, int delta) {
        var state = snapshot(); var values = commands(); int next = index + delta;
        if (!optionsEditable(state) || Math.abs(delta) != 1 || index < 0 || index >= values.size() || next < 0 || next >= values.size()) return;
        move(values, index, next);
        option(state.data(), state.cursor().option()).add("command", values);
        endEdit.run(); write(state, null, state.cursor());
    }
    public void editText(String text) {
        editTextField(ContentTextField.TEXT, text);
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
        editTextField(ContentTextField.SPEAKER_ID, id);
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
        editTextField(ContentTextField.EXIT_DIALOGUE, id);
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
    public void editOptionText(String text) { editTextField(ContentTextField.OPTION_TEXT, text); }
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
        editTextField(ContentTextField.OPTION_DIALOGUE, id);
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
        write(state, group, cursor, false);
    }
    private void write(Snapshot state, String group, Cursor cursor, boolean keepSelection) {
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
        if (keepSelection) changed.run(); else notifyChange(state.key(), false);
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
