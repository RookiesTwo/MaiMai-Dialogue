package top.rookiestwo.maimai_dialogue_editor.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import top.rookiestwo.maimai_dialogue_editor.document.ContentWorkspace;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
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
    private record Binding(ResourceKey resource, ContentWorkspace.Cursor cursor, String shape) {}
    private final ProjectWorkspace workspace;
    private final ContentWorkspace content;
    private final ChoicePresenter choices;
    private final List<Runnable> bindings = new ArrayList<>();
    private ContentWorkspace.Snapshot state;
    private Binding binding;
    private boolean refreshing;
    private long focusedRevision = -1;
    private final java.util.Map<String, View> fields = new java.util.HashMap<>();

    ContentPropertiesView(Context context, ProjectWorkspace workspace, ChoicePresenter choices) {
        super(context);
        this.workspace = workspace;
        content = workspace.content();
        this.choices = choices;
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
        if (issue == null || !Objects.equals(issue.resource(), state.key())
                || focusedRevision == workspace.issueFocusRevision()) return;
        focusedRevision = workspace.issueFocusRevision();
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
            default -> "";
        };
        View target = fields.get(label);
        if (target == null && path.equals("text")) target = fields.get("edit.text_mode");
        View selected = target;
        long revision = focusedRevision;
        if (selected != null) post(() -> {
            if (isAttachedToWindow() && revision == workspace.issueFocusRevision() && workspace.focusedIssue() == issue) {
                selected.requestFocus();
                selected.requestRectangleOnScreen(new Rect(0, 0, selected.getWidth(), selected.getHeight()));
            }
        });
    }

    private String shape() {
        if (state.data() == null) return "invalid";
        if (state.key().kind() == ResourceKind.SPEAKER) return "speaker";
        JsonObject node = selectedNode();
        JsonElement text = get(node, "text");
        JsonArray options = options(state.data());
        return (node == null ? (state.data().has("end") ? "invalid_node" : "missing_end") : "node")
                + "/" + (text == null ? "absent" : isString(text) ? "plain" : "unsupported")
                + "/" + speakerMode() + "/" + string(exit(state.data()), "type")
                + "/" + (options == null ? "invalid_options" : options.size())
                + "/" + (selectedOption() == null ? "no_option" : optionTargetType());
    }

    private boolean canEdit() {
        var selected = workspace.resources().selection();
        return content.active() && state.key() != null && state.key().equals(selected.owner())
                && (selected.isStep() || state.key().kind() == ResourceKind.SPEAKER);
    }
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
        if (state.key() == null) return;
        if (state.data() == null) { warning("edit.invalid_object"); return; }
        if (state.key().kind() == ResourceKind.SPEAKER) {
            field("browser.display_name", () -> string(state.data(), "name"), content::editSpeakerName, false);
            return;
        }
        if (state.key().kind() != ResourceKind.DIALOGUE) return;
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
        JsonElement text = node.get("text");
        if (text != null && !isString(text)) warning("edit.unsupported_text");
        else {
            choice("edit.text_mode", () -> get(selectedNode(), "text") == null ? "absent" : "plain",
                    () -> items("edit.text.", "absent", "plain"), content::setTextMode);
            if (text != null) field("edit.markdown", () -> string(selectedNode(), "text"), content::editText, true);
        }
        choice("edit.speaker", this::speakerMode, () -> items("edit.speaker.", "inherit", "set", "hide"), content::setSpeakerMode);
        if (speakerMode().equals("set")) reference("edit.speaker_id", ResourceKind.SPEAKER,
                () -> string(object(get(selectedNode(), "speaker")), "id"), content::editSpeakerId);
        if (state.cursor().step() == END) buildEnd();
    }

    private void buildEnd() {
        choice("edit.exit", () -> string(exit(state.data()), "type"),
                () -> items("edit.exit.", "return", "dialogue", "options"), content::setExitType);
        String type = string(exit(state.data()), "type");
        if (type.equals("dialogue")) {
            reference("edit.target_dialogue", ResourceKind.DIALOGUE, () -> string(exit(state.data()), "dialogue"), content::editExitDialogue);
        } else if (type.equals("options")) {
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
            field("edit.option_text", () -> string(selectedOption(), "text"), content::editOptionText, false);
            choice("edit.icon", () -> selectedOption().has("icon") ? string(selectedOption(), "icon") : "none",
                    () -> items("edit.icon.", "none", "question", "exclamation", "dialogue"), content::setOptionIcon);
            choice("edit.target", this::optionTargetType,
                    () -> items("edit.target.", "return", "close", "dialogue"), content::setOptionTarget);
            if (optionTargetType().equals("dialogue")) reference("edit.target_dialogue", ResourceKind.DIALOGUE,
                    () -> string(object(get(selectedOption(), "target")), "dialogue"), content::editOptionDialogue);
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

    private void field(String label, Supplier<String> value, Consumer<String> setter, boolean multiline) {
        Binding expected = binding;
        EditText input = EditorWidgets.compactInput(getContext(), value.get(), text -> {
            if (accepts(expected)) setter.accept(text);
        }, workspace::endEdit);
        if (multiline) {
            input.setSingleLine(false);
            input.setGravity(Gravity.TOP | Gravity.START);
            EditorWidgets.bindMetrics(input, () -> {
                input.setMinLines(4);
                input.setMaxLines(10);
                input.setMinimumHeight(dp(96));
            });
        }
        EditorWidgets.propertyRow(this, label, input, multiline);
        fields.put(label, input);
        bindings.add(() -> {
            String text = value.get();
            if (!input.getText().toString().equals(text)) input.setText(text);
            input.setEnabled(canEdit());
        });
    }

    private void reference(String label, ResourceKind kind, Supplier<String> value, Consumer<String> setter) {
        field(label, value, setter, false);
        choice(null, value, () -> {
            var draft = workspace.draft();
            if (draft == null) return List.of();
            var catalog = workspace.resources().catalog();
            return catalog.keys().stream().filter(key -> key.kind() == kind).map(key -> {
                String name = catalog.displayName(key);
                String id = key.id(draft.namespace());
                return new ChoicePresenter.Item(id, name.isBlank() ? id : name + " · " + id);
            }).toList();
        }, setter);
    }

    private void choice(String label, Supplier<String> value, Supplier<List<ChoicePresenter.Item>> items, Consumer<String> setter) {
        Binding expected = binding;
        Button button = EditorWidgets.button(getContext(), "", () -> {});
        button.setOnClickListener(view -> {
            if (accepts(expected)) choices.show(button, items.get(), value.get(), selected -> {
                if (accepts(expected)) setter.accept(selected);
            });
        });
        EditorWidgets.propertyRow(this, label, button, false);
        if (label != null) fields.put(label, button);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        EditorWidgets.bindMetrics(button, () -> {
            button.setPadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP),
                    0, dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0);
        });
        bindings.add(() -> {
            String text = label == null ? EditorWidgets.tr("edit.choose_resource") : items.get().stream()
                    .filter(item -> item.value().equals(value.get())).map(ChoicePresenter.Item::label).findFirst()
                    .orElse(EditorWidgets.tr("edit.unset"));
            button.setText(text + " ▾");
            button.setTooltipText(text);
            EditorWidgets.enabled(button, canEdit());
        });
    }

    private static List<ChoicePresenter.Item> items(String prefix, String... values) {
        return Arrays.stream(values).map(value -> new ChoicePresenter.Item(value, EditorWidgets.tr(prefix + value))).toList();
    }

    private void warning(String key) { addView(EditorWidgets.compactParagraph(getContext(), key)); }
    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return row;
    }
    private void action(LinearLayout row, String label, Runnable action, BooleanSupplier enabled) {
        Binding expected = binding;
        Button button = EditorWidgets.button(getContext(), label, () -> { if (accepts(expected)) action.run(); });
        row.addView(button);
        EditorWidgets.bindMetrics(button, () -> {
            button.setPadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0,
                    dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0);
            button.setLayoutParams(new LayoutParams(0, dp(EditorWidgets.COMPACT_CONTROL_DP), 1));
        });
        bindings.add(() -> EditorWidgets.enabled(button, canEdit() && enabled.getAsBoolean()));
    }
}
