package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** 项目创建、列表选择及确认表单；不提供路径编辑入口。 */
final class ProjectDialog extends FrameLayout {
    private final ProjectWorkspace workspace;
    private final LinearLayout content;
    private final View panel;
    private ProjectListView projectList;
    private final TextView feedback;
    private final List<Button> buttons = new ArrayList<>();
    private final Map<String, EditText> inputs = new LinkedHashMap<>();
    private boolean refreshing;

    ProjectDialog(Context context, ProjectWorkspace workspace) {
        super(context);
        this.workspace = workspace;
        setBackground(EditorWidgets.shape(0x80788088, 0));
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        EditorWidgets.bindMetrics(content, () -> content.setPadding(dp(18), dp(12), dp(18), dp(12)));
        String title = switch (workspace.page()) {
            case NEW -> "project.new";
            case OPEN -> "project.open";
            case SAVE_AS -> "project.save_as";
            case CONFIRM -> "project.confirm_title";
            default -> throw new IllegalArgumentException("Not a project form: " + workspace.page());
        };
        TextView heading = EditorWidgets.label(context, title, 18, EditorWidgets.ACCENT);
        content.addView(heading);
        EditorWidgets.bindMetrics(heading, () -> heading.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, heading.dp(40))));

        switch (workspace.page()) {
            case NEW -> {
                addInput("project.name", workspace.formName(), workspace::setFormName);
                addInput("project.namespace", workspace.formNamespace(), workspace::setFormNamespace);
                content.addView(EditorWidgets.paragraph(context, "project.namespace_help"));
                content.addView(EditorWidgets.paragraph(context, "project.new_help"));
                addButton("project.create", workspace::submitNew);
            }
            case OPEN -> {
                content.addView(EditorWidgets.paragraph(context, "project.open_help"));
                projectList = new ProjectListView(context, workspace);
                content.addView(projectList, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
                addButton("project.refresh", workspace::refreshProjects);
            }
            case SAVE_AS -> {
                addInput("project.name", workspace.formName(), workspace::setFormName);
                content.addView(EditorWidgets.paragraph(context, "project.save_as_help"));
                addButton("project.save_as", workspace::submitSaveAs);
            }
            case CONFIRM -> {
                content.addView(EditorWidgets.paragraph(context, "project.confirm_message"));
                addButton("project.save_continue", workspace::saveAndContinue);
                addButton("project.discard", workspace::discardAndContinue);
            }
            default -> throw new IllegalArgumentException("No project dialog requested");
        }
        addButton("project.cancel", workspace::cancel);
        feedback = EditorWidgets.paragraph(context, "");
        feedback.setMaxLines(3);
        content.addView(feedback);
        // 项目列表独立滚动，刷新／取消始终留在列表下方。
        panel = projectList == null ? EditorWidgets.formScroll(context, content) : content;
        EditorWidgets.bindMetrics(panel, () -> panel.setBackground(EditorWidgets.shape(EditorWidgets.PANEL, dp(1))));
        addView(panel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
        refresh();
    }

    private void addInput(String label, String value, Consumer<String> changed) {
        EditorWidgets.formLabel(content, label);
        EditText input = EditorWidgets.input(getContext(), value, text -> {
            if (!refreshing) changed.accept(text);
        }, () -> {});
        inputs.put(label, input);
        content.addView(input, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addButton(String key, Runnable action) {
        Button button = EditorWidgets.button(getContext(), key, action);
        buttons.add(button);
        content.addView(button);
        EditorWidgets.bindMetrics(button, () -> {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(34));
            params.setMargins(0, dp(6), 0, 0);
            button.setLayoutParams(params);
        });
    }

    void focusFirst() {
        if (inputs.isEmpty()) requestFocus();
        else inputs.values().iterator().next().requestFocus();
    }

    void refresh() {
        buttons.forEach(button -> EditorWidgets.enabled(button, !workspace.busy()));
        refreshing = true;
        try {
            syncInput("project.name", workspace.formName());
            syncInput("project.namespace", workspace.formNamespace());
            inputs.values().forEach(input -> input.setEnabled(!workspace.busy()));
        } finally {
            refreshing = false;
        }
        String text = workspace.errorReason() == null
                ? (workspace.busy() ? EditorWidgets.tr(workspace.message()) : "")
                : EditorWidgets.tr("project.error." + workspace.errorReason()) + "\n" + workspace.errorDetail();
        feedback.setText(text);
        feedback.setVisibility(text.isEmpty() ? GONE : VISIBLE);
        if (projectList != null) projectList.refresh();
    }

    private void syncInput(String key, String value) {
        EditText input = inputs.get(key);
        if (input != null && !input.getText().toString().equals(value)) input.setText(value);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        LayoutParams params = (LayoutParams) panel.getLayoutParams();
        params.width = Math.max(0, Math.min(dp(600), width - Math.min(dp(24), width / 8)));
        params.height = Math.max(0, Math.min(dp(560), height - Math.min(dp(24), height / 8)));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
