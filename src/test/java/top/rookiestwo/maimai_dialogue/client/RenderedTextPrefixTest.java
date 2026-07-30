package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderedTextPrefixTest {
    @Test
    void keepsAndClipsRenderedSpans() {
        Object heading = new Object();
        SpannableString source = new SpannableString("Heading body");
        source.setSpan(
                heading,
                0,
                7,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE
        );

        SpannableString prefix = RenderedTextPrefix.create(source, 4);

        assertEquals("Head", prefix.toString());
        assertEquals(0, prefix.getSpanStart(heading));
        assertEquals(4, prefix.getSpanEnd(heading));
    }

    @Test
    void remainsGrowableAfterTextViewStyleCopy() {
        Object heading = new Object();
        SpannableString source = new SpannableString("Heading");
        source.setSpan(
                heading,
                0,
                source.length(),
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE
        );

        SpannableString prefix = RenderedTextPrefix.create(source, 1);
        SpannableString textViewCopy = new SpannableString(prefix);

        assertDoesNotThrow(() -> textViewCopy.setSpan(
                new Object(),
                0,
                1,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
        ));
    }
}
