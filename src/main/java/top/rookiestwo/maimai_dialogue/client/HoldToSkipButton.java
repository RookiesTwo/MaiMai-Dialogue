package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.Button;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.util.Objects;

/**
 * Empty square control that requires a one-second pointer hold to activate.
 */
final class HoldToSkipButton extends Button {
    static final int HOLD_DURATION_MS = 1_000;

    private final Paint progressPaint = new Paint();
    private final Runnable completedAction;
    private ValueAnimator holdAnimator;
    private float holdProgress;
    private boolean triggered;

    HoldToSkipButton(Context context, Runnable completedAction) {
        super(context);
        this.completedAction = Objects.requireNonNull(
                completedAction,
                "completedAction"
        );
        setText("");
        setClickable(true);
        setFocusable(true);
        setWillNotDraw(false);
        progressPaint.setStroke(true);
        progressPaint.setStrokeWidth(dp(2));
    }

    void applyTheme(ThemeDefinition theme) {
        DialogueBoxView.applyControlButtonTheme(this, theme);
        progressPaint.setColor(theme.option().hoverBorder().argb());
        invalidate();
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
        next.setDuration(HOLD_DURATION_MS);
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
