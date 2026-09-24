package top.rookiestwo.maimai_dialogue_editor.client.ui.resource;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorButtonIcon;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;

/** Compact document title and actions; ordered steps live in the resource tree. */
public final class ResourceDocumentView extends FrameLayout {
    private final ProjectWorkspace workspace;
    private final TextView title;
    private final Button add;
    private int inset;
    private int verticalInset;
    private int buttonSize;
    private int titleWidth;

    public ResourceDocumentView(Context context, ProjectWorkspace workspace) {
        super(context);
        this.workspace = workspace;
        EditorWidgets.propertyButtonScope(this);
        title = EditorWidgets.label(context, "browser.no_document", 13, EditorWidgets.ACCENT);
        add = EditorWidgets.icon(context, EditorButtonIcon.ADD, "edit.add_step", workspace.content()::addStep);
        addView(title);
        addView(add);
        EditorWidgets.bindMetrics(this, () -> setBackground(EditorWidgets.shape(EditorWidgets.PANEL, dp(1))));
        refresh();
    }

    public void refresh() {
        ResourceKey opened = workspace.resources().opened();
        title.setText(opened == null ? EditorWidgets.tr("browser.no_document")
                : EditorWidgets.tr("resource." + opened.kind().key()) + " · " + opened.id(workspace.draft().namespace()));
        title.setTooltipText(title.getText());
        boolean dialogue = opened != null && opened.kind() == ResourceKind.DIALOGUE;
        add.setVisibility(dialogue ? VISIBLE : GONE);
        EditorWidgets.enabled(add, workspace.content().canAddStep());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        inset = Math.min(dp(4), width / 2);
        verticalInset = Math.min(dp(2), height / 2);
        int available = Math.max(0, width - inset * 2);
        int contentHeight = Math.max(0, height - verticalInset * 2);
        buttonSize = add.getVisibility() == GONE ? 0 : Math.min(dp(EditorWidgets.COMPACT_CONTROL_DP),
                Math.min(contentHeight, available));
        int remaining = available - buttonSize;
        titleWidth = remaining - (buttonSize == 0 ? 0 : Math.min(dp(6), remaining));
        EditorWidgets.measureExact(title, titleWidth, contentHeight);
        EditorWidgets.measureExact(add, buttonSize, buttonSize);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int end = right - left - inset;
        int buttonTop = (bottom - top - buttonSize) / 2;
        if (add.getVisibility() == GONE) add.layout(0, 0, 0, 0);
        else add.layout(end - buttonSize, buttonTop, end, buttonTop + buttonSize);
        title.layout(inset, verticalInset, inset + titleWidth, bottom - top - verticalInset);
    }
}
