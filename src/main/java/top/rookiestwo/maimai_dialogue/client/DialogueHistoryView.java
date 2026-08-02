package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.markflow.Markflow;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.Layout;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;
import top.rookiestwo.maimai_dialogue.client.session.DialogueHistoryEntry;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeOption;

import java.util.List;

final class DialogueHistoryView extends FrameLayout {
    private static final int ENTRY_VERTICAL_PADDING_DP = 20;
    private static final int COLUMN_GUTTER_DP = 12;
    private static final int QUOTE_CONTENT_INSET_DP = 18;
    private static final float CONTENT_WIDTH_FRACTION = 0.70F;

    private final LinearLayout panel;
    private final LinearLayout titleRow;
    private final Button closeButton;
    private final ScrollView scroll;
    private final LinearLayout list;
    private final Markflow markflow;
    private ThemeDefinition theme = ThemeDefinition.DEFAULT;
    private int renderedSize = -1;

    DialogueHistoryView(Context context, Runnable closedAction) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        closeButton = new Button(context);
        closeButton.setText(I18n.get("gui.maimai_dialogue.close"));
        closeButton.setOnClickListener(view -> closedAction.run());
        titleRow.addView(closeButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(titleRow, matchWidthWrapHeight());

        scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL
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
        addView(panel, panelParams);
        markflow = DialogueMarkdown.create(context);
        applyTheme(ThemeDefinition.DEFAULT);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ViewGroup.LayoutParams params = list.getLayoutParams();
        params.width = Math.max(
                1,
                Math.round(
                        MeasureSpec.getSize(widthMeasureSpec)
                                * CONTENT_WIDTH_FRACTION
                )
        );
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    // 页面创建后聚焦，并滚动到最新一条记录。
    void showLatest() {
        requestFocus();
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    // 只在历史记录数量变化时重新构建列表。
    void render(List<DialogueHistoryEntry> entries) {
        if (entries.size() == renderedSize) {
            return;
        }
        list.removeAllViews();
        for (DialogueHistoryEntry entry : entries) {
            list.addView(createEntry(entry), matchWidthWrapHeight());
        }
        renderedSize = entries.size();
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    // 应用当前 Dialogue Theme 到历史面板及滚动条。
    void applyTheme(ThemeDefinition theme) {
        this.theme = theme;
        renderedSize = -1;
        var box = theme.box();
        var text = theme.text();
        var spacing = theme.spacing();
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(box.background().argb());
        panel.setBackground(background);
        int horizontal = titleRow.dp(spacing.headerHorizontalDp());
        int vertical = titleRow.dp(spacing.headerVerticalDp());
        titleRow.setPadding(horizontal, vertical, horizontal, vertical);
        closeButton.setTextColor(text.primary().argb());
        closeButton.setTextSize(text.auxiliarySizeSp());
        applyControlButtonTheme(closeButton);
        applyScrollbarTheme();
    }

    private LinearLayout createEntry(DialogueHistoryEntry entry) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        int horizontal = row.dp(theme.spacing().contentHorizontalDp());
        int vertical = row.dp(ENTRY_VERTICAL_PADDING_DP);
        row.setPadding(horizontal, vertical, horizontal, vertical);
        String speaker = entry.speaker().orElse("");
        boolean hasSpeaker = !speaker.isBlank();
        TextView name = new TextView(getContext());
        name.setText(hasSpeaker ? "【" + speaker + "】" : "");
        name.setTextColor(theme.text().primary().argb());
        name.setTextSize(theme.text().speakerSizeSp());
        name.setGravity(Gravity.END | Gravity.TOP);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        nameParams.rightMargin = row.dp(COLUMN_GUTTER_DP);
        row.addView(name, nameParams);
        QuotedContentLayout contentColumn = new QuotedContentLayout(
                getContext()
        );
        TextView content = new TextView(getContext());
        content.setGravity(Gravity.START | Gravity.TOP);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        if (entry.type() == DialogueHistoryEntry.Type.DIALOGUE) {
            content.setTextColor(theme.text().primary().argb());
            content.setTextSize(theme.text().dialogueSizeSp());
            Spanned rendered = markflow.convert(entry.content());
            markflow.setRenderedMarkdown(content, rendered);
            if (hasSpeaker) {
                int quoteInset = content.dp(QUOTE_CONTENT_INSET_DP);
                contentParams.leftMargin = quoteInset;
                contentParams.rightMargin = quoteInset;
                contentColumn.addView(content, contentParams);
                contentColumn.addView(
                        createQuote("「"),
                        quoteLayoutParams(Gravity.START | Gravity.TOP)
                );
                TextView closingQuote = createQuote("」");
                contentColumn.addView(closingQuote, quoteLayoutParams(0));
                contentColumn.setClosingQuote(content, closingQuote);
            } else {
                contentColumn.addView(content, contentParams);
            }
        } else {
            content.setText(styledOption(entry.content()));
            content.setTextColor(theme.option().hoverBorder().argb());
            content.setTextSize(theme.text().optionSizeSp());
            contentColumn.addView(content, contentParams);
        }
        row.addView(contentColumn, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                4
        ));
        return row;
    }

    private TextView createQuote(String quote) {
        TextView view = new TextView(getContext());
        view.setText(quote);
        view.setTextColor(theme.text().primary().argb());
        view.setTextSize(theme.text().dialogueSizeSp());
        return view;
    }

    private static FrameLayout.LayoutParams quoteLayoutParams(int gravity) {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                gravity
        );
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
                        theme.hoverBorder().argb(), theme.cornerRadiusDp())
        );
        background.addState(
                new int[]{R.attr.state_hovered},
                createShape(view, theme.hoverBackground().argb(),
                        theme.hoverBorder().argb(), theme.cornerRadiusDp())
        );
        background.addState(
                StateSet.WILD_CARD,
                createShape(view, theme.background().argb(),
                        theme.border().argb(), theme.cornerRadiusDp())
        );
        return background;
    }

    private static ShapeDrawable createShape(
            View view,
            int color,
            int strokeColor,
            int cornerRadiusDp
    ) {
        ShapeDrawable shape = new ShapeDrawable();
        shape.setColor(color);
        shape.setCornerRadius(view.dp(cornerRadiusDp));
        shape.setStroke(view.dp(1), strokeColor);
        return shape;
    }

    private static LinearLayout.LayoutParams matchWidthWrapHeight() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private static final class QuotedContentLayout extends FrameLayout {
        private TextView content;
        private TextView closingQuote;

        private QuotedContentLayout(Context context) {
            super(context);
        }

        private void setClosingQuote(
                TextView content,
                TextView closingQuote
        ) {
            this.content = content;
            this.closingQuote = closingQuote;
        }

        @Override
        protected void onLayout(
                boolean changed,
                int left,
                int top,
                int right,
                int bottom
        ) {
            super.onLayout(changed, left, top, right, bottom);
            TextView textView = content;
            TextView quoteView = closingQuote;
            if (textView == null || quoteView == null) {
                return;
            }
            Layout layout = textView.getLayout();
            if (layout == null || layout.getLineCount() == 0) {
                return;
            }

            CharSequence text = textView.getText();
            int textEnd = text.length();
            while (textEnd > 0
                    && Character.isWhitespace(text.charAt(textEnd - 1))) {
                textEnd--;
            }
            int line = layout.getLineForOffset(textEnd);
            int quoteLeft = textView.getLeft()
                    + textView.getTotalPaddingLeft()
                    + Math.round(layout.getPrimaryHorizontal(textEnd));
            int quoteBaseline = textView.getTop()
                    + textView.getExtendedPaddingTop()
                    + layout.getLineBaseline(line);
            int ownBaseline = quoteView.getBaseline();
            int quoteTop = ownBaseline >= 0
                    ? quoteBaseline - ownBaseline
                    : textView.getTop() + layout.getLineTop(line);
            quoteLeft = Math.clamp(
                    quoteLeft,
                    0,
                    Math.max(0, getWidth() - quoteView.getMeasuredWidth())
            );
            quoteTop = Math.clamp(
                    quoteTop,
                    0,
                    Math.max(0, getHeight() - quoteView.getMeasuredHeight())
            );
            quoteView.layout(
                    quoteLeft,
                    quoteTop,
                    quoteLeft + quoteView.getMeasuredWidth(),
                    quoteTop + quoteView.getMeasuredHeight()
            );
        }
    }
}
