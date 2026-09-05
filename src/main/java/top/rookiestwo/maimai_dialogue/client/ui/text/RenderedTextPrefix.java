package top.rookiestwo.maimai_dialogue.client.ui.text;

import icyllis.modernui.text.NoCopySpan;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;

/**
 * Builds a styled prefix without using ModernUI's span-copy constructor.
 *
 * <p>ModernUI 3.13.0 trims a copied single-span string to capacity one, then
 * fails to grow it when TextView adds its ChangeWatcher. Starting from plain
 * characters and keeping two inert spans avoids that library defect.
 */
final class RenderedTextPrefix {
    private static final Object CAPACITY_GUARD_ONE = new Object();
    private static final Object CAPACITY_GUARD_TWO = new Object();

    private RenderedTextPrefix() {
    }

    static SpannableString create(Spanned source, int end) {
        int safeEnd = Math.clamp(end, 0, source.length());
        SpannableString result = new SpannableString(
                source.toString().substring(0, safeEnd)
        );

        for (Object span : source.getSpans(0, safeEnd, Object.class)) {
            if (span instanceof NoCopySpan) {
                continue;
            }
            int start = Math.max(0, source.getSpanStart(span));
            int spanEnd = Math.min(safeEnd, source.getSpanEnd(span));
            if (start > spanEnd) {
                continue;
            }
            if (start == spanEnd
                    && (source.getSpanFlags(span)
                    & Spanned.SPAN_POINT_MARK_MASK)
                    == Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) {
                continue;
            }
            result.setSpan(
                    span,
                    start,
                    spanEnd,
                    source.getSpanFlags(span)
            );
        }

        result.setSpan(
                CAPACITY_GUARD_ONE,
                0,
                safeEnd,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE
        );
        result.setSpan(
                CAPACITY_GUARD_TWO,
                0,
                safeEnd,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE
        );
        return result;
    }
}
