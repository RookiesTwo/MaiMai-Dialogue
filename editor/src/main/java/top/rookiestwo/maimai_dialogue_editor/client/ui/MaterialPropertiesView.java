package top.rookiestwo.maimai_dialogue_editor.client.ui;

import com.google.gson.*;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import top.rookiestwo.maimai_dialogue_editor.material.*;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.util.*;
import java.util.function.*;

/** Compact controls for asset variants and imported-file metadata. No file IO here. */
final class MaterialPropertiesView extends LinearLayout {
    private final ProjectWorkspace project;
    private final MaterialWorkspace model;
    private final ChoicePresenter choices;
    private final EditorLayoutState layout;
    private LinearLayout group;
    private final List<Runnable> bindings = new ArrayList<>();
    private ResourceKey key;
    private JsonObject data;
    private String binding = "";
    private boolean refreshing;

    MaterialPropertiesView(Context context, ProjectWorkspace project, ChoicePresenter choices, EditorLayoutState layout) {
        super(context); this.project = project; this.model = project.materials(); this.choices = choices;
        this.layout = layout;
        setOrientation(VERTICAL);
    }
    void refresh() {
        var state = project.content().snapshot(); key = state.key(); data = state.data();
        String shape = key == null ? "" : key.toString() + "/" + (data == null ? "invalid" : key.kind() == ResourceKind.VISUAL_ASSET
                ? (data.get("variants") instanceof JsonObject v ? (v.isEmpty() ? "empty" : "variants") : "invalid") : "file");
        refreshing = true;
        try {
            if (!binding.equals(shape)) {
                binding = shape; clearFocus(); removeAllViews(); bindings.clear(); build();
            }
            bindings.forEach(Runnable::run);
        } finally { refreshing = false; }
    }
    private boolean active() {
        return !refreshing && isAttachedToWindow() && project.content().active()
                && key != null && key.equals(project.resources().selection().owner());
    }
    private String variant() { return model.variant(key, data); }
    private String value(String field) { return data == null ? "" : MaterialPack.string(data.get(field)); }
    private void build() {
        group = this;
        if (key == null || (!key.kind().material() && key.kind() != ResourceKind.VISUAL_ASSET)) return;
        if (data == null) { addView(EditorWidgets.compactParagraph(getContext(), "edit.invalid_object")); return; }
        if (key.kind() == ResourceKind.VISUAL_ASSET) {
            if (!(data.get("variants") instanceof JsonObject)) { addView(EditorWidgets.compactParagraph(getContext(), "edit.invalid_object")); return; }
            section("material.group.settings");
            choice("material.sampling", () -> value("sampling").isEmpty() ? "linear" : value("sampling"),
                    () -> List.of(new ChoicePresenter.Item("linear", EditorWidgets.tr("material.linear")),
                            new ChoicePresenter.Item("nearest", EditorWidgets.tr("material.nearest"))), model::setSampling);
            section("material.group.variants");
            Button add = EditorWidgets.button(getContext(), "material.add_variant", () -> { if (active()) model.addVariant(); });
            EditorWidgets.propertyRow(group, null, add, false);
            if (data.getAsJsonObject("variants").isEmpty()) return;
            choice("material.variant", this::variant, () -> data.getAsJsonObject("variants").keySet().stream()
                    .map(name -> new ChoicePresenter.Item(name, name)).toList(), model::selectVariant);
            variantNameField();
            reference("material.image_id", () -> MaterialPack.string(data.getAsJsonObject("variants").get(variant())), () ->
                    project.resources().catalog().keys().stream().filter(k -> k.kind() == ResourceKind.IMAGE)
                            .map(k -> new ChoicePresenter.Item(MaterialPack.imageId(k, project.draft().namespace()), k.path())).toList(), model::setVariantImage);
            Button remove = EditorWidgets.button(getContext(), "material.delete_variant", () -> { if (active()) model.deleteVariant(); });
            EditorWidgets.propertyRow(group, null, remove, false);
        } else {
            section("material.group.file");
            TextView info = EditorWidgets.compactParagraph(getContext(), ""); group.addView(info);
            bindings.add(() -> info.setText(key.kind() == ResourceKind.IMAGE
                    ? MaterialPack.imageId(key, project.draft().namespace()) + "\n" + number("width") + " × " + number("height")
                    : key.id(project.draft().namespace()) + ".ogg\n" + number("channels") + " ch · " + number("sample_rate") + " Hz"));
            Button replace = EditorWidgets.button(getContext(), "material.replace", () -> { if (active()) model.begin(key); });
            EditorWidgets.propertyRow(group, null, replace, false);
            if (key.kind() == ResourceKind.SOUND) {
                section("material.group.sound_event");
                reference("material.event", () -> value("event"), () -> project.resources().catalog().keys().stream()
                        .filter(k -> k.kind() == ResourceKind.SOUND).map(k -> project.resources().catalog().displayName(k))
                        .filter(name -> !name.isBlank()).distinct().map(name -> new ChoicePresenter.Item(name, name)).toList(), model::setEvent);
                choice("material.stream", () -> String.valueOf(data.has("stream") && data.get("stream").isJsonPrimitive()
                                && data.get("stream").getAsBoolean()), () -> List.of(
                                new ChoicePresenter.Item("false", EditorWidgets.tr("material.stream.false")),
                                new ChoicePresenter.Item("true", EditorWidgets.tr("material.stream.true"))),
                        value -> model.setStream(Boolean.parseBoolean(value)));
            }
        }
    }
    private String number(String field) {
        JsonElement value = data.get(field);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "?";
    }
    private void variantNameField() {
        String expected = binding;
        String[] boundVariant = {variant()};
        boolean[] invalid = {false};
        EditText input = EditorWidgets.compactInput(getContext(), variant(), ignored -> {}, () -> {});
        input.setTag(EditorWidgets.DEFERRED_INPUT_TAG, Boolean.TRUE);
        TextView error = EditorWidgets.compactParagraph(getContext(), "");
        error.setTextColor(EditorWidgets.ERROR);
        error.setVisibility(GONE);
        input.setOnFocusChangeListener((view, focused) -> {
            if (focused) {
                invalid[0] = false;
                error.setVisibility(GONE);
                return;
            }
            if (active() && binding.equals(expected) && boundVariant[0].equals(variant())) {
                String issue = model.renameVariant(input.getText().toString());
                invalid[0] = !issue.isEmpty();
                error.setText(invalid[0] ? EditorWidgets.tr(issue) : "");
                error.setVisibility(invalid[0] ? VISIBLE : GONE);
            }
            project.endEdit();
        });
        EditorWidgets.propertyRow(group, "material.variant_name", input, false); group.addView(error);
        bindings.add(() -> {
            String current = variant();
            if (!boundVariant[0].equals(current)) {
                boundVariant[0] = current;
                invalid[0] = false;
                error.setVisibility(GONE);
                input.setText(current);
            } else if (!input.isFocused() && !invalid[0] && !input.getText().toString().equals(current)) {
                input.setText(current);
            }
        });
    }
    private void reference(String label, Supplier<String> value, Supplier<List<ChoicePresenter.Item>> items, Consumer<String> setter) {
        String expected = binding;
        Button picker = EditorWidgets.button(getContext(), "edit.choose_resource", () -> {});
        picker.setOnClickListener(view -> {
            if (!active() || !binding.equals(expected)) return;
            choices.showSearchable(picker, items.get(), value.get(), selected -> {
                if (active() && binding.equals(expected)) setter.accept(selected);
            });
        });
        EditText input = EditorWidgets.compactInput(getContext(), value.get(), ignored -> {}, () -> {});
        input.setTag(EditorWidgets.DEFERRED_INPUT_TAG, Boolean.TRUE);
        input.setOnFocusChangeListener((view, focused) -> {
            if (!focused && active() && binding.equals(expected)) {
                String entered = input.getText().toString();
                if (!entered.equals(value.get())) setter.accept(entered);
                project.endEdit();
            }
        });
        EditorWidgets.referenceRow(group, label, input, picker);
        bindings.add(() -> {
            if (!input.isFocused() && !input.getText().toString().equals(value.get())) input.setText(value.get());
            input.setEnabled(project.content().active());
            EditorWidgets.enabled(picker, project.content().active());
        });
    }
    private void choice(String label, Supplier<String> value, Supplier<List<ChoicePresenter.Item>> items, Consumer<String> setter) {
        String expected = binding;
        Button button = EditorWidgets.fieldButton(getContext(), "", null);
        button.setOnClickListener(view -> {
            if (active() && binding.equals(expected)) choices.show(button, items.get(), value.get(), selected -> {
                if (active() && binding.equals(expected)) setter.accept(selected);
            });
        });
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        EditorWidgets.bindMetrics(button, () -> {
            int padding = dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP);
            button.setPadding(padding, 0, padding, 0);
        });
        EditorWidgets.propertyRow(group, label, button, false);
        bindings.add(() -> {
            EditorWidgets.enabled(button, project.content().active());
            String text = items.get().stream()
                    .filter(item -> item.value().equals(value.get())).map(ChoicePresenter.Item::label).findFirst().orElse(value.get());
            button.setText(text + " ▾"); button.setTooltipText(text);
        });
    }
    private void section(String key) {
        var section = new EditorPropertySection(getContext(), key, layout);
        addView(section, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        bindings.add(section::refresh);
        group = section.body();
    }
}
