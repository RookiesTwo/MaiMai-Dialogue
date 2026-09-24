package top.rookiestwo.maimai_dialogue_editor.client.ui;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import org.jetbrains.annotations.Nullable;

final class EditorSplitter extends View {
    enum Axis { LEFT, RIGHT, ACTIONS }

    interface DragListener {
        void begin(Axis axis);

        void move(Axis axis, float delta);

        void end(Axis axis);
    }

    private final Axis axis;
    private final DragListener listener;
    private boolean dragging;
    private float origin;

    EditorSplitter(Context context, Axis axis, DragListener listener) {
        super(context);
        this.axis = axis;
        this.listener = listener;
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setTooltipText(EditorWidgets.tr("resize"));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{-R.attr.state_enabled}, EditorWidgets.shape(EditorWidgets.BACKGROUND, 0));
        background.addState(new int[]{R.attr.state_pressed}, EditorWidgets.shape(EditorWidgets.ACCENT, 0));
        background.addState(new int[]{R.attr.state_hovered}, EditorWidgets.shape(EditorWidgets.SPLITTER_HOVER, 0));
        background.addState(StateSet.WILD_CARD, EditorWidgets.shape(EditorWidgets.BORDER, 0));
        setBackground(background);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            cancelDrag();
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                if (!event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) {
                    return false;
                }
                requestFocus();
                origin = coordinate(event);
                dragging = true;
                setPressed(true);
                getParent().requestDisallowInterceptTouchEvent(true);
                listener.begin(axis);
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    return false;
                }
                if (!event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) {
                    cancelDrag();
                    return true;
                }
                listener.move(axis, coordinate(event) - origin);
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                if (!dragging) return false;
                listener.move(axis, coordinate(event) - origin);
                cancelDrag();
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                boolean handled = dragging;
                cancelDrag();
                return handled;
            }
            default -> {
                return dragging;
            }
        }
    }

    // 使用未随 View 位置变化的坐标，防止拖动分隔线时产生反馈跳动。
    private float coordinate(MotionEvent event) {
        return axis == Axis.LEFT || axis == Axis.RIGHT ? event.getRawX() : event.getRawY();
    }

    void cancelDrag() {
        boolean wasDragging = dragging;
        dragging = false;
        setPressed(false);
        if (wasDragging && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        // Clear the gesture before notifying; release/focus/cancel may arrive together.
        if (wasDragging) listener.end(axis);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            cancelDrag();
        }
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (!gainFocus) {
            cancelDrag();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelDrag();
        super.onDetachedFromWindow();
    }
}
