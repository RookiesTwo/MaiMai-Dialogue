package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.*;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.*;
import net.minecraft.client.Minecraft;
import top.rookiestwo.maimai_dialogue_editor.document.AudioWorkspace;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import java.util.*;
import java.util.function.*;

/** Audio configuration follows the selected Dialogue, Speaker, Step or End. */
final class AudioPropertiesView extends LinearLayout {
    private record Binding(long project, AudioWorkspace.Target target, String mode) {}
    private final ProjectWorkspace project;
    private final AudioWorkspace model;
    private final ChoicePresenter choices;
    private final EditorLayoutState layout;
    private final EditorPreviewHost preview;
    private final List<Runnable> bindings = new ArrayList<>();
    private Binding binding;
    private boolean refreshing;
    private LinearLayout body;
    private EditorPropertySection section;
    private final Map<String, icyllis.modernui.view.View> fields = new HashMap<>();
    private long focusedIssue = -1;
    AudioPropertiesView(Context context, ProjectWorkspace project, ChoicePresenter choices, EditorLayoutState layout, EditorPreviewHost preview) {
        super(context); setOrientation(VERTICAL);
        this.project = project; model = project.audio(); this.choices = choices; this.layout = layout; this.preview = preview;
        preview.setAudioListener(this::refresh);
    }
    void refresh() {
        var target = model.target();
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
        if (issue == null || target == null || !target.key().equals(issue.resource()) || focusedIssue == project.issueFocusRevision()) return;
        String prefix = target.bgm() ? "bgm" : (target.step() == -2 ? "" : target.step() == -1 ? "end." : "steps[" + target.step() + "].") + "typewriter_sound";
        if (!issue.field().equals(prefix) && !issue.field().startsWith(prefix + ".")) return;
        focusedIssue = project.issueFocusRevision();
        var field = fields.getOrDefault(issue.field().substring(Math.min(issue.field().length(), prefix.length() + 1)), fields.get("mode"));
        layout.collapsedPropertySections.remove(target.bgm() ? "audio.bgm" : "audio.typing"); section.refresh();
        if (field != null) post(() -> {
            if (field.isAttachedToWindow() && project.focusedIssue() == issue) {
                field.requestFocus(); field.requestRectangleOnScreen(new icyllis.modernui.graphics.Rect(0, 0, field.getWidth(), field.getHeight()));
            }
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
        var button = EditorWidgets.button(getContext(), "", () -> {});
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        EditorWidgets.bindMetrics(button, () -> button.setPadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0,
                dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0));
        button.setOnClickListener(view -> {
            if (accepts(expected)) choices.show(button, items.get(), value.get(), selected -> {
                if (accepts(expected)) { project.endEdit(); setter.accept(selected); project.endEdit(); }
            });
        });
        EditorWidgets.propertyRow(body, label, button, false);
        fields.put(label.substring("audio.".length()), button);
        bindings.add(() -> {
            String text = items.get().stream().filter(item -> item.value().equals(value.get())).map(ChoicePresenter.Item::label)
                    .findFirst().orElse(EditorWidgets.tr("edit.unset"));
            button.setText(text + " ▾"); button.setTooltipText(text); EditorWidgets.enabled(button, model.active());
        });
    }
    private void sound() {
        Binding expected = binding;
        var input = EditorWidgets.compactInput(getContext(), model.sound(), ignored -> {}, () -> {});
        input.setTag(EditorWidgets.DEFERRED_INPUT_TAG, Boolean.TRUE);
        input.setOnFocusChangeListener((view, focused) -> {
            if (!focused && accepts(expected)) { model.sound(input.getText().toString()); project.endEdit(); }
        });
        EditorWidgets.propertyRow(body, "audio.sound", input, false);
        fields.put("sound", input);
        bindings.add(() -> {
            if (!input.isFocused() && !input.getText().toString().equals(model.sound())) input.setText(model.sound());
            input.setEnabled(model.active());
        });
        var button = EditorWidgets.button(getContext(), "edit.choose_resource", () -> {});
        button.setOnClickListener(view -> {
            if (!accepts(expected)) return;
            String namespace = project.draft().namespace();
            var ids = new TreeSet<String>(); var catalog = project.resources().catalog();
            catalog.keys().stream().filter(key -> key.kind() == ResourceKind.SOUND).map(catalog::displayName)
                    .filter(event -> !event.isBlank()).forEach(event -> ids.add(namespace + ":" + event));
            // Snapshot the sound registry on its owning thread; no resource list or reload is requested.
            Minecraft.getInstance().execute(() -> {
                var external = Minecraft.getInstance().getSoundManager().getAvailableSounds().stream()
                        .filter(id -> !id.getNamespace().equals(namespace)).map(Object::toString).sorted().toList();
                Core.getUiHandler().post(() -> {
                    if (!accepts(expected)) return;
                    var items = new ArrayList<ChoicePresenter.Item>();
                    ids.forEach(id -> items.add(new ChoicePresenter.Item(id, id)));
                    external.forEach(id -> items.add(new ChoicePresenter.Item(id, id)));
                    choices.showSearchable(button, items, model.sound(), selected -> {
                        if (accepts(expected)) { project.endEdit(); model.sound(selected); project.endEdit(); }
                    });
                });
            });
        });
        EditorWidgets.propertyRow(body, null, button, false);
        bindings.add(() -> EditorWidgets.enabled(button, model.active()));
    }
    private void number(AudioWorkspace.Number field) {
        Binding expected = binding;
        var control = new EditorNumberField(getContext(), field.control(), () -> model.number(field), raw -> model.number(field, raw),
                () -> accepts(expected), project::endEdit, () -> model.beginGesture(field), "audio." + field.name(), field.quickMaximum());
        EditorWidgets.propertyRow(body, "audio." + field.name(), control, false);
        fields.put(field.name(), control);
        bindings.add(() -> control.refresh(model.active()));
    }
    private void audition() {
        Binding expected = binding;
        var row = new LinearLayout(getContext()); body.addView(row);
        var play = EditorWidgets.button(getContext(), "audio.audition", () -> { if (accepts(expected)) preview.audition(); });
        var stop = EditorWidgets.button(getContext(), "preview.stop", preview::stopAudition);
        for (var button : List.of(play, stop)) {
            row.addView(button);
            EditorWidgets.bindMetrics(button, () -> button.setLayoutParams(new LayoutParams(0, dp(EditorWidgets.COMPACT_CONTROL_DP), 1)));
        }
        var error = EditorWidgets.compactParagraph(getContext(), ""); error.setTextColor(EditorWidgets.ERROR); body.addView(error);
        bindings.add(() -> {
            EditorWidgets.enabled(play, model.active()); EditorWidgets.enabled(stop, preview.auditioning());
            error.setText(preview.auditionError()); error.setVisibility(preview.auditionError().isEmpty() ? GONE : VISIBLE);
        });
    }
}
