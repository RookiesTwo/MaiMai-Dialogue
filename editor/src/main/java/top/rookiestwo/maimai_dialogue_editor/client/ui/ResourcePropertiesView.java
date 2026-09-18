package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceTree;

/** Read-only resource metadata. Project settings remain in the Project menu. */
final class ResourcePropertiesView extends LinearLayout {
    private final ProjectWorkspace workspace;
    private final TextView details;
    private final Button open;

    ResourcePropertiesView(Context context, ProjectWorkspace workspace) {
        super(context);
        this.workspace = workspace;
        setOrientation(VERTICAL);
        EditorWidgets.bindMetrics(this, () -> setPadding(dp(12), dp(8), dp(12), dp(8)));
        details = EditorWidgets.paragraph(context, "no_selection");
        addView(details, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        open = EditorWidgets.button(context, "browser.open", () -> {
            ResourceKey selected = workspace.resources().selection().resource();
            if (selected != null) workspace.resources().open(selected);
        });
        addView(open);
        EditorWidgets.bindMetrics(open, () -> open.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(32))));
        refresh();
    }

    void refresh() {
        ResourceTree.Node node = workspace.resources().selection();
        ResourceKey key = node.resource();
        if (workspace.draft() == null || node.type() == ResourceTree.Type.PROJECT) {
            details.setText(EditorWidgets.tr("no_selection"));
        } else {
            String text = EditorWidgets.tr("resource." + node.kind().key());
            if (key != null) {
                text += "\n\n" + EditorWidgets.tr("browser.id") + "\n" + key.id(workspace.draft().namespace());
                String name = workspace.resources().catalog().displayName(key);
                if (!name.isBlank()) text += "\n\n" + EditorWidgets.tr("browser.display_name") + "\n" + name;
                text += "\n\n" + EditorWidgets.tr("browser.references") + " " + workspace.resources().catalog().users(key).size();
            } else {
                if (node.type() == ResourceTree.Type.FOLDER) text += "\n\n" + node.path();
                long count = workspace.resources().catalog().keys().stream().filter(item -> item.kind() == node.kind()
                        && (node.path().isEmpty() || item.path().startsWith(node.path() + "/"))).count();
                text += "\n\n" + EditorWidgets.tr("browser.count") + " " + count;
            }
            if (!node.kind().available()) text += "\n\n" + EditorWidgets.tr("unavailable");
            details.setText(text);
        }
        open.setVisibility(key != null && key.kind().available() ? VISIBLE : GONE);
        EditorWidgets.enabled(open, workspace.resources().canModifySelected());
    }
}
