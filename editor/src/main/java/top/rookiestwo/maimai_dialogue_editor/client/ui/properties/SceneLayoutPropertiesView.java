package top.rookiestwo.maimai_dialogue_editor.client.ui.properties;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.ChoicePresenter;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorChoiceField;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorNumberField;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorPropertySection;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorTextBinding;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorTextField;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.PropertySectionState;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.*;
import top.rookiestwo.maimai_dialogue_editor.client.EditorResourceCandidates;
import top.rookiestwo.maimai_dialogue.presentation.DialogueBoxLayout;
import top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualAnchor;
import top.rookiestwo.maimai_dialogue_editor.document.SceneWorkspace;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.util.*;
import java.util.function.*;

/** Theme and dialogue-box configuration belongs to the Scene resource. */
final class SceneLayoutPropertiesView extends LinearLayout {
    private final ProjectWorkspace project;
    private final SceneWorkspace model;
    private final ChoicePresenter choices;
    private final PropertySectionState layout;
    private final LinearLayout form;
    private final List<Runnable> bindings = new ArrayList<>();
    private final List<EditorPropertySection> sections = new ArrayList<>();
    private LinearLayout group;
    private String binding = "";
    private boolean refreshing;

    SceneLayoutPropertiesView(Context context, ProjectWorkspace project, ChoicePresenter choices, PropertySectionState layout) {
        super(context); setOrientation(VERTICAL);
        this.project = project; model = project.scenes(); this.choices = choices; this.layout = layout;
        form = new LinearLayout(context); form.setOrientation(VERTICAL); addView(form);
    }
    void refresh() {
        var state = model.snapshot();
        String next = !model.acceptsResource(state.key()) ? "" : project.projectGeneration() + "/" + state.key()
                + "/" + (state.data() == null ? "invalid" : model.data().has("dialogue_box") && model.box() == null);
        refreshing = true;
        try {
            if (!binding.equals(next)) {
                binding = next; form.clearFocus(); form.removeAllViews(); bindings.clear(); sections.clear(); build();
            }
            bindings.forEach(Runnable::run); sections.forEach(EditorPropertySection::refresh);
        } finally { refreshing = false; }
    }
    private boolean accepts(String expected) { return !refreshing && isAttachedToWindow() && binding.equals(expected) && model.active(); }
    private String value(String field, String fallback) { return SceneWorkspace.text(model.data(), field, fallback); }
    private void build() {
        if (binding.isEmpty()) return;
        if (model.data() == null) { form.addView(EditorWidgets.compactParagraph(getContext(), "edit.invalid_object")); return; }
        section("scene.settings");
        reference("scene.theme", "theme", ResourceKind.THEME, SceneDefinition.DEFAULT_THEME_ID.toString());
        section("scene.box");
        if (model.data().has("dialogue_box") && model.box() == null) group.addView(EditorWidgets.compactParagraph(getContext(), "edit.invalid_object"));
        for (var field : SceneWorkspace.BOX_NUMBERS) {
            String expected = binding;
            var input = new EditorNumberField(getContext(), field, EditorNumberField.Slider.range(field),
                    () -> SceneWorkspace.text(model.box(), field.name(), Float.toString(field.fallback())),
                    value -> model.setBoxNumber(field, value), () -> accepts(expected), project::endEdit,
                    () -> model.beginNumberDrag(SceneWorkspace.Part.BOX, field), "scene." + field.name());
            EditorWidgets.propertyRow(group, "scene.box." + field.name(), input, false);
            bindings.add(() -> input.refresh(model.active()));
        }
        choice("scene.anchor", () -> SceneWorkspace.text(model.box(), "anchor", DialogueBoxLayout.DEFAULT.anchor().serializedName()),
                () -> Arrays.stream(VisualAnchor.values()).map(anchor -> new ChoicePresenter.Item(anchor.serializedName(),
                        EditorWidgets.tr("scene.anchor." + anchor.serializedName()))).toList(), model::setBoxAnchor);
        String expected = binding;
        var reset = EditorWidgets.button(getContext(), "scene.reset_box", () -> { if (accepts(expected)) model.resetBox(); });
        EditorWidgets.propertyRow(group, null, reset, false);
        bindings.add(() -> EditorWidgets.enabled(reset, model.active() && model.data().has("dialogue_box")));
    }
    private void reference(String label, String field, ResourceKind kind, String fallback) {
        String expected = binding;
        var control = new EditorTextField(getContext(), EditorTextBinding.plain(() -> value(field, fallback),
                () -> accepts(expected), model::setTheme, project::endEdit));
        var choose = EditorWidgets.button(getContext(), "edit.choose_resource", () -> {});
        choose.setOnClickListener(view -> {
            if (!accepts(expected)) return;
            EditorResourceCandidates.references(project, kind, EditorResourceCandidates.Source.PROJECT_AND_EXTERNAL,
                    () -> accepts(expected), candidates -> {
                        var items = new ArrayList<ResourceCandidates.Item>();
                        if (kind == ResourceKind.SCENE) items.add(new ResourceCandidates.Item("", EditorWidgets.tr("scene.no_scene")));
                        items.addAll(candidates);
                        choices.showResources(choose, items, value(field, fallback), selected -> {
                            if (accepts(expected) && !selected.equals(value(field, fallback))) {
                                project.endEdit(); model.setTheme(selected); project.endEdit();
                            }
                        });
                    });
        });
        EditorWidgets.referenceRow(group, label, control.input(), choose);
        bindings.add(() -> {
            control.refresh(model.active());
            EditorWidgets.enabled(choose, model.active());
        });
    }
    private void choice(String label, Supplier<String> value, Supplier<List<ChoicePresenter.Item>> items, Consumer<String> setter) {
        String expected = binding;
        var field = new EditorChoiceField(getContext(), choices, value, items, () -> accepts(expected), model::active,
                selected -> {
                    if (!selected.equals(value.get())) { project.endEdit(); setter.accept(selected); project.endEdit(); }
                }, selected -> selected);
        EditorWidgets.propertyRow(group, label, field.button(), false);
        bindings.add(field::refresh);
    }
    private void section(String key) {
        var section = new EditorPropertySection(getContext(), key, layout); form.addView(section); sections.add(section); group = section.body();
    }
}
