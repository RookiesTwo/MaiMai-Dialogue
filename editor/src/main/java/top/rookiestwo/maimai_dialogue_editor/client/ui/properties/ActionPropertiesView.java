package top.rookiestwo.maimai_dialogue_editor.client.ui.properties;

import top.rookiestwo.maimai_dialogue_editor.client.preview.EditorPreviewHost;
import top.rookiestwo.maimai_dialogue_editor.client.ui.preview.EditorActionKeyframes;


import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.ChoicePresenter;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorChoiceField;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorNumberField;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorPropertySection;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorTextBinding;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorTextField;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.PropertySectionState;

import com.google.gson.*;
import icyllis.modernui.core.*;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;
import top.rookiestwo.maimai_dialogue_editor.client.EditorContentPreparation;
import top.rookiestwo.maimai_dialogue_editor.client.EditorResourceCandidates;
import top.rookiestwo.maimai_dialogue_editor.document.*;
import top.rookiestwo.maimai_dialogue_editor.preview.ActionSceneContext;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import java.util.*;
import java.util.function.*;
import static top.rookiestwo.maimai_dialogue_editor.document.DialogueDraft.*;

/** Shared inspector for an Action resource or the selected Step/End call. */
final class ActionPropertiesView extends LinearLayout {
    private static final List<String> PRESETS = List.of("custom", "move_x", "move_y", "scale", "fade_in", "fade_out", "show", "hide", "sound", "stop_bgm");
    private record Binding(long project, ActionWorkspace.Context context, int call, String shape) {}
    private final ProjectWorkspace project;
    private final ActionWorkspace model;
    private final ChoicePresenter choices;
    private final PropertySectionState layout;
    private final EditorPreviewHost preview;
    private final EditorActionKeyframes keyframes;
    private final List<Runnable> keyframeBindings = new ArrayList<>();
    private final Runnable timelineListener = () -> keyframeBindings.forEach(Runnable::run);
    private final List<Runnable> bindings = new ArrayList<>();
    private final Map<String, View> fields = new HashMap<>();
    private Binding binding;
    private boolean refreshing;
    private String sceneSignature = "", contextError = "", conversionError = "";
    private long metadataRequest, conversionRequest;
    private final EditorIssueFocus issueFocus;
    private ActionSceneContext scene;
    ActionPropertiesView(Context context, ProjectWorkspace project, ChoicePresenter choices, PropertySectionState layout, EditorPreviewHost preview) {
        super(context); setOrientation(VERTICAL); this.project = project; model = project.actions(); this.choices = choices; this.layout = layout;
        this.preview = preview; keyframes = new EditorActionKeyframes(project, preview, choices);
        issueFocus = new EditorIssueFocus(this, project);
    }
    void refresh() {
        var context = model.inspecting() ? model.context() : null;
        var next = new Binding(project.projectGeneration(), context, model.selected(), shape());
        prepareScene();
        refreshing = true;
        try {
            setVisibility(context == null ? GONE : VISIBLE);
            if (!next.equals(binding)) {
                clearFocus(); binding = next; removeAllViews(); bindings.clear(); keyframeBindings.clear(); fields.clear(); conversionError = ""; ++conversionRequest;
                if (context != null) build();
            }
            bindings.forEach(Runnable::run);
        } finally { refreshing = false; }
        focusIssue();
    }
    private String shape() {
        var data = model.definition(); if (data == null) return model.mode() + "/invalid";
        var parts = new ArrayList<String>(); parts.add(model.mode()); parts.add(Boolean.toString(model.audioOnly()));
        for (String key : ActionFields.COMPONENTS) {
            var value = data.get(key); parts.add(key + ":" + (value == null ? "absent" : value instanceof JsonArray array ? array.size() : value instanceof JsonObject ? "object" : "invalid"));
        }
        parts.add(model.text(false, "bgm.type", ""));
        parts.add(Boolean.toString(model.context().standalone() && model.previewContext().scene().isEmpty())); return String.join("/", parts);
    }
    private boolean accepts(Binding expected) {
        return !refreshing && isAttachedToWindow() && expected.equals(binding) && model.active()
                && expected.project() == project.projectGeneration() && Objects.equals(expected.context(), model.context()) && expected.call() == model.selected();
    }
    private void build() {
        var context = model.context();
        var error = EditorWidgets.compactParagraph(getContext(), ""); error.setTextColor(EditorWidgets.ERROR); addView(error);
        bindings.add(() -> {
            String text = conversionError;
            if (model.definition() != null) text = String.join("\n", ActionFields.errors(model.definition()).entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue()).toList());
            if (!contextError.isEmpty()) text = contextError + (text.isEmpty() ? "" : "\n" + text);
            error.setText(text); error.setVisibility(text.isEmpty() ? GONE : VISIBLE);
        });
        if (!context.standalone()) {
            if (model.call() == null) { addView(EditorWidgets.compactParagraph(getContext(), model.selected() < 0 ? "action.empty" : "edit.invalid_object")); return; }
            var body = section("action.call");
            choice(body, "action.source", () -> model.mode(), () -> items("action.source.", "inline", "reference"), this::changeMode);
            if (model.mode().equals("reference")) {
                var expected = binding;
                Button choose = EditorWidgets.button(getContext(), "edit.choose_resource", () -> {});
                choose.setOnClickListener(view -> {
                    if (!accepts(expected)) return;
                    EditorResourceCandidates.references(project, ResourceKind.ACTION, EditorResourceCandidates.Source.PROJECT_AND_EXTERNAL,
                            () -> accepts(expected), items -> choices.showResources(choose, items, model.text(true, "action.id", ""),
                                    id -> { if (accepts(expected)) discrete(() -> model.set(true, "action.id", new JsonPrimitive(id))); }));
                });
                field(body, "action.reference", true, "action.id", choose);
                bindings.add(() -> EditorWidgets.enabled(choose, model.active()));
            }
            choice(body, "action.target", () -> model.text(true, "target", ""), () -> {
                var targets = new ArrayList<ChoicePresenter.Item>(); targets.add(new ChoicePresenter.Item("", EditorWidgets.tr("action.no_target")));
                for (String target : scene == null ? List.of("dialogue") : scene.targets()) targets.add(new ChoicePresenter.Item(target,
                        List.of("dialogue", "background").contains(target) ? EditorWidgets.tr("action.target." + target) : target));
                return targets;
            }, target -> model.set(true, "target", target.isEmpty() ? null : new JsonPrimitive(target)));
            number(body, "action.delay_ms", true, ActionFields.time("delay_ms", 0));
            if (model.definition() != null) {
                var expected = binding;
                var extract = EditorWidgets.button(getContext(), "browser.extract", () -> { if (accepts(expected)) model.extract(); });
                EditorWidgets.propertyRow(body, null, extract, false);
                bindings.add(() -> EditorWidgets.enabled(extract, model.active()));
            }
        }
        if (model.definition() == null) return;
        var body = section("action.settings");
        var expected = binding; var preset = EditorWidgets.button(getContext(), "action.apply_preset", () -> {});
        preset.setOnClickListener(view -> choices.showMenu(preset, PRESETS.stream().map(id -> new ChoicePresenter.Item(id, EditorWidgets.tr("action.preset." + id))).toList(), "",
                id -> { if (accepts(expected)) model.applyPreset(id); }));
        EditorWidgets.propertyRow(body, null, preset, false);
        bindings.add(() -> EditorWidgets.enabled(preset, model.active()));
        if (!model.audioOnly()) {
            number(body, "action.duration_ms", false, ActionFields.time("duration_ms", model.audioOnly() ? 0 : 300));
            choice(body, "action.easing", () -> model.text(false, "easing", "linear"), () -> items("action.easing.", "linear", "ease_in", "ease_out", "ease_in_out"),
                    value -> model.set(false, "easing", value.equals("linear") ? null : new JsonPrimitive(value)));
            choice(body, "action.blocking", () -> model.text(false, "blocking", Boolean.toString(!model.audioOnly())), () -> items("audio.", "yes", "no").stream()
                            .map(item -> new ChoicePresenter.Item(item.value().equals("yes") ? "true" : "false", item.label())).toList(),
                    value -> model.set(false, "blocking", new JsonPrimitive(Boolean.parseBoolean(value))));
        }
        addComponentButton();
        for (String field : ActionFields.COMPONENTS) {
            if (!model.definition().has(field)) continue;
            if (ActionFields.TRACKS.contains(field)) track(field);
            else if (field.equals("variant") || field.equals("visible")) change(field);
            else audio(field);
        }
    }
    private LinearLayout section(String key) {
        var section = new EditorPropertySection(getContext(), key, layout); addView(section); bindings.add(section::refresh); return section.body();
    }
    private void addComponentButton() {
        var expected = binding;
        var add = EditorWidgets.button(getContext(), "action.add_component", () -> {});
        add.setOnClickListener(view -> {
            if (!accepts(expected)) return;
            var available = ActionFields.COMPONENTS.stream().filter(field -> !model.definition().has(field))
                    .map(field -> new ChoicePresenter.Item(field, EditorWidgets.tr("action." + field))).toList();
            choices.showMenu(add, available, "", field -> {
                if (!accepts(expected)) return;
                layout.collapsed("action." + field, false);
                discrete(() -> model.enabled(field, true));
            });
        });
        EditorWidgets.propertyRow(this, null, add, false); fields.put("action.add_component", add);
        bindings.add(() -> EditorWidgets.enabled(add, model.active()
                && ActionFields.COMPONENTS.stream().anyMatch(field -> !model.definition().has(field))));
    }
    private LinearLayout component(String field) {
        var expected = binding;
        var remove = EditorWidgets.icon(getContext(), "−", "action.remove_component", () -> {
            if (accepts(expected)) discrete(() -> model.enabled(field, false));
        });
        EditorWidgets.bindMetrics(remove, () -> remove.setLayoutParams(EditorWidgets.squareIconParams(remove)));
        var section = new EditorPropertySection(getContext(), "action." + field, layout, remove);
        addView(section); bindings.add(section::refresh);
        bindings.add(() -> EditorWidgets.enabled(remove, model.active()));
        return section.body();
    }
    private void track(String track) {
        var body = component(track);
        var frames = array(model.definition(), track); if (frames == null) return;
        var expected = binding;
        for (int i = 0; i < frames.size(); i++) {
            int index = i; var header = new LinearLayout(getContext()); header.setGravity(Gravity.CENTER_VERTICAL); body.addView(header);
            var label = EditorWidgets.compactParagraph(getContext(), ""); label.setText(EditorWidgets.tr("action.keyframe") + " " + (i + 1));
            header.addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            var remove = EditorWidgets.icon(getContext(), "−", "browser.delete", () -> { if (accepts(expected)) model.deleteFrame(track, index); });
            header.addView(remove); EditorWidgets.bindMetrics(remove, () -> remove.setLayoutParams(EditorWidgets.squareIconParams(remove)));
            bindings.add(() -> EditorWidgets.enabled(remove, model.active()));
            number(body, "action.at", false, ActionFields.fraction(track + "." + index + ".at", (index + 1f) / frames.size(), true));
            number(body, "action.delta", false, ActionFields.delta(track + "." + index + ".value", track));
        }
        var add = EditorWidgets.button(getContext(), "action.add_keyframe", () -> {});
        add.setOnClickListener(view -> { if (accepts(expected)) keyframes.addAtPlayhead(add, track); });
        EditorWidgets.propertyRow(body, null, add, false);
        Runnable update = () -> {
            boolean enabled = model.active() && keyframes.canAdd(track);
            if (add.isEnabled() != enabled) EditorWidgets.enabled(add, enabled);
            add.setTooltipText(keyframes.tooltip(track));
        };
        bindings.add(update); keyframeBindings.add(update);
    }
    private void change(String field) {
        var body = component(field); if (!(model.definition().get(field) instanceof JsonObject)) return;
        number(body, "action.at", false, ActionFields.fraction(field + ".at", 0, false));
        if (field.equals("visible")) choice(body, "action.visible_value", () -> model.text(false, "visible.value", "true"), this::booleans,
                value -> model.set(false, "visible.value", new JsonPrimitive(Boolean.parseBoolean(value))));
        else if (model.context().standalone() && model.previewContext().scene().isEmpty()) field(body, "action.variant_value", false, "variant.value");
        else choice(body, "action.variant_value", () -> model.text(false, "variant.value", ""),
                () -> scene == null ? List.of() : scene.variants(model.context().standalone() ? model.previewContext().target() : model.text(true, "target", ""))
                        .stream().map(value -> new ChoicePresenter.Item(value, value)).toList(),
                value -> model.set(false, "variant.value", new JsonPrimitive(value)));
    }
    private void audio(String field) {
        var body = component(field); if (!(model.definition().get(field) instanceof JsonObject)) return;
        if (field.equals("bgm")) choice(body, "audio.mode", () -> model.text(false, "bgm.type", "play"), () -> items("audio.bgm.", "play", "stop"), model::bgmMode);
        if (!field.equals("bgm") || model.text(false, "bgm.type", "play").equals("play")) {
            var expected = binding; var choose = EditorWidgets.button(getContext(), "edit.choose_resource", () -> {});
            choose.setOnClickListener(view -> EditorSoundChoices.show(project, choices, choose, model.text(false, field + ".sound", ""), () -> accepts(expected),
                    value -> discrete(() -> model.set(false, field + ".sound", new JsonPrimitive(value)))));
            field(body, "audio.sound", false, field + ".sound", choose);
            bindings.add(() -> EditorWidgets.enabled(choose, model.active()));
            number(body, "audio.volume", false, ActionFields.volume(field + ".volume"));
            if (field.equals("sound")) number(body, "audio.pitch", false, ActionFields.pitch("sound.pitch"));
            else choice(body, "audio.loop", () -> model.text(false, "bgm.loop", "true"), this::booleans,
                    value -> model.set(false, "bgm.loop", value.equals("true") ? null : new JsonPrimitive(false)));
        }
        if (field.equals("bgm")) number(body, "audio.fade_ms", false, ActionFields.time("bgm.fade_ms", 500));
    }
    private void field(LinearLayout body, String label, boolean call, String path) {
        field(body, label, call, path, null);
    }
    private void field(LinearLayout body, String label, boolean call, String path, Button picker) {
        var expected = binding;
        var control = new EditorTextField(getContext(), EditorTextBinding.plain(() -> model.text(call, path, ""),
                () -> accepts(expected), text -> discrete(() -> model.set(call, path, new JsonPrimitive(text.strip()))),
                () -> {}).commitUnchanged());
        var input = control.input();
        if (picker == null) EditorWidgets.propertyRow(body, label, input, false);
        else EditorWidgets.referenceRow(body, label, input, picker);
        fields.put(path, input);
        bindings.add(() -> control.refresh(model.active()));
    }
    private void number(LinearLayout body, String label, boolean call, ActionFields.Number field) {
        var expected = binding;
        var slider = Float.isFinite(field.quickMax()) ? EditorNumberField.Slider.range(field.quickMin(), field.quickMax(), field.integer()) : null;
        var control = new EditorNumberField(getContext(), field.constraints(), slider, () -> model.text(call, field.path(), field.integer() ? Integer.toString((int) field.fallback()) : Float.toString(field.fallback())),
                raw -> model.number(call, field, raw), () -> accepts(expected), project::endEdit, () -> model.beginGesture(call, field), label);
        EditorWidgets.propertyRow(body, label, control, false); fields.put(field.path(), control); bindings.add(() -> control.refresh(model.active()));
    }
    private void choice(LinearLayout body, String label, Supplier<String> value, Supplier<List<ChoicePresenter.Item>> items, Consumer<String> setter) {
        var expected = binding;
        var field = new EditorChoiceField(getContext(), choices, value, items, () -> accepts(expected), model::active,
                selected -> discrete(() -> setter.accept(selected)),
                selected -> selected.isBlank() ? EditorWidgets.tr("edit.unset") : selected);
        EditorWidgets.propertyRow(body, label, field.button(), false);
        fields.put(label, field.button());
        bindings.add(field::refresh);
    }
    private void discrete(Runnable edit) { project.endEdit(); edit.run(); project.endEdit(); }
    private List<ChoicePresenter.Item> booleans() { return List.of(new ChoicePresenter.Item("true", EditorWidgets.tr("audio.yes")), new ChoicePresenter.Item("false", EditorWidgets.tr("audio.no"))); }
    private static List<ChoicePresenter.Item> items(String prefix, String... values) { return Arrays.stream(values).map(value -> new ChoicePresenter.Item(value, EditorWidgets.tr(prefix + value))).toList(); }
    private void changeMode(String mode) {
        if (!mode.equals("inline") || model.text(true, "action.id", "").isBlank()) { model.mode(mode, null); return; }
        var expected = binding; var captured = project.draft(); String reference = model.text(true, "action.id", ""); long request = ++conversionRequest;
        EditorContentPreparation.prepare(project, external -> ActionSceneContext.reference(captured, reference, external), (definition, failure) -> {
            if (!accepts(expected) || captured != project.draft() || request != conversionRequest) return;
            if (failure == null) model.mode(mode, definition); else { conversionError = String.valueOf(failure.getMessage()); refresh(); }
        });
    }

    private void prepareScene() {
        var context = model.context();
        String id = !model.inspecting() ? "" : context.standalone() ? model.previewContext().scene() : ActionFields.text(model.data(), "scene", "");
        String signature = id.isEmpty() ? "" : project.projectGeneration() + "/" + ActionSceneContext.signature(project.draft(), id);
        if (signature.equals(sceneSignature)) return;
        sceneSignature = signature; long request = ++metadataRequest; scene = null; contextError = "";
        if (id.isEmpty()) return;
        var captured = project.draft();
        EditorContentPreparation.prepare(project, external -> ActionSceneContext.prepare(captured, id, external), (result, failure) -> {
            if (request != metadataRequest || !isAttachedToWindow() || !signature.equals(sceneSignature)) return;
            if (failure == null) scene = result; else contextError = String.valueOf(failure.getMessage());
            refresh();
        });
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); preview.addTimelineObserver(timelineListener); timelineListener.run();
    }
    @Override protected void onDetachedFromWindow() {
        preview.removeTimelineObserver(timelineListener);
        ++metadataRequest; ++conversionRequest; sceneSignature = ""; scene = null;
        super.onDetachedFromWindow();
    }

    private void focusIssue() {
        var issue = project.focusedIssue(); var context = model.context();
        if (issue == null || !model.inspecting() || !context.resource().equals(issue.resource()) || !issueFocus.pending()) return;
        String path = issue.field();
        if (!context.standalone()) {
            String prefix = (context.step() == -1 ? "end" : "steps[" + context.step() + "]") + ".actions[" + model.selected() + "]";
            if (!path.startsWith(prefix)) return;
            path = path.substring(prefix.length()).replaceFirst("^\\.", "").replaceFirst("^action\\.action\\.", "");
        }
        String normalized = path.replaceAll("\\[(\\d+)]", ".$1"); var field = fields.get(normalized);
        if (field == null) field = fields.get(normalized.startsWith("target") ? "action.target" : normalized.startsWith("action") ? "action.source" : "action.add_component");
        issueFocus.reveal(issue, field, () -> {
            layout.expandPrefix("action."); bindings.forEach(Runnable::run);
        });
    }
}
