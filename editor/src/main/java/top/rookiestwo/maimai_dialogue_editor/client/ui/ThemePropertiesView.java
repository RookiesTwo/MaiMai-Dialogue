package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.*;
import top.rookiestwo.maimai_dialogue_editor.document.*;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import java.util.*;

// 按运行时的五个配置组编辑主题，保持控件实例和输入焦点稳定。
final class ThemePropertiesView extends LinearLayout {
    private final ProjectWorkspace project;
    private final ThemeWorkspace model;
    private final ChoicePresenter choices;
    private final EditorLayoutState layout;
    private final List<Runnable> bindings = new ArrayList<>();
    private String binding = "";
    private boolean refreshing;
    ThemePropertiesView(Context context, ProjectWorkspace project, ChoicePresenter choices, EditorLayoutState layout) {
        super(context); setOrientation(VERTICAL);
        this.project = project; model = project.themes(); this.choices = choices; this.layout = layout;
    }
    void refresh() {
        var state = model.snapshot();
        String next = state.key() == null || state.key().kind() != ResourceKind.THEME ? ""
                : project.projectGeneration() + "/" + state.key() + "/" + (state.data() == null);
        refreshing = true;
        try {
            if (!binding.equals(next)) { binding = next; clearFocus(); removeAllViews(); bindings.clear(); build(); }
            bindings.forEach(Runnable::run);
        } finally { refreshing = false; }
    }
    private boolean accepts(String expected) { return !refreshing && isAttachedToWindow() && binding.equals(expected) && model.active(); }
    private void build() {
        if (binding.isEmpty()) return;
        if (model.snapshot().data() == null) { addView(EditorWidgets.compactParagraph(getContext(), "edit.invalid_object")); return; }
        String expected = binding;
        for (String group : ThemeFields.GROUPS) {
            var section = new EditorPropertySection(getContext(), "theme.group." + group, layout);
            addView(section); bindings.add(section::refresh);
            var body = section.body();
            for (var field : ThemeFields.ALL) {
                if (!field.group().equals(group)) continue;
                if (field.color()) {
                    var control = new EditorColorField(getContext(), () -> model.value(field), value -> model.set(field, value),
                            () -> accepts(expected), project::endEdit, choices, () -> model.beginGesture(field));
                    EditorWidgets.propertyRow(body, field.label(), control, false);
                    bindings.add(() -> control.refresh(model.active()));
                } else {
                    var control = new EditorNumberField(getContext(), field.number(), () -> model.value(field), value -> model.set(field, value),
                            () -> accepts(expected), project::endEdit, () -> model.beginGesture(field), field.label());
                    EditorWidgets.propertyRow(body, field.label(), control, false);
                    bindings.add(() -> control.refresh(model.active()));
                }
            }
            var reset = EditorWidgets.button(getContext(), "theme.reset_group", () -> { if (accepts(expected)) model.resetGroup(group); });
            EditorWidgets.propertyRow(body, null, reset, false);
            bindings.add(() -> EditorWidgets.enabled(reset, model.active() && model.snapshot().data().has(group)));
        }
    }
}
