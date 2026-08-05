package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.markflow.Markflow;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.widget.TextView;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Renders Dialogue body Markdown and reveals the rendered text progressively.
 */
final class DialogueTextPlayer {
    private static final int MAX_DURATION_MS = 10_000;

    private final TextView textView;
    private final Markflow markflow;
    private final Consumer<Boolean> textUpdated;
    private final PlaybackTimeline timeline = new PlaybackTimeline();
    private long playbackToken = Long.MIN_VALUE;
    private Spanned renderedText;
    private String plainText;

    DialogueTextPlayer(
            TextView textView,
            Consumer<Boolean> textUpdated
    ) {
        this.textView = Objects.requireNonNull(textView, "textView");
        this.textUpdated = Objects.requireNonNull(
                textUpdated,
                "textUpdated"
        );
        markflow = DialogueMarkdown.create(textView.getContext());
    }

    void render(
            long token,
            String markdown,
            int intervalMs,
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
            if (intervalMs == 0) {
                showFullText();
                finished.run();
                return;
            }
            start(intervalMs, finished);
            return;
        }

        if (skip) {
            cancelAnimator();
            showFullText();
        }
    }

    void clear() {
        timeline.cancel();
        playbackToken = Long.MIN_VALUE;
        renderedText = null;
        plainText = null;
        textView.setText("");
        textUpdated.accept(false);
    }

    void setPlaybackRate(float playbackRate) {
        timeline.setPlaybackRate(playbackRate);
    }

    private void start(int intervalMs, Runnable finished) {
        String text = Objects.requireNonNull(plainText);
        int codePoints = Character.codePointCount(text, 0, text.length());
        if (codePoints == 0) {
            showFullText();
            finished.run();
            return;
        }

        boolean[] reported = {false};
        int durationMs = durationMs(codePoints, intervalMs);
        timeline.start(durationMs, elapsedMs -> {
            float fraction = elapsedMs / (float) durationMs;
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
            textUpdated.accept(true);
            if (!reported[0] && visibleCodePoints == codePoints) {
                reported[0] = true;
                showFullText();
            }
        }, finished);
    }

    static int durationMs(int codePointCount, int intervalMs) {
        if (codePointCount < 0 || intervalMs < 0) {
            throw new IllegalArgumentException(
                    "codePointCount and intervalMs must not be negative."
            );
        }
        return (int) Math.min(
                MAX_DURATION_MS,
                (long) codePointCount * intervalMs
        );
    }

    private void showFullText() {
        Spanned text = renderedText;
        if (text == null) {
            textView.setText("");
        } else {
            markflow.setRenderedMarkdown(textView, text);
        }
        textUpdated.accept(false);
    }

    private void cancelAnimator() {
        timeline.cancel();
    }
}
