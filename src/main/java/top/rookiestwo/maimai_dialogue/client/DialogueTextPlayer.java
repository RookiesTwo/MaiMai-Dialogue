package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.markflow.Markflow;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.widget.TextView;

import java.util.Objects;

/**
 * Renders Dialogue body Markdown and reveals the rendered text progressively.
 */
final class DialogueTextPlayer {
    private static final int MILLIS_PER_CODE_POINT = 30;
    private static final int MAX_DURATION_MS = 10_000;

    private final TextView textView;
    private final Markflow markflow;
    private ValueAnimator animator;
    private long playbackToken = Long.MIN_VALUE;
    private Spanned renderedText;
    private String plainText;

    DialogueTextPlayer(TextView textView) {
        this.textView = Objects.requireNonNull(textView, "textView");
        markflow = DialogueMarkdown.create(textView.getContext());
    }

    void render(
            long token,
            String markdown,
            boolean skip,
            Runnable finished
    ) {
        Objects.requireNonNull(markdown, "markdown");
        Objects.requireNonNull(finished, "finished");

        if (token != playbackToken) {
            cancelAnimator();
            playbackToken = token;
            renderedText = markflow.convert(markdown);
            plainText = renderedText.toString();
            if (skip || renderedText.isEmpty()) {
                showFullText();
                return;
            }
            start(finished);
            return;
        }

        if (skip) {
            cancelAnimator();
            showFullText();
        }
    }

    void clear() {
        cancelAnimator();
        playbackToken = Long.MIN_VALUE;
        renderedText = null;
        plainText = null;
        textView.setText("");
    }

    private void start(Runnable finished) {
        String text = Objects.requireNonNull(plainText);
        int codePoints = Character.codePointCount(text, 0, text.length());
        if (codePoints == 0) {
            showFullText();
            finished.run();
            return;
        }

        ValueAnimator next = ValueAnimator.ofFloat(0.0F, 1.0F);
        next.setDuration(Math.min(
                MAX_DURATION_MS,
                codePoints * MILLIS_PER_CODE_POINT
        ));
        boolean[] reported = {false};
        next.addUpdateListener(valueAnimator -> {
            float fraction = valueAnimator.getAnimatedFraction();
            int visibleCodePoints = Math.clamp(
                    (int) Math.ceil(codePoints * fraction),
                    1,
                    codePoints
            );
            int end = Character.offsetByCodePoints(
                    text,
                    0,
                    visibleCodePoints
            );
            SpannableString prefix = RenderedTextPrefix.create(
                    Objects.requireNonNull(renderedText),
                    end
            );
            textView.setText(prefix, TextView.BufferType.SPANNABLE);
            if (!reported[0] && visibleCodePoints == codePoints) {
                reported[0] = true;
                animator = null;
                showFullText();
                finished.run();
            }
        });
        animator = next;
        next.start();
    }

    private void showFullText() {
        Spanned text = renderedText;
        if (text == null) {
            textView.setText("");
        } else {
            markflow.setRenderedMarkdown(textView, text);
        }
    }

    private void cancelAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}
