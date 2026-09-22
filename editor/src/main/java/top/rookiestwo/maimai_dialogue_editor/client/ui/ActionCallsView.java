package top.rookiestwo.maimai_dialogue_editor.client.ui;

import com.google.gson.JsonObject;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.*;
import top.rookiestwo.maimai_dialogue_editor.document.*;
import top.rookiestwo.maimai_dialogue_editor.project.*;
import java.util.*;

/** Scrollable Step/End action calls; resources themselves remain in the left resource tree. */
final class ActionCallsView extends LinearLayout {
    private final ProjectWorkspace project;
    private final ActionWorkspace model;
    private final LinearLayout list;
    private final TextView empty;
    private final List<Button> controls = new ArrayList<>(), rows = new ArrayList<>();
    private Object revision;
    private ActionWorkspace.Context context;
    ActionCallsView(Context context, ProjectWorkspace project, EditorPreviewHost preview) {
        super(context); this.project = project; model = project.actions(); setOrientation(VERTICAL);
        var toolbar = new LinearLayout(context); addView(toolbar);
        button(toolbar, "+", "action.add", () -> model.add("custom"));
        button(toolbar, "↗", "action.add_reference", () -> model.add("reference"));
        button(toolbar, "⧉", "browser.copy", model::copy);
        button(toolbar, "−", "browser.delete", model::delete);
        button(toolbar, "↑", "edit.up", () -> model.move(-1));
        button(toolbar, "↓", "edit.down", () -> model.move(1));
        button(toolbar, "▶", "action.preview_step", preview::restartStep);
        list = new LinearLayout(context); list.setOrientation(VERTICAL);
        list.setOnClickListener(view -> model.select(-1));
        empty = EditorWidgets.compactParagraph(context, "action.empty");
        var scroll = EditorWidgets.formScroll(context, list); scroll.setFillViewport(true);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
    }
    private Button button(LinearLayout toolbar, String icon, String label, Runnable action) {
        var button = EditorWidgets.icon(getContext(), icon, label, action); toolbar.addView(button); controls.add(button);
        EditorWidgets.bindMetrics(button, () -> button.setLayoutParams(new LayoutParams(dp(28), dp(EditorWidgets.COMPACT_CONTROL_DP)))); return button;
    }
    void refresh() {
        var next = model.context(); var resource = next == null ? null : project.draft().revision(next.resource());
        if (!Objects.equals(context, next) || revision != resource) {
            context = next; revision = resource; list.removeAllViews(); rows.clear(); var calls = model.calls();
            if (calls == null || calls.isEmpty()) {
                empty.setText(EditorWidgets.tr(context == null || context.standalone() ? "no_step" : calls == null ? "edit.invalid_object" : "action.empty")); list.addView(empty);
            } else for (int i = 0; i < calls.size(); i++) {
                int index = i; var expected = context;
                var button = EditorWidgets.button(getContext(), "", () -> {
                    if (Objects.equals(expected, model.context())) model.select(model.selected() == index ? -1 : index);
                });
                JsonObject call = DialogueDraft.object(calls.get(i));
                String title = ActionFields.text(call, "action.type", "").equals("reference") ? ActionFields.text(call, "action.id", "")
                        : summary(DialogueDraft.object(ActionFields.get(call, "action.action")));
                String target = ActionFields.text(call, "target", "");
                String label = (i + 1) + "  " + (target.isEmpty() ? "" : target + " · ") + title + "   +" + ActionFields.text(call, "delay_ms", "0") + " ms";
                button.setText(label); button.setTooltipText(label); button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                list.addView(button); rows.add(button);
                EditorWidgets.bindMetrics(button, () -> {
                    button.setPadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0, dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0);
                    button.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_ROW_DP)));
                });
            }
        }
        int selected = model.selected();
        for (int i = 0; i < rows.size(); i++) { rows.get(i).setSelected(i == selected); EditorWidgets.enabled(rows.get(i), model.active()); }
        for (int i = 0; i < controls.size(); i++) EditorWidgets.enabled(controls.get(i), model.canAdd() && switch (i) {
            case 0, 1, 6 -> true; case 4 -> selected > 0; case 5 -> selected >= 0 && selected + 1 < rows.size(); default -> selected >= 0;
        });
    }
    private String summary(JsonObject action) {
        if (action == null) return EditorWidgets.tr("edit.invalid_object");
        var fields = ActionFields.COMPONENTS.stream()
                .filter(action::has).map(field -> EditorWidgets.tr("action." + field)).toList();
        return fields.isEmpty() ? EditorWidgets.tr("action.preset.custom") : String.join(" · ", fields);
    }
}
