package top.rookiestwo.maimai_dialogue.client.ui.style;

import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.widget.ScrollView;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

// 为 Dialogue 内的所有垂直 viewport 复用同一套滚动条 Theme。
public final class DialogueScrollbarStyle {
    private DialogueScrollbarStyle() {
    }

    public static void apply(ScrollView scroll, ThemeDefinition theme) {
        int width = scroll.dp(theme.controls().scrollbarWidthDp());
        ShapeDrawable thumb = createLine(
                width,
                theme.controls().scrollbarThumb().argb()
        );
        scroll.setVerticalScrollbarThumbDrawable(thumb);
        ShapeDrawable track = createLine(
                width,
                theme.controls().scrollbarTrack().argb()
        );
        scroll.setVerticalScrollbarTrackDrawable(track);
    }

    private static ShapeDrawable createLine(int width, int color) {
        ShapeDrawable line = new ShapeDrawable();
        line.setShape(ShapeDrawable.VLINE);
        line.setStroke(width, color);
        line.setSize(width, -1);
        line.setCornerRadius(width / 2.0F);
        return line;
    }
}
