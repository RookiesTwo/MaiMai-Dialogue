package top.rookiestwo.maimai_dialogue_editor.client.ui.properties;

import top.rookiestwo.maimai_dialogue_editor.document.ContentCursor;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.ChoicePresenter;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorActionRow;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorChoiceField;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorNumberField;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorPropertySection;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorTextBinding;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorTextField;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.PropertySectionState;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import top.rookiestwo.maimai_dialogue_editor.document.ContentWorkspace;
import top.rookiestwo.maimai_dialogue_editor.document.ContentTextField;
import top.rookiestwo.maimai_dialogue_editor.document.DialogueFields;
import top.rookiestwo.maimai_dialogue_editor.document.field.NumberField;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceCandidates;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static top.rookiestwo.maimai_dialogue_editor.document.DialogueDraft.*;

/** Field bindings are rebuilt only when the selected object or form shape changes, never while typing. */
final class ContentPropertiesView extends LinearLayout {
    private record Binding(ResourceKey resource, ContentCursor cursor, String shape) {}
    private final ProjectWorkspace workspace;
    private final ContentWorkspace content;
    private final ChoicePresenter choices;
    private final PropertySectionState layout;
    private LinearLayout group;
    private final List<Runnable> bindings = new ArrayList<>();
    private ContentWorkspace.Snapshot state;
    private Binding binding;
    private boolean refreshing;
    private final EditorIssueFocus issueFocus;
    private final java.util.Map<String, View> fields = new java.util.HashMap<>();

    ContentPropertiesView(Context context, ProjectWorkspace workspace, ChoicePresenter choices, PropertySectionState layout) {
        super(context);
        this.workspace = workspace;
        issueFocus = new EditorIssueFocus(this, workspace);
        content = workspace.content();
        this.choices = choices;
        this.layout = layout;
        setOrientation(VERTICAL);
    }

    void refresh() {
        state = content.snapshot();
        Binding next = new Binding(state.key(), state.cursor(), shape());
        refreshing = true;
        try {
            if (!Objects.equals(binding, next)) {
                binding = next;
                clearFocus();
                removeAllViews();
                bindings.clear();
                fields.clear();
                build();
            }
            for (Runnable update : bindings) update.run();
        } finally {
            refreshing = false;
        }
        focusIssue();
    }

    private void focusIssue() {
        var issue = workspace.focusedIssue();
        if (issue == null || workspace.actions().inspecting() || !Objects.equals(issue.resource(), state.key())
                || !issueFocus.pending()) return;
        String path = issue.field().replaceFirst("^steps\\[\\d+]\\.", "").replaceFirst("^end\\.", "");
        boolean option = path.startsWith("exit.options[");
        if (option) path = path.replaceFirst("^exit\\.options\\[\\d+]\\.", "");
        String label = switch (path) {
            case "name" -> "browser.display_name";
            case "text" -> option ? "edit.option_text" : "edit.markdown";
            case "speaker", "speaker.type" -> "edit.speaker";
            case "speaker.id" -> "edit.speaker_id";
            case "exit", "exit.type", "exit.options" -> "edit.exit";
            case "exit.dialogue", "target.dialogue" -> "edit.target_dialogue";
            case "target", "target.type" -> "edit.target";
            case "icon" -> "edit.icon";
            case "requires" -> "edit.requires_expression";
            case "skip_summary" -> "edit.skip_summary_text";
            case "must_complete" -> "edit.must_complete";
            case "typewriter_interval_ms" -> "edit.typewriter_interval";
            case "command" -> "edit.commands";
            default -> "";
        };
        View target = fields.get(label);
        if (target == null && path.equals("text")) target = fields.get("edit.text_mode");
        issueFocus.reveal(issue, target);
    }

    private String shape() {
        if (state.data() == null) return "invalid";
        if (state.key().kind() == ResourceKind.SPEAKER) return "speaker";
        if (rootSelected()) return "root/" + state.data().has("requires") + "/" + state.data().has("skip_summary");
        JsonObject node = selectedNode();
        JsonElement text = get(node, "text");
        JsonArray options = options(state.data());
        return (node == null ? (state.data().has("end") ? "invalid_node" : "missing_end") : "node")
                + "/" + (text == null ? "absent" : isString(text) ? "plain" : text.isJsonArray() ? "random/" + text.getAsJsonArray().size() : "unsupported")
                + "/" + (node != null && node.has("typewriter_interval_ms"))
                + "/" + speakerMode() + "/" + string(exit(state.data()), "type")
                + "/" + (options == null ? "invalid_options" : options.size())
                + "/" + (selectedOption() == null ? "no_option" : optionTargetType());
    }

    private boolean canEdit() {
        var selected = workspace.resources().selection();
        return !workspace.actions().inspecting() && content.active() && state.key() != null && state.key().equals(selected.owner())
                && (selected.isStep() || rootSelected() || state.key().kind() == ResourceKind.SPEAKER);
    }
    private boolean rootSelected() { return state.key() != null && state.key().kind() == ResourceKind.DIALOGUE
            && workspace.resources().selection().type() == top.rookiestwo.maimai_dialogue_editor.resource.ResourceTree.Type.RESOURCE; }
    private boolean accepts(Binding expected) {
        return !refreshing && isAttachedToWindow() && Objects.equals(expected, binding) && canEdit();
    }
    private JsonObject selectedNode() { return node(state.data(), state.cursor().step()); }
    private JsonObject selectedOption() { return option(state.data(), state.cursor().option()); }
    private String speakerMode() {
        JsonElement speaker = get(selectedNode(), "speaker");
        return speaker == null ? "inherit" : string(object(speaker), "type");
    }
    private String optionTargetType() { return string(object(get(selectedOption(), "target")), "type"); }

    private void build() {
        group = this;
        if (state.key() == null) return;
        if (state.data() == null) { warning("edit.invalid_object"); return; }
        if (state.key().kind() == ResourceKind.SPEAKER) {
            section("properties.general");
            field("browser.display_name", () -> string(state.data(), "name"), ContentTextField.NAME, false);
            return;
        }
        if (state.key().kind() != ResourceKind.DIALOGUE) return;
        if (rootSelected()) { buildRoot(); return; }
        var heading = EditorWidgets.compactParagraph(getContext(), "");
        heading.setText(EditorWidgets.tr(state.cursor().step() == END ? "edit.end" : "edit.step")
                + (state.cursor().step() == END ? "" : " " + (state.cursor().step() + 1)));
        addView(heading);
        if (state.cursor().step() != END) {
            LinearLayout actions = row();
            action(actions, "browser.copy", content::copyStep, () -> true);
            action(actions, "browser.delete", content::deleteStep, () -> true);
            action(actions, "edit.up", () -> content.moveStep(-1), () -> state.cursor().step() > 0);
            action(actions, "edit.down", () -> content.moveStep(1), () -> {
                JsonArray steps = array(state.data(), "steps");
                return steps != null && state.cursor().step() + 1 < steps.size();
            });
        }
        JsonObject node = selectedNode();
        if (node == null) {
            warning("edit.invalid_object");
            if (state.cursor().step() == END && !state.data().has("end")) {
                action(row(), "edit.create_end", content::createEnd, () -> true);
            }
            return;
        }
        section("edit.group.text");
        JsonElement text = node.get("text");
        if (text != null && !isString(text) && !text.isJsonArray()) warning("edit.unsupported_text");
        else {
            choice("edit.text_mode", () -> get(selectedNode(), "text") == null ? "absent" : isString(get(selectedNode(), "text")) ? "plain" : "random",
                    () -> items("edit.text.", "absent", "plain", "random"), content::setTextMode);
            if (text != null && text.isJsonArray()) buildVariants();
            else if (text != null) field("edit.markdown", () -> string(selectedNode(), "text"), ContentTextField.TEXT, true);
        }
        error(() -> DialogueFields.error("text", get(selectedNode(), "text")));
        buildInterval();
        section("edit.group.speaker");
        choice("edit.speaker", this::speakerMode, () -> items("edit.speaker.", "inherit", "set", "hide"), content::setSpeakerMode);
        if (speakerMode().equals("set")) reference("edit.speaker_id", ResourceKind.SPEAKER,
                () -> string(object(get(selectedNode(), "speaker")), "id"), ContentTextField.SPEAKER_ID);
        if (state.cursor().step() == END) buildEnd();
    }

    private void buildRoot() {
        section("edit.group.behavior");
        choice("edit.must_complete", () -> Boolean.toString(get(state.data(), "must_complete") instanceof com.google.gson.JsonPrimitive value
                        && value.isBoolean() && value.getAsBoolean()), () -> items("edit.boolean.", "false", "true"),
                value -> content.rootOption("must_complete", Boolean.parseBoolean(value)));
        error(() -> DialogueFields.error("must_complete", get(state.data(), "must_complete")));
        section("edit.requires");
        choice("edit.requires", () -> Boolean.toString(state.data().has("requires")), () -> items("edit.boolean.", "false", "true"),
                value -> content.rootOption("requires", Boolean.parseBoolean(value)));
        if (state.data().has("requires")) {
            field("edit.requires_expression", () -> string(state.data(), "requires"), ContentTextField.REQUIRES, false);
            ((EditText) fields.get("edit.requires_expression")).setHint("chapter.start && !chapter.end");
            error(() -> DialogueFields.error("requires", get(state.data(), "requires")));
        }
        section("edit.skip_summary");
        choice("edit.skip_summary", () -> Boolean.toString(state.data().has("skip_summary")), () -> items("edit.boolean.", "false", "true"),
                value -> content.rootOption("skip_summary", Boolean.parseBoolean(value)));
        if (state.data().has("skip_summary")) {
            field("edit.skip_summary_text", () -> string(state.data(), "skip_summary"), ContentTextField.SKIP_SUMMARY, true);
            error(() -> DialogueFields.error("skip_summary", get(state.data(), "skip_summary")));
        }
    }
    private JsonArray variants() { return array(selectedNode(), "text"); }
    private void buildVariants() {
        var actions = row();
        action(actions, "edit.add_variant", () -> content.changeVariants("add"), () -> true);
        action(actions, "browser.delete", () -> content.changeVariants("delete"), () -> !variants().isEmpty());
        if (variants().isEmpty()) return;
        choice("edit.variant", () -> Integer.toString(state.cursor().variant()), () -> {
            var items = new ArrayList<ChoicePresenter.Item>();
            for (int i = 0; i < variants().size(); i++) items.add(new ChoicePresenter.Item(Integer.toString(i),
                    (i + 1) + " · " + string(variants().get(i)).replace('\n', ' ')));
            return items;
        }, value -> content.selectVariant(Integer.parseInt(value)));
        var order = row();
        action(order, "edit.up", () -> content.changeVariants("up"), () -> state.cursor().variant() > 0);
        action(order, "edit.down", () -> content.changeVariants("down"), () -> state.cursor().variant() + 1 < variants().size());
        field("edit.markdown", () -> string(variants().get(state.cursor().variant())), ContentTextField.RANDOM_TEXT, true);
        choice("edit.preview_variant", () -> Integer.toString(workspace.simulation().variant(state.key().id(workspace.draft().namespace()), state.cursor().step())), () -> {
            var items = new ArrayList<ChoicePresenter.Item>(); items.add(new ChoicePresenter.Item("-1", EditorWidgets.tr("edit.text.random")));
            for (int i = 0; i < variants().size(); i++) items.add(new ChoicePresenter.Item(Integer.toString(i), EditorWidgets.tr("edit.variant") + " " + (i + 1)));
            return items;
        }, value -> workspace.simulation(workspace.simulation().withVariant(state.key().id(workspace.draft().namespace()), state.cursor().step(), Integer.parseInt(value))));
    }
    private void buildInterval() {
        section("edit.typewriter");
        choice("edit.typewriter_mode", () -> selectedNode().has("typewriter_interval_ms") ? "custom" : "default",
                () -> items("edit.interval.", "default", "custom"), value -> content.intervalDefault(value.equals("default")));
        if (!selectedNode().has("typewriter_interval_ms")) return;
        var expected = binding;
        var field = new NumberField("typewriter_interval_ms", 30, 0, 1000, true);
        Supplier<String> value = () -> { var number = get(selectedNode(), "typewriter_interval_ms"); return number != null && number.isJsonPrimitive() ? number.getAsString() : ""; };
        var input = new EditorNumberField(getContext(), field, EditorNumberField.Slider.range(field), value, content::editInterval, () -> accepts(expected), workspace::endEdit,
                () -> new top.rookiestwo.maimai_dialogue_editor.document.EditGesture() {
                    final Object draft = workspace.draft(); String pending;
                    public boolean update(String text) { if (!accepts(expected) || workspace.draft() != draft) return false; pending = text; return true; }
                    public void finish(boolean commit) { if (commit && pending != null && accepts(expected) && workspace.draft() == draft) content.editInterval(pending); }
                }, "edit.typewriter_interval");
        EditorWidgets.propertyRow(group, "edit.typewriter_interval", input, false); fields.put("edit.typewriter_interval", input);
        bindings.add(() -> input.refresh(canEdit()));
        error(() -> DialogueFields.error("typewriter_interval_ms", get(selectedNode(), "typewriter_interval_ms")));
    }
    private void error(Supplier<String> issue) {
        var label = EditorWidgets.compactParagraph(getContext(), ""); label.setTextColor(EditorWidgets.ERROR); group.addView(label);
        bindings.add(() -> { String text = issue.get(); label.setText(text); label.setVisibility(text.isEmpty() ? GONE : VISIBLE); });
    }

    private void buildEnd() {
        section("edit.group.exit");
        choice("edit.exit", () -> string(exit(state.data()), "type"),
                () -> items("edit.exit.", "return", "close", "dialogue", "options"), content::setExitType);
        String type = string(exit(state.data()), "type");
        if (type.equals("dialogue")) {
            reference("edit.target_dialogue", ResourceKind.DIALOGUE, () -> string(exit(state.data()), "dialogue"), ContentTextField.EXIT_DIALOGUE);
        } else if (type.equals("options")) {
            section("edit.group.options");
            JsonArray options = options(state.data());
            if (options == null) { warning("edit.invalid_options"); return; }
            LinearLayout actions = row();
            action(actions, "edit.add_option", content::addOption, () -> true);
            action(actions, "browser.copy", content::copyOption, () -> state.cursor().option() >= 0);
            action(actions, "browser.delete", content::deleteOption, () -> state.cursor().option() >= 0);
            if (options.isEmpty()) { warning("edit.empty_options"); return; }
            choice("edit.option", () -> Integer.toString(state.cursor().option()), this::optionItems,
                    value -> content.selectOption(Integer.parseInt(value)));
            LinearLayout order = row();
            action(order, "edit.up", () -> content.moveOption(-1), () -> state.cursor().option() > 0);
            action(order, "edit.down", () -> content.moveOption(1), () -> state.cursor().option() + 1 < options(state.data()).size());
            if (selectedOption() == null) { warning("edit.invalid_object"); return; }
            field("edit.option_text", () -> string(selectedOption(), "text"), ContentTextField.OPTION_TEXT, false);
            choice("edit.icon", () -> selectedOption().has("icon") ? string(selectedOption(), "icon") : "none",
                    () -> items("edit.icon.", "none", "question", "exclamation", "dialogue"), content::setOptionIcon);
            choice("edit.target", this::optionTargetType,
                    () -> items("edit.target.", "return", "close", "dialogue"), content::setOptionTarget);
            if (optionTargetType().equals("dialogue")) reference("edit.target_dialogue", ResourceKind.DIALOGUE,
                    () -> string(object(get(selectedOption(), "target")), "dialogue"), ContentTextField.OPTION_DIALOGUE);
            section("edit.commands");
            var commands = new OptionCommandsView(getContext(), workspace, choices);
            group.addView(commands, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            fields.put("edit.commands", commands);
            bindings.add(() -> commands.refresh(canEdit()));
            error(() -> DialogueFields.error("command", get(selectedOption(), "command")));
        }
    }

    private List<ChoicePresenter.Item> optionItems() {
        List<ChoicePresenter.Item> items = new ArrayList<>();
        JsonArray options = options(state.data());
        if (options != null) for (int index = 0; index < options.size(); index++) {
            String text = string(object(options.get(index)), "text");
            items.add(new ChoicePresenter.Item(Integer.toString(index), (index + 1) + ". "
                    + (text.isBlank() ? EditorWidgets.tr("edit.untitled_option") : text)));
        }
        return items;
    }

    private void field(String label, Supplier<String> value, ContentTextField field, boolean multiline) {
        field(label, value, field, multiline, null);
    }
    private void field(String label, Supplier<String> value, ContentTextField field, boolean multiline, Button picker) {
        Binding expected = binding;
        Object buffer = new Object();
        var edit = EditorTextBinding.plain(value, () -> accepts(expected), text -> content.editTextField(field, text), workspace::endEdit)
                .onChange(text -> {
                    if (text.equals(value.get())) workspace.clearStagedText(buffer);
                    else workspace.stageText(buffer, expected.resource(), expected.cursor(), field, text);
                }).onBlur(() -> workspace.clearStagedText(buffer));
        var control = new EditorTextField(getContext(), edit);
        EditText input = control.input();
        if (multiline) {
            input.setSingleLine(false);
            input.setGravity(Gravity.TOP | Gravity.START);
            EditorWidgets.bindMetrics(input, () -> {
                input.setMinLines(4);
                input.setMaxLines(10);
                input.setMinimumHeight(dp(96));
            });
        }
        if (picker == null) EditorWidgets.propertyRow(group, label, input, multiline);
        else EditorWidgets.referenceRow(group, label, input, picker);
        fields.put(label, input);
        bindings.add(() -> control.refresh(canEdit()));
    }

    private void reference(String label, ResourceKind kind, Supplier<String> value, ContentTextField field) {
        Binding expected = binding;
        Button picker = EditorWidgets.button(getContext(), "edit.choose_resource", () -> {});
        picker.setOnClickListener(view -> {
            if (!accepts(expected)) return;
            var draft = workspace.draft();
            if (draft == null) return;
            var items = ResourceCandidates.project(workspace.resources().catalog(), draft.namespace(), kind, ResourceCandidates.Label.NAME_AND_ID);
            choices.showResources(picker, items, value.get(), selected -> {
                if (accepts(expected)) content.editTextField(field, selected);
            });
        });
        field(label, value, field, false, picker);
        bindings.add(() -> EditorWidgets.enabled(picker, canEdit()));
    }

    private void choice(String label, Supplier<String> value, Supplier<List<ChoicePresenter.Item>> items, Consumer<String> setter) {
        Binding expected = binding;
        var control = new EditorChoiceField(getContext(), choices, value, items, () -> accepts(expected), this::canEdit,
                setter, selected -> EditorWidgets.tr("edit.unset"));
        EditorWidgets.propertyRow(group, label, control.button(), false);
        if (label != null) fields.put(label, control.button());
        bindings.add(control::refresh);
    }

    private static List<ChoicePresenter.Item> items(String prefix, String... values) {
        return Arrays.stream(values).map(value -> new ChoicePresenter.Item(value, EditorWidgets.tr(prefix + value))).toList();
    }

    private void section(String key) {
        var section = new EditorPropertySection(getContext(), key, layout);
        addView(section, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        bindings.add(section::refresh);
        group = section.body();
    }
    private void warning(String key) { group.addView(EditorWidgets.compactParagraph(getContext(), key)); }
    private LinearLayout row() {
        LinearLayout row = new EditorActionRow(getContext());
        group.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return row;
    }
    private void action(LinearLayout row, String label, Runnable action, BooleanSupplier enabled) {
        Binding expected = binding;
        Button button = EditorWidgets.button(getContext(), label, () -> { if (accepts(expected)) action.run(); });
        row.addView(button);
        bindings.add(() -> EditorWidgets.enabled(button, canEdit() && enabled.getAsBoolean()));
    }
}
