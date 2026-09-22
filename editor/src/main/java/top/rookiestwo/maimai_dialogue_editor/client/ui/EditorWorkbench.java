package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewTreeObserver;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue.client.ui.layout.ResponsiveFrameLayout;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.export.ExportWorkspace;

final class EditorWorkbench extends ResponsiveFrameLayout implements EditorSplitter.DragListener {
    private final EditorLayoutState state;
    private final EditorToolbar toolbar;
    private final TextView status;
    private final ResourceBrowserView browser;
    private final ResourcePropertiesView resourceProperties;
    private final ResourceDocumentView document;
    private final ProjectWorkspace workspace;
    private long issueFocusRevision = -1;
    private final ExportWorkspace exports;
    private final EditorPanel resources;
    private final EditorPanel properties;
    private final EditorPanel preview;
    private final EditorPreviewHost previewHost;
    private final EditorPanel actions;
    private final Button leftRail;
    private final Button rightRail;
    private final EditorSplitter leftSplitter;
    private final EditorSplitter rightSplitter;
    private final EditorSplitter actionsSplitter;
    private EditorLayout layout;
    private EditorLayout dragStart;
    private ViewTreeObserver tooltipObserver;
    private final ViewTreeObserver.OnGlobalLayoutListener tooltipLayoutListener =
            () -> EditorWidgets.styleTooltips(this);

    EditorWorkbench(Context context, EditorLayoutState state, ProjectWorkspace workspace, Runnable closeAction,
                    ChoicePresenter choices, EditorPreviewHost previewHost, ExportWorkspace exports) {
        super(context);
        this.state = state;
        this.workspace = workspace;
        this.exports = exports;
        this.previewHost = previewHost;
        setBackground(EditorWidgets.shape(EditorWidgets.BACKGROUND, 0));
        setFocusable(true);
        setFocusableInTouchMode(true);

        toolbar = new EditorToolbar(context, closeAction, this::restoreDefaultLayout, workspace, previewHost);
        previewHost.setListener(() -> toolbar.refresh(workspace));
        browser = new ResourceBrowserView(context, workspace);
        resources = new EditorPanel(context, "resources", browser, () -> {
            cancelDrags();
            state.leftCollapsed = true;
            requestLayout();
        }, true);
        resourceProperties = new ResourcePropertiesView(context, workspace, choices, state, previewHost);
        properties = new EditorPanel(context, "properties", EditorWidgets.formScroll(context, resourceProperties), () -> {
            cancelDrags();
            state.rightCollapsed = true;
            requestLayout();
        }, false);
        document = new ResourceDocumentView(context, workspace);
        preview = new EditorPanel(context, "scene_preview", previewHost.createView(context), null, true);
        actions = new EditorPanel(context, "actions", EditorWidgets.placeholder(context, "no_step"), null, true);
        leftRail = EditorWidgets.icon(context, "›", "expand_left", () -> {
            state.leftCollapsed = false;
            requestLayout();
        });
        rightRail = EditorWidgets.icon(context, "‹", "expand_right", () -> {
            state.rightCollapsed = false;
            requestLayout();
        });
        leftSplitter = new EditorSplitter(context, EditorSplitter.Axis.LEFT, this);
        rightSplitter = new EditorSplitter(context, EditorSplitter.Axis.RIGHT, this);
        actionsSplitter = new EditorSplitter(context, EditorSplitter.Axis.ACTIONS, this);
        status = EditorWidgets.label(context, "status", 12, EditorWidgets.MUTED);
        status.setBackground(EditorWidgets.shape(EditorWidgets.HEADER, 0));
        EditorWidgets.bindMetrics(status, () -> status.setPadding(status.dp(10), 0, status.dp(10), 0));

        for (View view : new View[]{toolbar, resources, properties, document, preview, actions,
                leftRail, rightRail, leftSplitter, rightSplitter, actionsSplitter, status}) {
            addView(view);
        }
        refreshProject();
    }

    private void restoreDefaultLayout() {
        cancelDrags();
        state.reset();
        resourceProperties.refresh();
        requestLayout();
    }

    void refreshProject() {
        workspace.materials().synchronize();
        if (issueFocusRevision != workspace.issueFocusRevision()) {
            issueFocusRevision = workspace.issueFocusRevision();
            var issue = workspace.focusedIssue();
            if (issue != null && issue.resource() != null) {
                state.leftCollapsed = false;
                state.rightCollapsed = false;
                requestLayout();
            }
        }
        previewHost.synchronize();
        toolbar.refresh(workspace);
        String name = workspace.draft() == null ? EditorWidgets.tr("no_project")
                : workspace.draft().name().isBlank() ? EditorWidgets.tr("project.untitled") : workspace.draft().name();
        String label = name + (workspace.dirty() ? " *" : "");
        browser.refresh();
        resourceProperties.refresh();
        document.refresh();
        String message = workspace.errorReason() == null ? EditorWidgets.tr(workspace.message())
                : EditorWidgets.tr("project.error." + workspace.errorReason()) + " " + workspace.errorDetail();
        String saveState = workspace.draft() == null ? "" : " · "
                + EditorWidgets.tr(workspace.dirty() ? "project.unsaved" : "project.saved_state");
        String validation = exports.busy() || exports.report() != null || !exports.error().isEmpty()
                ? " · " + EditorWidgets.tr(exports.status()) : "";
        String materialError = workspace.draft() == null ? "" : workspace.materials().refreshError();
        String material = materialError.isEmpty() ? "" : " · " + EditorWidgets.tr("material.failed");
        status.setText(label + saveState + " · " + message + validation + material);
        status.setTooltipText(status.getText() + (materialError.isEmpty() ? "" : "\n" + materialError));
    }

    View projectMenuAnchor() {
        return toolbar.projectMenuAnchor();
    }

    View exportMenuAnchor() { return toolbar.exportMenuAnchor(); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        prepareViewport(widthMeasureSpec, heightMeasureSpec);
        layout = EditorLayout.calculate(state, MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec), density());
        previewHost.setReferenceHeight(layout.height());
        resources.setVisibility(layout.leftCollapsed() ? GONE : VISIBLE);
        leftRail.setVisibility(layout.leftCollapsed() ? VISIBLE : GONE);
        properties.setVisibility(layout.rightCollapsed() ? GONE : VISIBLE);
        rightRail.setVisibility(layout.rightCollapsed() ? VISIBLE : GONE);
        leftRail.setTooltipText(EditorWidgets.tr(layout.leftCollapsed() && !state.leftCollapsed
                ? "narrow_window" : "expand_left"));
        rightRail.setTooltipText(EditorWidgets.tr(layout.rightCollapsed() && !state.rightCollapsed
                ? "narrow_window" : "expand_right"));
        leftSplitter.setEnabled(!layout.leftCollapsed());
        rightSplitter.setEnabled(!layout.rightCollapsed());
        boolean verticalResizable = layout.preview() >= dp(EditorLayout.PREVIEW_MIN_DP);
        actionsSplitter.setEnabled(verticalResizable);

        EditorPanel.measureExact(toolbar, layout.width(), layout.toolbar());
        EditorPanel.measureExact(status, layout.width(), layout.status());
        EditorPanel.measureExact(layout.leftCollapsed() ? leftRail : resources, layout.left(), layout.workHeight());
        EditorPanel.measureExact(layout.rightCollapsed() ? rightRail : properties, layout.right(), layout.workHeight());
        EditorPanel.measureExact(document, layout.center(), layout.document());
        EditorPanel.measureExact(preview, layout.center(), layout.preview());
        EditorPanel.measureExact(actions, layout.center(), layout.actions());
        EditorPanel.measureExact(leftSplitter, layout.horizontalGap(), layout.workHeight());
        EditorPanel.measureExact(rightSplitter, layout.horizontalGap(), layout.workHeight());
        EditorPanel.measureExact(actionsSplitter, layout.center(), layout.verticalGap());
        setMeasuredDimension(layout.width(), layout.height());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int centerX = layout.centerX();
        int centerEnd = centerX + layout.center();
        int workBottom = layout.height() - layout.status();
        toolbar.layout(0, 0, layout.width(), layout.toolbar());
        status.layout(0, workBottom, layout.width(), layout.height());
        (layout.leftCollapsed() ? leftRail : resources).layout(0, layout.toolbar(), layout.left(), workBottom);
        (layout.rightCollapsed() ? rightRail : properties).layout(
                centerEnd + layout.horizontalGap(), layout.toolbar(), layout.width(), workBottom);
        leftSplitter.layout(layout.left(), layout.toolbar(), centerX, workBottom);
        rightSplitter.layout(centerEnd, layout.toolbar(), centerEnd + layout.horizontalGap(), workBottom);
        document.layout(centerX, layout.toolbar(), centerEnd, layout.previewY());
        preview.layout(centerX, layout.previewY(), centerEnd, layout.previewY() + layout.preview());
        actionsSplitter.layout(centerX, layout.previewY() + layout.preview(), centerEnd, layout.actionsY());
        actions.layout(centerX, layout.actionsY(), centerEnd, workBottom);
        restoreScrollPositions();
    }

    @Override
    protected void onViewportChanged(boolean densityChanged) {
        cancelDrags();
        if (densityChanged) {
            EditorWidgets.refreshMetrics(this);
        }
    }

    @Override
    public void begin(EditorSplitter.Axis axis) {
        dragStart = layout;
    }

    @Override
    public void end(EditorSplitter.Axis axis) {
        dragStart = null;
        previewHost.finishViewportResize();
    }

    // 拖动以按下时的实际尺寸为基准；当前手势里固定另一侧，避免钳制造成反向挤压。
    @Override
    public void move(EditorSplitter.Axis axis, float delta) {
        if (dragStart == null) {
            return;
        }
        EditorLayout start = dragStart;
        switch (axis) {
            case LEFT -> {
                int max = start.columnSpace() - start.right() - dp(EditorLayout.CENTER_MIN_DP);
                state.leftFraction = clamp(start.left() + delta, dp(EditorLayout.LEFT_MIN_DP), max)
                        / Math.max(1, start.columnSpace());
                if (!start.rightCollapsed()) {
                    state.rightFraction = (double) start.right() / Math.max(1, start.columnSpace());
                }
            }
            case RIGHT -> {
                int max = start.columnSpace() - start.left() - dp(EditorLayout.CENTER_MIN_DP);
                state.rightFraction = clamp(start.right() - delta, dp(EditorLayout.RIGHT_MIN_DP), max)
                        / Math.max(1, start.columnSpace());
                if (!start.leftCollapsed()) {
                    state.leftFraction = (double) start.left() / Math.max(1, start.columnSpace());
                }
            }
            case ACTIONS -> {
                int max = start.workHeight() - start.verticalGap() - start.document() - dp(EditorLayout.PREVIEW_MIN_DP);
                state.actionsDp = clamp(start.actions() - delta, dp(EditorLayout.HEADER_DP), max) / density();
            }
        }
        requestLayout();
    }

    private static double clamp(double value, int min, int max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }

    private float density() {
        return Math.max(0.01F, getContext().getResources().getDisplayMetrics().density);
    }

    void cancelDrags() {
        for (EditorSplitter splitter : new EditorSplitter[]{leftSplitter, rightSplitter, actionsSplitter}) {
            if (splitter != null) {
                splitter.cancelDrag();
            }
        }
        dragStart = null;
        previewHost.finishSceneDrag(true);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            cancelDrags();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        tooltipObserver = getViewTreeObserver();
        tooltipObserver.addOnGlobalLayoutListener(tooltipLayoutListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (tooltipObserver != null && tooltipObserver.isAlive()) {
            tooltipObserver.removeOnGlobalLayoutListener(tooltipLayoutListener);
        }
        tooltipObserver = null;
        cancelDrags();
        super.onDetachedFromWindow();
    }
}
