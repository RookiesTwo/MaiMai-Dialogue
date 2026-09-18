package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewTreeObserver;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue.client.ui.layout.ResponsiveFrameLayout;

final class EditorWorkbench extends ResponsiveFrameLayout implements EditorSplitter.DragListener {
    private final EditorLayoutState state;
    private final EditorToolbar toolbar;
    private final TextView status;
    private final EditorPanel resources;
    private final EditorPanel properties;
    private final EditorPanel steps;
    private final EditorPanel preview;
    private final EditorPanel actions;
    private final Button leftRail;
    private final Button rightRail;
    private final EditorSplitter leftSplitter;
    private final EditorSplitter rightSplitter;
    private final EditorSplitter stepsSplitter;
    private final EditorSplitter actionsSplitter;
    private EditorLayout layout;
    private EditorLayout dragStart;
    private ViewTreeObserver tooltipObserver;
    private final ViewTreeObserver.OnGlobalLayoutListener tooltipLayoutListener =
            () -> EditorWidgets.styleTooltips(this);

    EditorWorkbench(Context context, EditorLayoutState state, Runnable closeAction) {
        super(context);
        this.state = state;
        setBackground(EditorWidgets.shape(EditorWidgets.BACKGROUND, 0));
        setFocusable(true);
        setFocusableInTouchMode(true);

        toolbar = new EditorToolbar(context, closeAction, () -> {
            cancelDrags();
            state.reset();
            requestLayout();
        });
        resources = new EditorPanel(context, "resources", EditorWidgets.resourceList(context), () -> {
            cancelDrags();
            state.leftCollapsed = true;
            requestLayout();
        }, true);
        properties = new EditorPanel(context, "properties", EditorWidgets.placeholder(context, "no_selection"), () -> {
            cancelDrags();
            state.rightCollapsed = true;
            requestLayout();
        }, false);
        steps = new EditorPanel(context, "steps", EditorWidgets.placeholder(context, "no_dialogue"), null, true);
        TextView previewContent = EditorWidgets.placeholder(context, "preview_placeholder");
        previewContent.setBackground(EditorWidgets.shape(EditorWidgets.PREVIEW, 0));
        preview = new EditorPanel(context, "scene_preview", previewContent, null, true);
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
        stepsSplitter = new EditorSplitter(context, EditorSplitter.Axis.STEPS, this);
        actionsSplitter = new EditorSplitter(context, EditorSplitter.Axis.ACTIONS, this);
        status = EditorWidgets.label(context, "status", 12, EditorWidgets.MUTED);
        status.setBackground(EditorWidgets.shape(EditorWidgets.HEADER, 0));
        EditorWidgets.bindMetrics(status, () -> status.setPadding(status.dp(10), 0, status.dp(10), 0));

        for (View view : new View[]{toolbar, resources, properties, steps, preview, actions,
                leftRail, rightRail, leftSplitter, rightSplitter, stepsSplitter, actionsSplitter, status}) {
            addView(view);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        prepareViewport(widthMeasureSpec, heightMeasureSpec);
        layout = EditorLayout.calculate(state, MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec), density());
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
        stepsSplitter.setEnabled(verticalResizable);
        actionsSplitter.setEnabled(verticalResizable);

        EditorPanel.measureExact(toolbar, layout.width(), layout.toolbar());
        EditorPanel.measureExact(status, layout.width(), layout.status());
        EditorPanel.measureExact(layout.leftCollapsed() ? leftRail : resources, layout.left(), layout.workHeight());
        EditorPanel.measureExact(layout.rightCollapsed() ? rightRail : properties, layout.right(), layout.workHeight());
        EditorPanel.measureExact(steps, layout.center(), layout.steps());
        EditorPanel.measureExact(preview, layout.center(), layout.preview());
        EditorPanel.measureExact(actions, layout.center(), layout.actions());
        EditorPanel.measureExact(leftSplitter, layout.horizontalGap(), layout.workHeight());
        EditorPanel.measureExact(rightSplitter, layout.horizontalGap(), layout.workHeight());
        EditorPanel.measureExact(stepsSplitter, layout.center(), layout.verticalGap());
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
        steps.layout(centerX, layout.toolbar(), centerEnd, layout.toolbar() + layout.steps());
        stepsSplitter.layout(centerX, layout.toolbar() + layout.steps(), centerEnd, layout.previewY());
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
            case STEPS -> {
                int max = start.workHeight() - start.verticalGap() * 2 - start.actions() - dp(EditorLayout.PREVIEW_MIN_DP);
                state.stepsDp = clamp(start.steps() + delta, dp(EditorLayout.HEADER_DP), max) / density();
                state.actionsDp = start.actions() / density();
            }
            case ACTIONS -> {
                int max = start.workHeight() - start.verticalGap() * 2 - start.steps() - dp(EditorLayout.PREVIEW_MIN_DP);
                state.actionsDp = clamp(start.actions() - delta, dp(EditorLayout.HEADER_DP), max) / density();
                state.stepsDp = start.steps() / density();
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
        for (EditorSplitter splitter : new EditorSplitter[]{leftSplitter, rightSplitter, stepsSplitter, actionsSplitter}) {
            if (splitter != null) {
                splitter.cancelDrag();
            }
        }
        dragStart = null;
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
