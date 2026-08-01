package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.markflow.Markflow;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue.client.session.DialogueHistoryEntry;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeOption;

import java.util.List;

final class DialogueHistoryView extends FrameLayout {
    private final LinearLayout panel;
    private final TextView title;
    private final Button closeButton;
    private final View headerDivider;
    private final ScrollView scroll;
    private final LinearLayout list;
    private final Markflow markflow;
    private final Runnable closedAction;
    private ThemeDefinition theme = ThemeDefinition.DEFAULT;
    private int renderedSize = -1;

    DialogueHistoryView(Context context, Runnable closedAction) {
        super(context);
        this.closedAction = closedAction;
        setVisibility(View.GONE);
        setClickable(true);
        setOnClickListener(view -> close());
        ShapeDrawable scrim = new ShapeDrawable();
        scrim.setColor(0x4D000000);
        setBackground(scrim);

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        title = new TextView(context);
        title.setText("History");
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        closeButton = new Button(context);
        closeButton.setText("Close");
        closeButton.setOnClickListener(view -> close());
        titleRow.addView(closeButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(titleRow, matchWidthWrapHeight());
        headerDivider = createDivider(panel);
        panel.addView(headerDivider);

        scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        int margin = dp(24);
        panelParams.setMargins(margin, margin, margin, margin);
        panelParams.gravity = Gravity.CENTER;
        addView(panel, panelParams);
        markflow = Markflow.create(context);
        applyTheme(ThemeDefinition.DEFAULT);
    }

    // 打开历史面板并滚动到最新一条记录。
    void open() {
        setVisibility(View.VISIBLE);
        requestFocus();
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    void close() {
        setVisibility(View.GONE);
        closedAction.run();
    }

    boolean isOpen() {
        return getVisibility() == View.VISIBLE;
    }

    // 只在历史记录数量变化时重新构建列表。
    void render(List<DialogueHistoryEntry> entries) {
        if (entries.size() == renderedSize) {
            return;
        }
        list.removeAllViews();
        for (int index = 0; index < entries.size(); index++) {
            list.addView(createEntry(entries.get(index)), matchWidthWrapHeight());
            if (index + 1 < entries.size()) {
                View divider = createDivider(list);
                setDividerTheme(divider, theme.box().divider().argb());
                list.addView(divider);
            }
        }
        renderedSize = entries.size();
        if (isOpen()) {
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    // 应用当前 Dialogue Theme 到历史面板及滚动条。
    void applyTheme(ThemeDefinition theme) {
        this.theme = theme;
        renderedSize = -1;
        var box = theme.box();
        var text = theme.text();
        var spacing = theme.spacing();
        panel.setBackground(createShape(
                panel,
                box.background().argb(),
                box.border().argb(),
                box.cornerRadiusDp(),
                box.borderWidthDp()
        ));
        setDividerTheme(headerDivider, box.divider().argb());
        title.setTextColor(text.primary().argb());
        title.setTextSize(text.speakerSizeSp());
        int horizontal = title.dp(spacing.headerHorizontalDp());
        int vertical = title.dp(spacing.headerVerticalDp());
        title.setPadding(horizontal, vertical, horizontal, vertical);
        closeButton.setTextColor(text.primary().argb());
        closeButton.setTextSize(text.auxiliarySizeSp());
        applyControlButtonTheme(closeButton);
        ViewGroup.LayoutParams rawParams = closeButton.getLayoutParams();
        if (rawParams instanceof LinearLayout.LayoutParams params) {
            params.rightMargin = closeButton.dp(
                    spacing.headerHorizontalDp()
            );
            closeButton.setLayoutParams(params);
        }
        applyScrollbarTheme();
    }

    private LinearLayout createEntry(DialogueHistoryEntry entry) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        int horizontal = row.dp(theme.spacing().contentHorizontalDp());
        int vertical = row.dp(theme.spacing().contentVerticalDp());
        row.setPadding(horizontal, vertical, horizontal, vertical);
        TextView name = new TextView(getContext());
        name.setText(entry.speaker().orElse(""));
        name.setTextColor(theme.text().primary().argb());
        name.setTextSize(theme.text().speakerSizeSp());
        name.setGravity(Gravity.START | Gravity.TOP);
        row.addView(name, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        TextView content = new TextView(getContext());
        content.setGravity(Gravity.START | Gravity.TOP);
        if (entry.type() == DialogueHistoryEntry.Type.DIALOGUE) {
            content.setTextColor(theme.text().primary().argb());
            content.setTextSize(theme.text().dialogueSizeSp());
            markflow.setMarkdown(content, entry.content());
        } else {
            content.setText(styledOption(entry.content()));
            content.setTextColor(theme.option().hoverBorder().argb());
            content.setTextSize(theme.text().optionSizeSp());
        }
        row.addView(content, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                4
        ));
        return row;
    }

    private CharSequence styledOption(String text) {
        String prefix = "›  ";
        SpannableString label = new SpannableString(prefix + text);
        label.setSpan(
                new ForegroundColorSpan(theme.controls().icon().argb()),
                0,
                prefix.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return label;
    }

    private void applyControlButtonTheme(Button button) {
        ThemeOption option = theme.option();
        button.setBackground(createOptionBackground(button, option));
        int horizontal = button.dp(option.horizontalPaddingDp());
        int vertical = button.dp(Math.max(2, option.verticalPaddingDp() / 2));
        button.setPadding(horizontal, vertical, horizontal, vertical);
    }

    private void applyScrollbarTheme() {
        int width = scroll.dp(theme.controls().scrollbarWidthDp());
        ShapeDrawable thumb = new ShapeDrawable();
        thumb.setShape(ShapeDrawable.VLINE);
        thumb.setStroke(width, theme.controls().scrollbarThumb().argb());
        thumb.setSize(width, -1);
        thumb.setCornerRadius(width / 2.0F);
        scroll.setVerticalScrollbarThumbDrawable(thumb);
        ShapeDrawable track = new ShapeDrawable();
        track.setShape(ShapeDrawable.VLINE);
        track.setStroke(width, theme.controls().scrollbarTrack().argb());
        track.setSize(width, -1);
        track.setCornerRadius(width / 2.0F);
        scroll.setVerticalScrollbarTrackDrawable(track);
    }

    private static StateListDrawable createOptionBackground(
            View view,
            ThemeOption theme
    ) {
        StateListDrawable background = new StateListDrawable();
        background.addState(
                new int[]{R.attr.state_pressed},
                createShape(view, theme.pressedBackground().argb(),
                        theme.hoverBorder().argb(), theme.cornerRadiusDp(), 1)
        );
        background.addState(
                new int[]{R.attr.state_hovered},
                createShape(view, theme.hoverBackground().argb(),
                        theme.hoverBorder().argb(), theme.cornerRadiusDp(), 1)
        );
        background.addState(
                StateSet.WILD_CARD,
                createShape(view, theme.background().argb(),
                        theme.border().argb(), theme.cornerRadiusDp(), 1)
        );
        return background;
    }

    private static ShapeDrawable createShape(
            View view,
            int color,
            int strokeColor,
            int cornerRadiusDp,
            int strokeWidthDp
    ) {
        ShapeDrawable shape = new ShapeDrawable();
        shape.setColor(color);
        shape.setCornerRadius(view.dp(cornerRadiusDp));
        shape.setStroke(view.dp(strokeWidthDp), strokeColor);
        return shape;
    }

    private static View createDivider(ViewGroup parent) {
        View divider = new View(parent.getContext());
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                parent.dp(1)
        ));
        return divider;
    }

    private static void setDividerTheme(View divider, int color) {
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(color);
        divider.setBackground(background);
    }

    private static LinearLayout.LayoutParams matchWidthWrapHeight() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }
}
