package top.rookiestwo.maimai_dialogue_editor.client.ui;

import com.google.gson.*;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.bootstrap.ClientServices;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualAnchor;
import top.rookiestwo.maimai_dialogue_editor.document.SceneWorkspace;
import top.rookiestwo.maimai_dialogue_editor.document.SceneWorkspace.Part;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialPack;
import java.util.*;
import java.util.function.*;
import static top.rookiestwo.maimai_dialogue_editor.document.SceneWorkspace.*;

/** Compact Scene inspector. Draft operations and selection live in SceneWorkspace, not in Views. */
final class ScenePropertiesView extends LinearLayout {
    private final ProjectWorkspace project;
    private final SceneWorkspace model;
    private final ChoicePresenter choices;
    private final EditorLayoutState layout;
    private final List<Runnable> bindings = new ArrayList<>();
    private final List<EditorPropertySection> sections = new ArrayList<>();
    private LinearLayout group;
    private String binding = "";
    private boolean refreshing;

    ScenePropertiesView(Context context, ProjectWorkspace project, ChoicePresenter choices, EditorLayoutState layout) {
        this(context, project, choices, layout, project.scenes());
    }
    ScenePropertiesView(Context context, ProjectWorkspace project, ChoicePresenter choices, EditorLayoutState layout, SceneWorkspace model) {
        super(context); this.project = project; this.model = model; this.choices = choices; setOrientation(VERTICAL);
        this.layout = layout;
    }
    void refresh() {
        var state = model.snapshot();
        String next = !model.acceptsResource(state.key()) ? "" : project.projectGeneration() + "/" + state.key() + "/"
                + (state.data() == null ? "invalid" : model.objectId() + "/" + shape(model.objectMap()) + "/"
                + model.data().has("background") + "/" + component(model.part(Part.BACKGROUND)) + "/" + shape(model.variantMap(Part.BACKGROUND)) + "/" + model.variant(Part.BACKGROUND)
                + "/" + component(model.part(Part.OBJECT)) + "/" + shape(model.variantMap(Part.OBJECT)) + "/" + model.variant(Part.OBJECT)
                + "/" + model.data().has("filter") + "/" + component(model.part(Part.FILTER)) + "/" + text(model.part(Part.FILTER), "type", "none")
                );
        refreshing = true;
        try {
            if (!binding.equals(next)) { binding = next; clearFocus(); removeAllViews(); bindings.clear(); sections.clear(); group = this; build(); }
            bindings.forEach(Runnable::run);
            sections.forEach(EditorPropertySection::refresh);
        } finally { refreshing = false; }
    }
    private static String shape(JsonObject data) { return data == null ? "absent" : data.keySet().toString(); }
    private static String component(JsonObject data) {
        return data == null ? "invalid" : data.has("asset") + "/"
                + (data.has("variants") ? data.get("variants").getClass().getSimpleName() : "absent");
    }
    private boolean accepts(String expected) {
        return !refreshing && isAttachedToWindow() && binding.equals(expected) && model.active();
    }
    private String value(Part part, String field, String fallback) { return text(model.part(part), field, fallback); }
    private void build() {
        if (binding.isEmpty()) return;
        if (model.data() == null) { warning(); return; }
        title("scene.background");
        choice("scene.enabled", () -> String.valueOf(model.data().has("background")),
                () -> items("scene.boolean.", "false", "true"), value -> model.setBackground(Boolean.parseBoolean(value)));
        if (model.data().has("background")) {
            if (model.part(Part.BACKGROUND) == null) warning();
            else {
                reference("scene.asset", ResourceKind.VISUAL_ASSET, () -> value(Part.BACKGROUND, "asset", ""),
                        value -> model.setAsset(Part.BACKGROUND, value));
                initialVariant(Part.BACKGROUND);
                choice("scene.fit", () -> value(Part.BACKGROUND, "fit", "cover"),
                        () -> items("scene.fit.", "cover", "contain", "stretch"), value -> model.setText(Part.BACKGROUND, "fit", value));
                number(Part.BACKGROUND, new NumberField("opacity", 1, 0, 1));
            }
        }
        title("scene.objects");
        LinearLayout actions = row();
        action(actions, "scene.add_object", () -> model.addObject(false), () -> !model.data().has("visual_objects") || model.objectMap() != null);
        action(actions, "browser.copy", () -> model.addObject(true), () -> !model.objectId().isEmpty());
        action(actions, "browser.delete", model::deleteObject, () -> !model.objectId().isEmpty());
        if (model.data().has("visual_objects") && model.objectMap() == null) warning();
        if (model.objectMap() != null && !model.objectMap().isEmpty()) {
            choice("scene.object", model::objectId, () -> names(model.objectMap()), model::selectObject);
        }
        if (!model.objectId().isEmpty()) {
            field("scene.object_id", model::objectId, model::renameObject, false);
            if (model.part(Part.OBJECT) == null) warning();
            else {
                choice("scene.source", () -> model.part(Part.OBJECT).has("asset") ? "asset" : "inline",
                        () -> items("scene.source.", "inline", "asset"), model::setSource);
                if (model.part(Part.OBJECT).has("asset")) {
                    reference("scene.asset", ResourceKind.VISUAL_ASSET, () -> value(Part.OBJECT, "asset", ""), model::setAsset);
                    initialVariant(Part.OBJECT);
                } else {
                    variants(Part.OBJECT);
                    action(row(), "browser.extract", () -> {
                        project.endEdit();
                        project.resources().beginExtract(InlineResource.visualAsset(project.draft(), model.snapshot().key(), model.objectId()),
                                model.snapshot().key().path() + "_" + model.objectId());
                    }, () -> model.variantMap(Part.OBJECT) != null);
                }
                for (NumberField field : OBJECT_NUMBERS) number(Part.OBJECT, field);
                choice("scene.anchor", () -> value(Part.OBJECT, "anchor", "center"),
                        () -> Arrays.stream(VisualAnchor.values()).map(anchor -> new ChoicePresenter.Item(anchor.serializedName(),
                                EditorWidgets.tr("scene.anchor." + anchor.serializedName()))).toList(),
                        value -> model.setText(Part.OBJECT, "anchor", value));
                choice("material.sampling", () -> value(Part.OBJECT, "sampling", ""), () -> List.of(
                        new ChoicePresenter.Item("", EditorWidgets.tr("scene.sampling_default")),
                        new ChoicePresenter.Item("linear", EditorWidgets.tr("material.linear")),
                        new ChoicePresenter.Item("nearest", EditorWidgets.tr("material.nearest"))),
                        value -> model.setText(Part.OBJECT, "sampling", value));
                choice("scene.visible", () -> value(Part.OBJECT, "visible", "true"), () -> items("scene.boolean.", "false", "true"),
                        value -> model.setText(Part.OBJECT, "visible", value));
            }
        }
        title("scene.filter");
        choice("scene.filter_type", () -> value(Part.FILTER, "type", "none"),
                () -> items("scene.filter.", "none", "color_adjust", "crt"), model::setFilter);
        if (model.data().has("filter") && model.part(Part.FILTER) == null) warning();
        String type = value(Part.FILTER, "type", "none");
        if (type.equals("color_adjust")) {
            for (NumberField field : COLOR_NUMBERS) number(Part.FILTER, field);
            String expected = binding;
            var tint = new EditorColorField(getContext(), () -> value(Part.FILTER, "tint", ""),
                    value -> model.setText(Part.FILTER, "tint", value), () -> accepts(expected), project::endEdit, choices);
            EditorWidgets.propertyRow(group, "scene.tint", tint, false);
            bindings.add(() -> tint.refresh(model.active()));
        } else if (type.equals("crt")) for (NumberField field : CRT_NUMBERS) number(Part.FILTER, field);
    }
    private void variants(Part part) {
        if (model.variantMap(part) == null) { warning(); return; }
        LinearLayout actions = row();
        action(actions, "material.add_variant", () -> model.addVariant(part), () -> true);
        action(actions, "material.delete_variant", () -> model.deleteVariant(part), () -> !model.variant(part).isEmpty());
        if (model.variantMap(part).isEmpty()) return;
        choice("material.variant", () -> model.variant(part), () -> names(model.variantMap(part)), value -> model.selectVariant(part, value));
        field("material.variant_name", () -> model.variant(part), value -> model.renameVariant(part, value), false);
        reference("material.image_id", ResourceKind.IMAGE, () -> text(model.variantMap(part), model.variant(part), ""),
                value -> model.setVariantImage(part, value));
        initialVariant(part);
    }
    private void initialVariant(Part part) {
        String expected = binding;
        Button button = choice("scene.initial_variant", () -> value(part, "initial_variant", part == Part.BACKGROUND ? "default" : ""),
                () -> variantItems(part), value -> model.setText(part, "initial_variant", value));
        button.setOnClickListener(view -> showVariants(button, expected, part));
    }
    private List<ChoicePresenter.Item> variantItems(Part part) {
        ResourceLocation asset = ResourceLocation.tryParse(value(part, "asset", ""));
        if (asset != null && project.draft() != null && !asset.getNamespace().equals(project.draft().namespace())) {
            return ClientServices.get().content().current().visualAssets().find(asset)
                    .map(definition -> definition.variants().keySet().stream().sorted()
                            .map(name -> new ChoicePresenter.Item(name, name)).toList()).orElse(List.of());
        }
        return names(model.variantMap(part));
    }
    private void showVariants(Button button, String expected, Part part) {
        if (!accepts(expected) || !button.isAttachedToWindow()) return;
        String source = value(part, "asset", "");
        ResourceLocation asset = ResourceLocation.tryParse(source);
        if (asset != null && asset.getNamespace().equals(project.draft().namespace())) {
            ResourceKey key = new ResourceKey(ResourceKind.VISUAL_ASSET, asset.getPath());
            if (project.draft().revision(key) != null && !project.draft().isLoaded(key)
                    && !project.resources().whenLoaded(key, () -> {
                        if (source.equals(value(part, "asset", ""))) showVariants(button, expected, part);
                    })) return;
        }
        choices.show(button, variantItems(part), value(part, "initial_variant", part == Part.BACKGROUND ? "default" : ""), selected -> {
            if (accepts(expected) && source.equals(value(part, "asset", ""))
                    && variantItems(part).stream().anyMatch(item -> item.value().equals(selected))) {
                model.setText(part, "initial_variant", selected);
            }
        });
    }
    private void number(Part part, NumberField field) {
        String fallback = field.integer() ? Integer.toString((int)field.fallback()) : Float.toString(field.fallback());
        String expected = binding;
        var control = new EditorNumberField(getContext(), field, () -> value(part, field.name(), fallback),
                value -> model.setNumber(part, field, value), () -> accepts(expected), project::endEdit,
                () -> model.beginNumberDrag(part, field));
        EditorWidgets.propertyRow(group, "scene." + field.name(), control, false);
        bindings.add(() -> control.refresh(model.active()));
    }
    private void field(String label, Supplier<String> value, Function<String, String> setter, boolean live) {
        field(label, value, setter, live, null);
    }
    private void field(String label, Supplier<String> value, Function<String, String> setter, boolean live, Button picker) {
        String expected = binding;
        var edit = new EditorTextBinding(value, () -> accepts(expected), setter, project::endEdit)
                .onChange(text -> { if (live && !text.isBlank()) setter.apply(text); });
        var control = new EditorTextField(getContext(), edit);
        EditText input = control.input();
        if (picker == null) EditorWidgets.propertyRow(group, label, input, false);
        else EditorWidgets.referenceRow(group, label, input, picker);
        group.addView(control.error());
        bindings.add(() -> control.refresh(model.active()));
    }
    private void reference(String label, ResourceKind kind, Supplier<String> value, Consumer<String> setter) {
        String expected = binding;
        Button picker = EditorWidgets.button(getContext(), "edit.choose_resource", () -> {});
        picker.setOnClickListener(view -> {
            if (!accepts(expected)) return;
            choices.showSearchable(picker, resourceItems(kind), value.get(), selected -> {
                if (accepts(expected) && !selected.equals(value.get())) setter.accept(selected);
            });
        });
        field(label, value, text -> { setter.accept(text); return ""; }, false, picker);
        bindings.add(() -> EditorWidgets.enabled(picker, model.active()));
    }
    private List<ChoicePresenter.Item> resourceItems(ResourceKind kind) {
        if (project.draft() == null) return List.of();
        var ids = new TreeSet<String>();
        project.resources().catalog().keys().stream().filter(key -> key.kind() == kind).forEach(key -> ids.add(
                kind == ResourceKind.IMAGE ? MaterialPack.imageId(key, project.draft().namespace()) : key.id(project.draft().namespace())));
        if (kind == ResourceKind.VISUAL_ASSET) ClientServices.get().content().current().visualAssets().ids().stream()
                .filter(id -> !id.getNamespace().equals(project.draft().namespace())).forEach(id -> ids.add(id.toString()));
        return ids.stream().map(id -> new ChoicePresenter.Item(id, id)).toList();
    }
    private Button choice(String label, Supplier<String> value, Supplier<List<ChoicePresenter.Item>> items, Consumer<String> setter) {
        String expected = binding;
        var field = new EditorChoiceField(getContext(), choices, value, items, () -> accepts(expected), model::active,
                selected -> { if (!selected.equals(value.get())) setter.accept(selected); },
                selected -> selected.isEmpty() ? EditorWidgets.tr("no_selection") : selected);
        EditorWidgets.propertyRow(group, label, field.button(), false);
        bindings.add(field::refresh);
        return field.button();
    }
    private void action(LinearLayout row, String label, Runnable action, BooleanSupplier enabled) {
        String expected = binding;
        Button button = EditorWidgets.button(getContext(), label, () -> { if (accepts(expected)) action.run(); });
        row.addView(button); bindings.add(() -> EditorWidgets.enabled(button, model.active() && enabled.getAsBoolean()));
    }
    private static List<ChoicePresenter.Item> names(JsonObject data) {
        return data == null ? List.of() : data.keySet().stream().map(name -> new ChoicePresenter.Item(name, name)).toList();
    }
    private static List<ChoicePresenter.Item> items(String prefix, String... values) {
        return Arrays.stream(values).map(value -> new ChoicePresenter.Item(value, EditorWidgets.tr(prefix + value))).toList();
    }
    private void title(String key) {
        var section = new EditorPropertySection(getContext(), key, layout);
        addView(section, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        sections.add(section);
        group = section.body();
    }
    private void warning() { group.addView(EditorWidgets.compactParagraph(getContext(), "edit.invalid_object")); }
    private LinearLayout row() { var row = new EditorActionRow(getContext()); group.addView(row); return row; }
}
