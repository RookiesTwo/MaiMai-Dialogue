package top.rookiestwo.maimai_dialogue_editor.client.ui.properties;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;

/** 项目下拉菜单中的基本设置；不占用对象属性面板。 */
public final class ProjectProperties extends LinearLayout {
    private final ProjectWorkspace workspace;
    private final TextView empty;
    private final LinearLayout fields;
    private final EditText name;
    private final EditText namespace;
    private final TextView path;
    private final TextView warning;
    private boolean refreshing;
    private long focusedRevision = -1;

    public ProjectProperties(Context context, ProjectWorkspace workspace) {
        super(context);
        this.workspace = workspace;
        setOrientation(VERTICAL);
        EditorWidgets.bindMetrics(this, () -> setPadding(dp(8), dp(4), dp(8), dp(4)));
        empty = EditorWidgets.paragraph(context, "no_project");
        addView(empty);
        fields = new LinearLayout(context);
        fields.setOrientation(VERTICAL);
        addView(fields, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        EditorWidgets.formLabel(fields, "project.name");
        name = EditorWidgets.input(context, "", value -> {
            if (!refreshing) workspace.editName(value);
        }, workspace::endEdit);
        fields.addView(name, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        EditorWidgets.formLabel(fields, "project.namespace");
        namespace = EditorWidgets.input(context, "", value -> {
            if (!refreshing) workspace.editNamespace(value);
        }, workspace::endEdit);
        fields.addView(namespace, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        fields.addView(EditorWidgets.paragraph(context, "project.namespace_help"));
        EditorWidgets.formLabel(fields, "project.directory");
        path = EditorWidgets.paragraph(context, "");
        fields.addView(path);
        warning = EditorWidgets.paragraph(context, "project.metadata_warning");
        fields.addView(warning);
        refresh();
    }

    public void refresh() {
        ProjectDraft draft = workspace.draft();
        empty.setVisibility(draft == null ? VISIBLE : GONE);
        fields.setVisibility(draft == null ? GONE : VISIBLE);
        if (draft == null) return;
        refreshing = true;
        try {
            // Do not reset the selection/IME composition during ordinary text input.
            if (!name.getText().toString().equals(draft.name())) name.setText(draft.name());
            if (!namespace.getText().toString().equals(draft.namespace())) namespace.setText(draft.namespace());
            name.setEnabled(!workspace.busy());
            namespace.setEnabled(!workspace.busy());
            path.setText(workspace.directory().toString());
            warning.setVisibility(draft.hasValidMetadata() ? GONE : VISIBLE);
            warning.setText(EditorWidgets.tr("project.metadata_warning"));
        } finally {
            refreshing = false;
        }
        var issue = workspace.focusedIssue();
        if (issue != null && issue.resource() == null) {
            warning.setVisibility(VISIBLE);
            warning.setText(issue.field() + "\n" + top.rookiestwo.maimai_dialogue_editor.client.EditorIssueText.describe(issue));
            EditText target = issue.field().equals("name") ? name : issue.field().equals("namespace") ? namespace : null;
            if (focusedRevision != workspace.issueFocusRevision()) {
                focusedRevision = workspace.issueFocusRevision();
                if (target != null) post(() -> {
                    if (isAttachedToWindow() && workspace.focusedIssue() == issue) target.requestFocus();
                });
            }
        }
    }
}
