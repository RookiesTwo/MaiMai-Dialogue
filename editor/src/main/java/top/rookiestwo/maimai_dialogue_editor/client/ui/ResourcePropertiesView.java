package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceTree;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;

/** Read-only resource metadata. Project settings remain in the Project menu. */
final class ResourcePropertiesView extends LinearLayout {
    private final ProjectWorkspace workspace;
    private final TextView details;
    private final ContentPropertiesView content;
    private final TextView diagnostic;
    private final MaterialPropertiesView materials;

    ResourcePropertiesView(Context context, ProjectWorkspace workspace, ChoicePresenter choices) {
        super(context);
        this.workspace = workspace;
        setOrientation(VERTICAL);
        EditorWidgets.bindMetrics(this, () -> setPadding(dp(12), dp(8), dp(12), dp(8)));
        details = EditorWidgets.paragraph(context, "no_selection");
        addView(details, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        diagnostic = EditorWidgets.paragraph(context, "");
        diagnostic.setTextIsSelectable(true);
        addView(diagnostic);
        content = new ContentPropertiesView(context, workspace, choices);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        materials = new MaterialPropertiesView(context, workspace, choices);
        addView(materials, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        refresh();
    }

    void refresh() {
        ResourceTree.Node node = workspace.resources().selection();
        ResourceKey key = node.owner();
        boolean opened = key != null && key.equals(workspace.resources().opened());
        boolean editing = opened && (node.isStep() || key.kind() == ResourceKind.SPEAKER);
        boolean editingMaterial = opened && (key.kind().material() || key.kind() == ResourceKind.VISUAL_ASSET);
        var issue = workspace.focusedIssue();
        boolean showIssue = issue != null && key != null && key.equals(issue.resource());
        diagnostic.setVisibility(showIssue ? VISIBLE : GONE);
        diagnostic.setText(showIssue ? issue.field() + "\n" + ExportMenu.describe(issue) : "");
        if (workspace.draft() == null || node.type() == ResourceTree.Type.PROJECT) {
            details.setText(EditorWidgets.tr("no_selection"));
        } else {
            String text = EditorWidgets.tr("resource." + node.kind().key());
            if (key != null) {
                text += "\n\n" + EditorWidgets.tr("browser.id") + "\n" + key.id(workspace.draft().namespace());
                if (!editing) {
                    String name = workspace.resources().catalog().displayName(key);
                    if (!name.isBlank()) text += "\n\n" + EditorWidgets.tr("browser.display_name") + "\n" + name;
                    text += "\n\n" + EditorWidgets.tr("browser.references") + " " + workspace.resources().catalog().users(key).size();
                }
            } else {
                if (node.type() == ResourceTree.Type.FOLDER) text += "\n\n" + node.path();
                long count = workspace.resources().catalog().keys().stream().filter(item -> item.kind() == node.kind()
                        && (node.path().isEmpty() || item.path().startsWith(node.path() + "/"))).count();
                text += "\n\n" + EditorWidgets.tr("browser.count") + " " + count;
            }
            if (!node.kind().available()) text += "\n\n" + EditorWidgets.tr("unavailable");
            details.setText(text);
        }
        content.setVisibility(editing ? VISIBLE : GONE);
        content.refresh();
        materials.setVisibility(editingMaterial ? VISIBLE : GONE);
        materials.refresh();
    }
}
