package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;

/** Open-document host; content-specific editors are introduced in the next delivery. */
final class ResourceDocumentView extends LinearLayout {
    private final ProjectWorkspace workspace;
    private final TextView title;
    private final TextView placeholder;
    private final Button close;

    ResourceDocumentView(Context context, ProjectWorkspace workspace) {
        super(context);
        this.workspace = workspace;
        setOrientation(VERTICAL);
        LinearLayout header = new LinearLayout(context);
        title = EditorWidgets.label(context, "browser.no_document", 13, EditorWidgets.ACCENT);
        header.addView(title, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        close = EditorWidgets.icon(context, "×", "browser.close_document", workspace.resources()::closeDocument);
        header.addView(close);
        EditorWidgets.bindMetrics(close, () -> close.setLayoutParams(new LayoutParams(dp(28), LayoutParams.MATCH_PARENT)));
        addView(header);
        EditorWidgets.bindMetrics(header, () -> {
            header.setPadding(dp(10), 0, 0, 0);
            header.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(28)));
        });
        placeholder = EditorWidgets.placeholder(context, "browser.no_document");
        addView(placeholder, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        refresh();
    }

    void refresh() {
        ResourceKey opened = workspace.resources().opened();
        title.setText(opened == null ? "" : EditorWidgets.tr("resource." + opened.kind().key()) + " · "
                + opened.id(workspace.draft().namespace()));
        title.setTooltipText(title.getText());
        placeholder.setText(EditorWidgets.tr(opened == null ? "browser.no_document" : "browser.editor_pending"));
        close.setVisibility(opened == null ? GONE : VISIBLE);
        EditorWidgets.enabled(close, workspace.resources().active());
    }
}
