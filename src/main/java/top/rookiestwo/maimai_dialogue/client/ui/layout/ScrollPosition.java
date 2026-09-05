package top.rookiestwo.maimai_dialogue.client.ui.layout;

import icyllis.modernui.text.Layout;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.util.ArrayList;
import java.util.List;

// 在重新换行前记录阅读位置；正文按字符定位，列表按条目和条目内比例定位。
final class ScrollPosition {
    private final ScrollView scroll;
    private final View content;
    private final int index;
    private final float fraction;
    private final int textOffset;
    private final boolean bottom;

    private ScrollPosition(ScrollView scroll) {
        this.scroll = scroll;
        content = scroll.getChildAt(0);
        int y = scroll.getScrollY();
        int range = range(scroll, content);
        bottom = range > 0 && y >= range - 1;
        if (content instanceof TextView text && text.getLayout() != null) {
            Layout layout = text.getLayout();
            textOffset = layout.getLineStart(layout.getLineForVertical(Math.max(0, y - text.getTotalPaddingTop())));
        } else {
            textOffset = -1;
        }
        int item = 0;
        View anchor = content;
        if (content instanceof ViewGroup group && group.getChildCount() > 0) {
            while (item + 1 < group.getChildCount() && group.getChildAt(item + 1).getTop() <= y) item++;
            anchor = group.getChildAt(item);
        }
        index = item;
        fraction = Math.clamp((y - anchor.getTop()) / (float) Math.max(1, anchor.getHeight()), 0, 1);
    }

    static List<ScrollPosition> capture(View root) {
        List<ScrollPosition> positions = new ArrayList<>();
        collect(root, positions);
        return positions;
    }

    private static void collect(View view, List<ScrollPosition> positions) {
        if (view instanceof ScrollView scroll && scroll.getChildCount() > 0 && scroll.getHeight() > 0) {
            positions.add(new ScrollPosition(scroll));
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), positions);
        }
    }

    void restore() {
        if (scroll.getChildCount() == 0 || scroll.getChildAt(0) != content) return;
        int y;
        if (bottom) {
            y = range(scroll, content);
        } else if (textOffset >= 0 && content instanceof TextView text && text.getLayout() != null) {
            Layout layout = text.getLayout();
            y = layout.getLineTop(layout.getLineForOffset(Math.min(textOffset, text.getText().length())));
        } else {
            View anchor = content;
            if (content instanceof ViewGroup group && group.getChildCount() > 0) {
                anchor = group.getChildAt(Math.min(index, group.getChildCount() - 1));
            }
            y = anchor.getTop() + Math.round(anchor.getHeight() * fraction);
        }
        scroll.scrollTo(scroll.getScrollX(), y);
    }

    private static int range(ScrollView scroll, View content) {
        return Math.max(0, content.getHeight() - scroll.getHeight() + scroll.getPaddingTop() + scroll.getPaddingBottom());
    }
}
