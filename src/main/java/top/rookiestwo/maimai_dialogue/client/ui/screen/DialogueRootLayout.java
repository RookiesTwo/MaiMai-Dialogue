package top.rookiestwo.maimai_dialogue.client.ui.screen;

import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueSceneView;

import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import top.rookiestwo.maimai_dialogue.presentation.DialogueBoxLayout;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualAnchor;
import top.rookiestwo.maimai_dialogue.client.scene.DialogueBoxState;
import top.rookiestwo.maimai_dialogue.client.config.ClientPreferences;

import java.util.Objects;
import java.util.function.Consumer;

final class DialogueRootLayout extends FrameLayout {
    private static final int HEIGHT_ANIMATION_DURATION_MS = 220;
    private static final int CORNER_CONTROL_SIZE_DP = 40;
    private static final float DISABLED_CONTROL_ALPHA = 0.35F;
    private static final float EXPANDED_MAX_HEIGHT = 1.0F;

    private final DialogueSceneView sceneLayer;
    private final View dialogueBox;
    private final View historyEntry;
    private final HoldToSkipButton skipButton;
    private final DialogueInputHandler input;
    private DialogueBoxLayout dialogueBoxLayout = DialogueBoxLayout.DEFAULT;
    private DialogueBoxState dialogueBoxState = DialogueBoxState.initial(
            DialogueBoxLayout.DEFAULT,
            1.0F
    );
    private ValueAnimator heightAnimator;
    private int dialogueTargetHeight = -1;
    private float dialogueDisplayHeight = -1.0F;
    private boolean heightAnimationPosted;
    private View confirmationView;
    private float controlsAlpha = 1.0F;
    private boolean optionsExpanded;
    private boolean animateNextHeightDecrease;

    DialogueRootLayout(
            Context context,
            DialogueSceneView sceneLayer,
            View dialogueBox,
            View historyEntry,
            HoldToSkipButton skipButton,
            ClientPreferences preferences
    ) {
        super(context);
        this.sceneLayer = Objects.requireNonNull(sceneLayer, "sceneLayer");
        this.dialogueBox = Objects.requireNonNull(dialogueBox, "dialogueBox");
        this.historyEntry = Objects.requireNonNull(
                historyEntry,
                "historyEntry"
        );
        this.skipButton = Objects.requireNonNull(skipButton, "skipButton");
        input = new DialogueInputHandler(
                Objects.requireNonNull(preferences, "preferences"), skipButton, this::hasConfirmation
        );
        addView(
                sceneLayer,
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );
        addView(
                dialogueBox,
                new LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                )
        );
        addView(
                historyEntry,
                new LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                )
        );
        addView(
                skipButton,
                new LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                )
        );
    }

    void setDialogueBoxLayout(DialogueBoxLayout layout) {
        dialogueBoxLayout = Objects.requireNonNull(layout, "layout");
        requestLayout();
    }

    void setOptionsExpanded(boolean expanded, boolean animateCollapse) {
        if (optionsExpanded == expanded) {
            return;
        }
        optionsExpanded = expanded;
        animateNextHeightDecrease = animateCollapse && !expanded;
        requestLayout();
    }

    void setAdvanceAction(Runnable advanceAction) {
        input.advanceAction = Objects.requireNonNull(
                advanceAction,
                "advanceAction"
        );
    }

    void setFastForwardAction(Consumer<Boolean> fastForwardAction) {
        input.fastForwardAction = Objects.requireNonNull(
                fastForwardAction,
                "fastForwardAction"
        );
    }

    void setHistoryAction(Runnable historyAction) {
        input.historyAction = Objects.requireNonNull(
                historyAction,
                "historyAction"
        );
    }

    void setExitAction(Runnable exitAction) {
        input.exitAction = Objects.requireNonNull(exitAction, "exitAction");
    }

    void setSkipAvailable(boolean available) {
        skipButton.setEnabled(available);
        skipButton.setAlpha(
                controlsAlpha * (available ? 1.0F : DISABLED_CONTROL_ALPHA)
        );
        if (!available) {
            skipButton.cancelHold();
        }
    }

    void showConfirmation(View view, Runnable escapeAction) {
        dismissConfirmation();
        input.fastForwardAction.accept(false);
        skipButton.cancelHold();
        confirmationView = Objects.requireNonNull(view, "view");
        input.confirmationEscapeAction = Objects.requireNonNull(
                escapeAction,
                "escapeAction"
        );
        addView(
                view,
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );
        view.requestFocus();
    }

    void dismissConfirmation() {
        View current = confirmationView;
        confirmationView = null;
        input.confirmationEscapeAction = () -> {
        };
        if (current != null) {
            removeView(current);
        }
    }

    boolean hasConfirmation() {
        return confirmationView != null;
    }

    void cancelTransientInput() {
        input.cancelTransientInput();
    }

    void setDialogueBoxState(DialogueBoxState state) {
        dialogueBoxState = Objects.requireNonNull(state, "state");
        float clamped = Math.clamp(state.opacity(), 0.0F, 1.0F);
        dialogueBox.setAlpha(clamped);
        historyEntry.setAlpha(clamped);
        controlsAlpha = clamped;
        skipButton.setAlpha(
                clamped * (skipButton.isEnabled()
                        ? 1.0F
                        : DISABLED_CONTROL_ALPHA)
        );
        requestLayout();
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        return input.handle(event) || super.dispatchKeyEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        sceneLayer.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        );

        int boxWidth = Math.max(
                1,
                Math.round(width * dialogueBoxLayout.width())
        );
        int boxMaxHeight = Math.max(
                1,
                Math.round(height * (optionsExpanded
                        ? EXPANDED_MAX_HEIGHT
                        : dialogueBoxLayout.maxHeight()))
        );
        dialogueBox.measure(
                MeasureSpec.makeMeasureSpec(boxWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(boxMaxHeight, MeasureSpec.AT_MOST)
        );
        updateDialogueHeightTarget(dialogueBox.getMeasuredHeight());
        historyEntry.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
        );
        int cornerControlSize = dp(CORNER_CONTROL_SIZE_DP);
        skipButton.measure(
                MeasureSpec.makeMeasureSpec(
                        cornerControlSize,
                        MeasureSpec.EXACTLY
                ),
                MeasureSpec.makeMeasureSpec(
                        cornerControlSize,
                        MeasureSpec.EXACTLY
                )
        );
        if (confirmationView != null) {
            confirmationView.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            );
        }

        setMeasuredDimension(
                View.resolveSize(width, widthMeasureSpec),
                View.resolveSize(height, heightMeasureSpec)
        );
    }

    @Override
    protected void onLayout(
            boolean changed,
            int left,
            int top,
            int right,
            int bottom
    ) {
        int width = right - left;
        int height = bottom - top;
        sceneLayer.layout(0, 0, width, height);

        int boxWidth = dialogueBox.getMeasuredWidth();
        int boxHeight = dialogueDisplayHeight < 0.0F
                ? dialogueBox.getMeasuredHeight()
                : Math.max(0, Math.round(dialogueDisplayHeight));
        float anchorX = horizontalAnchor(dialogueBoxLayout.anchor());
        float anchorY = verticalAnchor(dialogueBoxLayout.anchor());
        int boxLeft = Math.round(
                dialogueBoxState.x() * width - anchorX * boxWidth
        );
        int boxTop = Math.round(
                dialogueBoxState.y() * height - anchorY * boxHeight
        );
        // 钳制底边不超出屏幕：选项展开时对话框可能超过 anchor 预留的高度，
        // 溢出时自动改为底部对齐向上生长，保证底部选项始终可见可交互。
        int boxBottom = boxTop + boxHeight;
        if (boxBottom > height) {
            boxTop = height - boxHeight;
        }
        if (boxTop < 0) {
            boxTop = 0;
        }
        dialogueBox.layout(
                boxLeft,
                boxTop,
                boxLeft + boxWidth,
                boxTop + boxHeight
        );
        dialogueBox.setPivotX(anchorX * boxWidth);
        dialogueBox.setPivotY(anchorY * boxHeight);
        dialogueBox.setScaleX(dialogueBoxState.scale());
        dialogueBox.setScaleY(dialogueBoxState.scale());
        int historyWidth = historyEntry.getMeasuredWidth();
        int historyHeight = historyEntry.getMeasuredHeight();
        int historyMargin = dp(12);
        historyEntry.layout(
                historyMargin,
                historyMargin,
                historyMargin + historyWidth,
                historyMargin + historyHeight
        );
        int skipWidth = skipButton.getMeasuredWidth();
        int skipHeight = skipButton.getMeasuredHeight();
        skipButton.layout(
                width - historyMargin - skipWidth,
                historyMargin,
                width - historyMargin,
                historyMargin + skipHeight
        );
        if (confirmationView != null) {
            confirmationView.layout(0, 0, width, height);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            input.reset();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        input.reset();
        dismissConfirmation();
        cancelHeightAnimator();
        super.onDetachedFromWindow();
    }

    private void updateDialogueHeightTarget(int targetHeight) {
        boolean animateDecrease = animateNextHeightDecrease;
        animateNextHeightDecrease = false;
        if (dialogueTargetHeight < 0) {
            dialogueTargetHeight = targetHeight;
            dialogueDisplayHeight = targetHeight;
            return;
        }
        if (dialogueTargetHeight == targetHeight) {
            return;
        }

        int previousTargetHeight = dialogueTargetHeight;
        dialogueTargetHeight = targetHeight;
        // 普通高度缩小立即完成；只有玩家点击“收起”时保留收缩动画。
        if (targetHeight < previousTargetHeight && !animateDecrease) {
            cancelHeightAnimator();
            dialogueDisplayHeight = targetHeight;
            requestLayout();
            return;
        }
        if (heightAnimationPosted) {
            return;
        }
        heightAnimationPosted = true;
        post(() -> {
            heightAnimationPosted = false;
            animateDialogueHeightTo(dialogueTargetHeight);
        });
    }

    private void animateDialogueHeightTo(int targetHeight) {
        cancelHeightAnimator();
        float startHeight = dialogueDisplayHeight;
        if (startHeight < 0.0F
                || Math.abs(startHeight - targetHeight) < 0.5F) {
            dialogueDisplayHeight = targetHeight;
            requestLayout();
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0.0F, 1.0F);
        animator.setDuration(HEIGHT_ANIMATION_DURATION_MS);
        animator.addUpdateListener(valueAnimator -> {
            float fraction = valueAnimator.getAnimatedFraction();
            float eased = easeInOut(fraction);
            dialogueDisplayHeight = startHeight
                    + (targetHeight - startHeight) * eased;
            if (fraction >= 1.0F && heightAnimator == animator) {
                dialogueDisplayHeight = targetHeight;
                heightAnimator = null;
            }
            requestLayout();
        });
        heightAnimator = animator;
        animator.start();
    }

    private void cancelHeightAnimator() {
        if (heightAnimator != null) {
            heightAnimator.cancel();
            heightAnimator = null;
        }
    }

    private static float easeInOut(float fraction) {
        float clamped = Math.clamp(fraction, 0.0F, 1.0F);
        return clamped < 0.5F
                ? 2.0F * clamped * clamped
                : 1.0F
                  - (float) Math.pow(-2.0F * clamped + 2.0F, 2.0F)
                    / 2.0F;
    }

    private static float horizontalAnchor(VisualAnchor anchor) {
        return switch (anchor) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> 0.0F;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> 0.5F;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> 1.0F;
        };
    }

    private static float verticalAnchor(VisualAnchor anchor) {
        return switch (anchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0.0F;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> 0.5F;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> 1.0F;
        };
    }
}
