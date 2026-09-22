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
    private int verticalInset;
    private int buttonSize;
    private int buttonGap;
    private int titleWidth;

    ResourceDocumentView(Context context, ProjectWorkspace workspace) {
        super(context);
        this.workspace = workspace;
        EditorWidgets.propertyButtonScope(this);
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
        inset = Math.min(dp(4), width / 2);
        verticalInset = Math.min(dp(2), height / 2);
        int available = Math.max(0, width - inset * 2);
        int contentHeight = Math.max(0, height - verticalInset * 2);
        int count = (close.getVisibility() == GONE ? 0 : 1) + (add.getVisibility() == GONE ? 0 : 2);
        buttonGap = count == 0 ? 0 : Math.min(dp(4), available / (count * 2));
        int gaps = buttonGap * Math.max(0, count - 1);
        buttonSize = count == 0 ? 0 : Math.min(dp(EditorWidgets.COMPACT_CONTROL_DP),
                Math.min(contentHeight, Math.max(0, available - gaps) / count));
        int remaining = Math.max(0, available - count * buttonSize - gaps);
        titleWidth = remaining - (count == 0 ? 0 : Math.min(dp(6), remaining));
        EditorPanel.measureExact(title, titleWidth, contentHeight);
        for (Button button : new Button[]{add, locate, close}) {
            int size = button.getVisibility() == GONE ? 0 : buttonSize;
            EditorPanel.measureExact(button, size, size);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int end = right - left - inset;
        int buttonTop = (bottom - top - buttonSize) / 2;
        for (Button button : new Button[]{close, locate, add}) {
            if (button.getVisibility() == GONE) { button.layout(0, 0, 0, 0); continue; }
            button.layout(end - buttonSize, buttonTop, end, buttonTop + buttonSize);
            end -= buttonSize + buttonGap;
        }
        title.layout(inset, verticalInset, inset + titleWidth, bottom - top - verticalInset);
    }
}
