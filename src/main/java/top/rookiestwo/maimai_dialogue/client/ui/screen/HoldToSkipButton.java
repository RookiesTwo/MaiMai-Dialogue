package top.rookiestwo.maimai_dialogue.client.ui.screen;

import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.ImageButton;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.util.Objects;

/**
 * Icon control that requires a one-second pointer hold to activate.
 */
final class HoldToSkipButton extends ImageButton {
    static final int ICON_PADDING_DP = 10;

    private final Paint progressPaint = new Paint();
    private final Runnable completedAction;
    private ValueAnimator holdAnimator;
    private float holdProgress;
    private boolean triggered;
    private int holdDurationMs = 600;

    HoldToSkipButton(Context context, Runnable completedAction) {
        super(context);
        this.completedAction = Objects.requireNonNull(
                completedAction,
                "completedAction"
        );
        setClickable(true);
        setFocusable(true);
        setWillNotDraw(false);
        setBackground(null);
        setPadding(
                dp(ICON_PADDING_DP),
                dp(ICON_PADDING_DP),
                dp(ICON_PADDING_DP),
                dp(ICON_PADDING_DP)
        );
        progressPaint.setStroke(true);
        progressPaint.setStrokeWidth(dp(2));
    }

    void applyTheme(ThemeDefinition theme) {
        setBackground(null);
        int iconPadding = dp(ICON_PADDING_DP);
        setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
        progressPaint.setColor(theme.option().hoverBorder().argb());
        invalidate();
    }

    void setHoldDurationMs(int holdDurationMs) {
        this.holdDurationMs = Math.clamp(holdDurationMs, 200, 3_000);
    }

    void beginHold() {
        if (isEnabled()) {
            startHold();
        }
    }

    void cancelHold() {
        ValueAnimator current = holdAnimator;
        holdAnimator = null;
        if (current != null) {
            current.cancel();
        }
        holdProgress = 0.0F;
        triggered = false;
        setPressed(false);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!isEnabled()) {
            cancelHold();
            return true;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                startHold();
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!contains(event.getX(), event.getY())) {
                    cancelHold();
                }
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelHold();
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (holdProgress <= 0.0F) {
            return;
        }
        float strokeInset = dp(3);
        float radius = Math.max(
                1.0F,
                Math.min(getWidth(), getHeight()) / 2.0F - strokeInset
        );
        canvas.drawArc(
                getWidth() / 2.0F,
                getHeight() / 2.0F,
                radius,
                -90.0F,
                360.0F * holdProgress,
                progressPaint
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            cancelHold();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelHold();
        super.onDetachedFromWindow();
    }

    private void startHold() {
        cancelHold();
        setPressed(true);
        ValueAnimator next = ValueAnimator.ofFloat(0.0F, 1.0F);
        next.setDuration(holdDurationMs);
        next.setInterpolator(TimeInterpolator.LINEAR);
        next.addUpdateListener(animator -> {
            if (holdAnimator != next) {
                return;
            }
            holdProgress = animator.getAnimatedFraction();
            invalidate();
            if (!triggered && holdProgress >= 0.9999F) {
                triggered = true;
                holdAnimator = null;
                setPressed(false);
                completedAction.run();
            }
        });
        holdAnimator = next;
        next.start();
    }

    private boolean contains(float x, float y) {
        return x >= 0.0F
                && y >= 0.0F
                && x < getWidth()
                && y < getHeight();
    }
}
