package top.rookiestwo.maimai_dialogue.client.ui.text;

import icyllis.modernui.core.Context;
import icyllis.modernui.markflow.Markflow;
import icyllis.modernui.markflow.MarkflowPlugin;
import icyllis.modernui.markflow.MarkflowTheme;
import icyllis.modernui.markflow.MarkflowVisitor;
import org.commonmark.node.ThematicBreak;

/**
 * Creates the shared Markdown renderer used by Dialogue body text.
 */
public final class DialogueMarkdown {
    private static final MarkflowPlugin NO_DIVIDERS = new MarkflowPlugin() {
        @Override
        public void configureTheme(MarkflowTheme.Builder builder) {
            // Markflow decorates level-one and level-two headings by default.
            builder.headingBreakColor(0).thematicBreakColor(0);
        }

        @Override
        public void configureVisitor(MarkflowVisitor.Builder builder) {
            // An absent visitor drops thematic-break nodes without leaving a line.
            builder.on(ThematicBreak.class, null);
        }
    };

    private DialogueMarkdown() {
    }

    public static Markflow create(Context context) {
        return Markflow.builder(context)
                .usePlugin(NO_DIVIDERS)
                .build();
    }
}
