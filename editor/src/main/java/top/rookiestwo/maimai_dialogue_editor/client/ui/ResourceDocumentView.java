package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;

/** Compact document title and actions; ordered steps live in the resource tree. */
final class ResourceDocumentView extends FrameLayout {
    private final ProjectWorkspace workspace;
    private final TextView title;
    private final Button add;
    private final Button locate;
    private final Button close;
    private int inset;
    private int closeWidth;
    private int actionWidth;

    ResourceDocumentView(Context context, ProjectWorkspace workspace) {
        super(context);
        this.workspace = workspace;
        title = EditorWidgets.label(context, "browser.no_document", 13, EditorWidgets.ACCENT);
        add = EditorWidgets.icon(context, "+", "edit.add_step", workspace.content()::addStep);
        locate = EditorWidgets.icon(context, "↳", "edit.locate_step", () ->
                workspace.content().selectStep(workspace.content().snapshot().cursor().step()));
        close = EditorWidgets.icon(context, "×", "browser.close_document", workspace.resources()::closeDocument);
        addView(title);
        addView(add);
        addView(locate);
        addView(close);
        EditorWidgets.bindMetrics(this, () -> setBackground(EditorWidgets.shape(EditorWidgets.PANEL, dp(1))));
        refresh();
    }

    void refresh() {
        ResourceKey opened = workspace.resources().opened();
        title.setText(opened == null ? EditorWidgets.tr("browser.no_document")
                : EditorWidgets.tr("resource." + opened.kind().key()) + " · " + opened.id(workspace.draft().namespace()));
        title.setTooltipText(title.getText());
        boolean dialogue = opened != null && opened.kind() == ResourceKind.DIALOGUE;
        add.setVisibility(dialogue ? VISIBLE : GONE);
        locate.setVisibility(dialogue ? VISIBLE : GONE);
        close.setVisibility(opened == null ? GONE : VISIBLE);
        EditorWidgets.enabled(add, workspace.content().canAddStep());
        EditorWidgets.enabled(locate, workspace.content().active() && dialogue);
        EditorWidgets.enabled(close, workspace.resources().active());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        inset = Math.min(dp(4), Math.min(width, height) / 2);
        int available = Math.max(0, width - inset * 2);
        int contentHeight = Math.max(0, height - inset * 2);
        closeWidth = close.getVisibility() == GONE ? 0 : Math.min(dp(24), available);
        actionWidth = add.getVisibility() == GONE ? 0 : Math.min(dp(24), (available - closeWidth) / 2);
        EditorPanel.measureExact(title, available - closeWidth - actionWidth * 2, contentHeight);
        EditorPanel.measureExact(add, actionWidth, contentHeight);
        EditorPanel.measureExact(locate, actionWidth, contentHeight);
        EditorPanel.measureExact(close, closeWidth, contentHeight);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int end = right - left - inset;
        int contentBottom = bottom - top - inset;
        close.layout(end - closeWidth, inset, end, contentBottom);
        end -= closeWidth;
        locate.layout(end - actionWidth, inset, end, contentBottom);
        end -= actionWidth;
        add.layout(end - actionWidth, inset, end, contentBottom);
        title.layout(inset, inset, end - actionWidth, contentBottom);
    }
}
