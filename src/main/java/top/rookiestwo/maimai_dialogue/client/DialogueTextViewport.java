package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.widget.ScrollView;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

// 正文未溢出时保持自然高度，超过对话框剩余空间后才允许滚动。
final class DialogueTextViewport extends ScrollView {
    private final int touchSlop;
    private int heightLimit = Integer.MAX_VALUE;
    private float touchDownX;
    private float touchDownY;
    private int touchDownScrollY;
    private boolean touchMoved;
    private boolean userScrolling;
    private boolean detachedByUser;
    private boolean followLatest;
    private boolean followPosted;

    DialogueTextViewport(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setFillViewport(false);
        setVerticalScrollBarEnabled(true);
    }

    void setHeightLimit(int heightLimit) {
        this.heightLimit = Math.max(0, heightLimit);
    }

    void resetForStep() {
        userScrolling = false;
        detachedByUser = false;
        followLatest = false;
        scrollTo(0, 0);
    }

    // progressive=true 只用于打字机逐字更新，整段瞬时文本从顶部开始。
    void onTextUpdated(boolean progressive) {
        if (progressive && !detachedByUser) {
            followLatest = true;
        }
        if (!followLatest || detachedByUser || followPosted) {
            return;
        }
        followPosted = true;
        post(() -> {
            followPosted = false;
            if (followLatest && !detachedByUser) {
                fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    void applyTheme(ThemeDefinition theme) {
        DialogueScrollbarStyle.apply(this, theme);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentMode = MeasureSpec.getMode(heightMeasureSpec);
        int parentLimit = MeasureSpec.getSize(heightMeasureSpec);
        int effectiveLimit = parentMode == MeasureSpec.UNSPECIFIED
                ? heightLimit
                : Math.min(parentLimit, heightLimit);
        if (effectiveLimit == Integer.MAX_VALUE) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int mode = parentMode == MeasureSpec.EXACTLY
                ? MeasureSpec.EXACTLY
                : MeasureSpec.AT_MOST;
        super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(effectiveLimit, mode)
        );
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            touchDownX = event.getX();
            touchDownY = event.getY();
            touchDownScrollY = getScrollY();
            touchMoved = false;
        } else if (action == MotionEvent.ACTION_MOVE && !touchMoved) {
            float deltaX = Math.abs(event.getX() - touchDownX);
            float deltaY = Math.abs(event.getY() - touchDownY);
            if (deltaX > touchSlop || deltaY > touchSlop) {
                touchMoved = true;
                userScrolling = true;
            }
        }

        boolean handled = super.onTouchEvent(event);
        if (userScrolling) {
            updateFollowFromUserScroll();
        }
        if (action == MotionEvent.ACTION_UP) {
            if (handled
                    && !touchMoved
                    && getScrollY() == touchDownScrollY) {
                performClick();
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            touchMoved = true;
        }
        return handled;
    }

    @Override
    public boolean onGenericMotionEvent(@NonNull MotionEvent event) {
        boolean scrollEvent = event.getAction() == MotionEvent.ACTION_SCROLL
                && event.getAxisValue(MotionEvent.AXIS_VSCROLL) != 0.0F;
        if (scrollEvent) {
            userScrolling = true;
            if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0.0F) {
                detachedByUser = true;
                followLatest = false;
            }
        }
        boolean handled = super.onGenericMotionEvent(event);
        if (scrollEvent && handled) {
            post(this::updateFollowFromUserScroll);
        }
        return handled;
    }

    @Override
    protected void onScrollChanged(
            int left,
            int top,
            int oldLeft,
            int oldTop
    ) {
        super.onScrollChanged(left, top, oldLeft, oldTop);
        if (userScrolling) {
            updateFollowFromUserScroll();
        }
    }

    private void updateFollowFromUserScroll() {
        detachedByUser = canScrollVertically(1);
        followLatest = !detachedByUser;
        if (!detachedByUser) {
            userScrolling = false;
        }
    }
}
