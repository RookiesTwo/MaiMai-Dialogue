package top.rookiestwo.maimai_dialogue_editor.client.ui.properties;

import top.rookiestwo.maimai_dialogue_editor.client.ui.EditorPreviewHost;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.ChoicePresenter;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorActionRow;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorChoiceField;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorNumberField;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorPropertySection;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorTextBinding;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorTextField;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.PropertySectionState;

import icyllis.modernui.core.*;
import icyllis.modernui.widget.*;
import top.rookiestwo.maimai_dialogue_editor.document.AudioWorkspace;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import java.util.*;
import java.util.function.*;

/** Audio configuration follows the selected Dialogue, Speaker, Step or End. */
final class AudioPropertiesView extends LinearLayout {
    private record Binding(long project, AudioWorkspace.Target target, String mode) {}
    private final ProjectWorkspace project;
    private final AudioWorkspace model;
    private final ChoicePresenter choices;
    private final PropertySectionState layout;
    private final EditorPreviewHost preview;
    private final List<Runnable> bindings = new ArrayList<>();
    private Binding binding;
    private boolean refreshing;
    private LinearLayout body;
    private EditorPropertySection section;
    private final Map<String, icyllis.modernui.view.View> fields = new HashMap<>();
    private final EditorIssueFocus issueFocus;
    AudioPropertiesView(Context context, ProjectWorkspace project, ChoicePresenter choices, PropertySectionState layout, EditorPreviewHost preview) {
        super(context); setOrientation(VERTICAL);
        this.project = project; model = project.audio(); this.choices = choices; this.layout = layout; this.preview = preview;
        issueFocus = new EditorIssueFocus(this, project);
        preview.setAudioListener(this::refresh);
    }
    void refresh() {
        var target = project.actions().inspecting() ? null : model.target();
        var next = new Binding(project.projectGeneration(), target, model.mode());
        refreshing = true;
        try {
            setVisibility(target == null ? GONE : VISIBLE);
            if (!next.equals(binding)) { clearFocus(); binding = next; removeAllViews(); bindings.clear(); fields.clear(); if (target != null) build(); }
            bindings.forEach(Runnable::run);
        } finally { refreshing = false; }
        focusIssue();
    }
    private void focusIssue() {
        var issue = project.focusedIssue(); var target = binding.target();
        if (issue == null || target == null || !target.key().equals(issue.resource()) || !issueFocus.pending()) return;
        String prefix = target.bgm() ? "bgm" : (target.step() == -2 ? "" : target.step() == -1 ? "end." : "steps[" + target.step() + "].") + "typewriter_sound";
        if (!issue.field().equals(prefix) && !issue.field().startsWith(prefix + ".")) return;
        var field = fields.getOrDefault(issue.field().substring(Math.min(issue.field().length(), prefix.length() + 1)), fields.get("mode"));
        issueFocus.reveal(issue, field, () -> {
            layout.collapsed(target.bgm() ? "audio.bgm" : "audio.typing", false); section.refresh();
        });
    }
    private boolean accepts(Binding expected) {
        return !refreshing && isAttachedToWindow() && Objects.equals(expected, binding) && model.active()
                && project.projectGeneration() == expected.project() && Objects.equals(model.target(), expected.target());
    }
    private void build() {
        section = new EditorPropertySection(getContext(), binding.target().bgm() ? "audio.bgm" : "audio.typing", layout);
        addView(section); body = section.body(); bindings.add(section::refresh);
        boolean bgm = binding.target().bgm();
        var modes = bgm ? List.of("inherit", "play", "stop") : List.of("inherit", "custom", "silent");
        choice("audio.mode", model::mode, () -> modes.stream().map(value -> new ChoicePresenter.Item(value,
                EditorWidgets.tr("audio." + (bgm ? "bgm." : binding.target().step() == -2 ? "speaker." : "step.") + value))).toList(), model::mode);
        String mode = model.mode();
        if (mode.equals("play") || mode.equals("custom")) {
            sound();
            number(bgm ? AudioWorkspace.BGM_VOLUME : AudioWorkspace.TYPING_VOLUME);
            if (bgm) choice("audio.loop", () -> model.field("loop", "true"), () -> List.of(
                    new ChoicePresenter.Item("true", EditorWidgets.tr("audio.yes")), new ChoicePresenter.Item("false", EditorWidgets.tr("audio.no"))),
                    value -> model.loop(Boolean.parseBoolean(value)));
            else { number(AudioWorkspace.PITCH); number(AudioWorkspace.INTERVAL); }
        }
        if (bgm && (mode.equals("play") || mode.equals("stop"))) number(AudioWorkspace.FADE);
        if (mode.equals("play") || mode.equals("custom") || !bgm && binding.target().step() == -2 && mode.equals("inherit")) audition();
    }
    private void choice(String label, Supplier<String> value, Supplier<List<ChoicePresenter.Item>> items, Consumer<String> setter) {
        Binding expected = binding;
        var field = new EditorChoiceField(getContext(), choices, value, items, () -> accepts(expected), model::active,
                selected -> { project.endEdit(); setter.accept(selected); project.endEdit(); },
                selected -> EditorWidgets.tr("edit.unset"));
        EditorWidgets.propertyRow(body, label, field.button(), false);
        fields.put(label.substring("audio.".length()), field.button());
        bindings.add(field::refresh);
    }
    private void sound() {
        Binding expected = binding;
        var control = new EditorTextField(getContext(), EditorTextBinding.plain(model::sound,
                () -> accepts(expected), model::sound, project::endEdit).commitUnchanged());
        var input = control.input();
        fields.put("sound", input);
        bindings.add(() -> control.refresh(model.active()));
        var button = EditorWidgets.button(getContext(), "edit.choose_resource", () -> {});
        button.setOnClickListener(view -> {
            if (!accepts(expected)) return;
            EditorSoundChoices.show(project, choices, button, model.sound(), () -> accepts(expected), selected -> {
                project.endEdit(); model.sound(selected); project.endEdit();
            });
        });
        EditorWidgets.referenceRow(body, "audio.sound", input, button);
        bindings.add(() -> EditorWidgets.enabled(button, model.active()));
    }
    private void number(AudioWorkspace.Number field) {
        Binding expected = binding;
        var slider = EditorNumberField.Slider.range(field.minimum(), field.quickMaximum(), field.integer());
        var control = new EditorNumberField(getContext(), field.constraints(), slider, () -> model.number(field), raw -> model.number(field, raw),
                () -> accepts(expected), project::endEdit, () -> model.beginGesture(field), "audio." + field.name());
        EditorWidgets.propertyRow(body, "audio." + field.name(), control, false);
        fields.put(field.name(), control);
        bindings.add(() -> control.refresh(model.active()));
    }
    private void audition() {
        Binding expected = binding;
        var row = new EditorActionRow(getContext()); body.addView(row);
        var play = EditorWidgets.button(getContext(), "audio.audition", () -> { if (accepts(expected)) preview.audition(); });
        var stop = EditorWidgets.button(getContext(), "preview.stop", preview::stopAudition);
        for (var button : List.of(play, stop)) row.addView(button);
        var error = EditorWidgets.compactParagraph(getContext(), ""); error.setTextColor(EditorWidgets.ERROR); body.addView(error);
        bindings.add(() -> {
            EditorWidgets.enabled(play, model.active()); EditorWidgets.enabled(stop, preview.auditioning());
            error.setText(preview.auditionError()); error.setVisibility(preview.auditionError().isEmpty() ? GONE : VISIBLE);
        });
    }
}
