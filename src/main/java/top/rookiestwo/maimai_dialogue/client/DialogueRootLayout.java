package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.core.Context;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueBoxLayout;
import top.rookiestwo.maimai_dialogue.dialogue.VisualAnchor;

import java.util.Objects;

final class DialogueRootLayout extends FrameLayout {
    private final DialogueSceneLayer sceneLayer;
    private final View dialogueBox;
    private final View historyEntry;
    private final View historyOverlay;
    private DialogueBoxLayout dialogueBoxLayout = DialogueBoxLayout.DEFAULT;
    private Runnable advanceAction = () -> {
    };

    DialogueRootLayout(
            Context context,
            DialogueSceneLayer sceneLayer,
            View dialogueBox,
            View historyEntry,
            View historyOverlay
    ) {
        super(context);
        this.sceneLayer = Objects.requireNonNull(sceneLayer, "sceneLayer");
        this.dialogueBox = Objects.requireNonNull(dialogueBox, "dialogueBox");
        this.historyEntry = Objects.requireNonNull(
                historyEntry,
                "historyEntry"
        );
        this.historyOverlay = Objects.requireNonNull(
                historyOverlay,
                "historyOverlay"
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
                historyOverlay,
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );
    }

    void setDialogueBoxLayout(DialogueBoxLayout layout) {
        dialogueBoxLayout = Objects.requireNonNull(layout, "layout");
        requestLayout();
    }

    void setAdvanceAction(Runnable advanceAction) {
        this.advanceAction = Objects.requireNonNull(
                advanceAction,
                "advanceAction"
        );
    }

    void setDialogueOpacity(float opacity) {
        float clamped = Math.clamp(opacity, 0.0F, 1.0F);
        dialogueBox.setAlpha(clamped);
        historyEntry.setAlpha(clamped);
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEY_SPACE) {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && !event.isCanceled()) {
                advanceAction.run();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
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
                Math.round(height * dialogueBoxLayout.maxHeight())
        );
        dialogueBox.measure(
                MeasureSpec.makeMeasureSpec(boxWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(boxMaxHeight, MeasureSpec.AT_MOST)
        );
        historyEntry.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
        );
        if (historyOverlay.getVisibility() != GONE) {
            historyOverlay.measure(
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
        int boxHeight = dialogueBox.getMeasuredHeight();
        float anchorX = horizontalAnchor(dialogueBoxLayout.anchor());
        float anchorY = verticalAnchor(dialogueBoxLayout.anchor());
        int boxLeft = Math.round(
                dialogueBoxLayout.x() * width - anchorX * boxWidth
        );
        int boxTop = Math.round(
                dialogueBoxLayout.y() * height - anchorY * boxHeight
        );
        dialogueBox.layout(
                boxLeft,
                boxTop,
                boxLeft + boxWidth,
                boxTop + boxHeight
        );
        int historyWidth = historyEntry.getMeasuredWidth();
        int historyHeight = historyEntry.getMeasuredHeight();
        int historyMargin = dp(12);
        int historyLeft = width - historyMargin - historyWidth;
        int historyTop = (height - historyHeight) / 2;
        historyEntry.layout(
                historyLeft,
                historyTop,
                historyLeft + historyWidth,
                historyTop + historyHeight
        );
        if (historyOverlay.getVisibility() != GONE) {
            historyOverlay.layout(0, 0, width, height);
        }
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
