package top.rookiestwo.maimai_dialogue_editor.client.ui.properties;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.ChoicePresenter;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorButtonIcon;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import com.google.gson.JsonArray;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;

import java.util.ArrayList;
import java.util.List;

/** Commands are ordered entries; a native editor confirms one entry at a time. */
final class OptionCommandsView extends LinearLayout {
    private final ProjectWorkspace workspace;
    private final ChoicePresenter choices;
    private final Button add;
    private final LinearLayout entries;
    private final List<Runnable> bindings = new ArrayList<>();
    private JsonArray displayed;
    private boolean editable;

    OptionCommandsView(Context context, ProjectWorkspace workspace, ChoicePresenter choices) {
        super(context);
        this.workspace = workspace;
        this.choices = choices;
        setOrientation(VERTICAL);
        var actions = new LinearLayout(context);
        EditorWidgets.bindMetrics(actions, () -> actions.setPadding(dp(3), dp(3), dp(3), dp(3)));
        add = EditorWidgets.icon(context, EditorButtonIcon.ADD, "command.add", () -> edit(-1));
        actions.addView(add);
        EditorWidgets.bindMetrics(add, () -> add.setLayoutParams(EditorWidgets.squareIconParams(add)));
        addView(actions, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        entries = new LinearLayout(context); entries.setOrientation(VERTICAL);
        addView(entries, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setFocusable(true);
    }

    void refresh(boolean editable) {
        this.editable = editable;
        var values = workspace.content().commands();
        if (!values.equals(displayed)) {
            displayed = values;
            entries.removeAllViews(); bindings.clear();
            for (int index = 0; index < values.size(); index++) buildEntry(index);
        }
        EditorWidgets.enabled(add, editable);
        bindings.forEach(Runnable::run);
    }

    private void buildEntry(int index) {
        var entry = new LinearLayout(getContext()) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
                int gap = Math.min(dp(6), available / 9);
                int size = Math.min(dp(EditorWidgets.COMPACT_CONTROL_DP), Math.max(0, (available - gap * 3) / 6));
                for (int i = 1; i < getChildCount(); i++) {
                    var params = (LayoutParams) getChildAt(i).getLayoutParams();
                    params.width = params.height = size; params.leftMargin = gap;
                }
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        entry.setGravity(Gravity.CENTER_VERTICAL);
        EditorWidgets.bindMetrics(entry, () -> entry.setPadding(dp(3), 0, dp(3), 0));
        var text = EditorWidgets.compactParagraph(getContext(), "");
        var value = displayed.get(index);
        String command = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : value.toString();
        text.setText((index + 1) + ". " + command);
        text.setSingleLine(true); text.setEllipsize(TextUtils.TruncateAt.END);
        text.setGravity(Gravity.CENTER_VERTICAL); text.setTextColor(EditorWidgets.TEXT);
        text.setTooltipText(command);
        text.setMinWidth(0); text.setMinimumWidth(0); text.setFocusable(true);
        text.setOnClickListener(view -> edit(index));
        text.setContentDescription(EditorWidgets.tr("command.edit") + ": " + command);
        text.setBackground(EditorWidgets.treeRowBackground());
        entry.addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        var up = EditorWidgets.icon(getContext(), EditorButtonIcon.TRIANGLE_UP, "edit.up", () -> { if (editable) workspace.content().moveCommand(index, -1); });
        var down = EditorWidgets.icon(getContext(), EditorButtonIcon.TRIANGLE_DOWN, "edit.down", () -> { if (editable) workspace.content().moveCommand(index, 1); });
        var delete = EditorWidgets.icon(getContext(), EditorButtonIcon.REMOVE, "browser.delete", () -> { if (editable) workspace.content().deleteCommand(index); });
        for (var button : List.of(up, down, delete)) {
            entry.addView(button);
            EditorWidgets.bindMetrics(button, () -> button.setLayoutParams(EditorWidgets.squareIconParams(button)));
        }
        entries.addView(entry, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        bindings.add(() -> {
            text.setEnabled(editable); text.setTextColor(editable ? EditorWidgets.TEXT : EditorWidgets.MUTED);
            EditorWidgets.enabled(delete, editable);
            EditorWidgets.enabled(up, editable && index > 0);
            EditorWidgets.enabled(down, editable && index + 1 < displayed.size());
        });
    }

    private void edit(int index) {
        if (!editable) return;
        var focused = getRootView().findFocus();
        if (focused != null) focused.clearFocus();
        workspace.endEdit();
        var request = workspace.content().beginCommandEdit(index);
        if (request == null) return;
        long project = workspace.projectGeneration();
        choices.editCommand(this, request.initial(), command -> {
            if (isAttachedToWindow() && editable && workspace.projectGeneration() == project)
                workspace.content().completeCommandEdit(request, command);
        });
    }
}
