package top.rookiestwo.maimai_dialogue.client.config.ui;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;

final class ConfigWidgets {
    private ConfigWidgets() {
    }

    static final int CONTENT_MAX_WIDTH_DP = 780;
    static final int PAGE_PADDING_DP = 18;
    static final int CARD_COLOR = 0xCC17191D;
    static final int CARD_STROKE_COLOR = 0xFF555B66;
    static final int BUTTON_COLOR = 0x2017191D;
    static final int BUTTON_HOVER_COLOR = 0x40555B66;
    static final int BUTTON_PRESSED_COLOR = 0x70555B66;
    static final int BUTTON_HOVER_STROKE_COLOR = 0xFF8E97A7;
    static final int OVERLAY_COLOR = 0xB3000000;
    static final int ERROR_COLOR = 0xFFFF6B6B;

    // 独立的 keyed tag 随 View 一起释放；动态字体列表不进入全局注册表。
    private static final int METRICS_TAG = 0x6d640001;

    static void bindMetrics(View view, Runnable apply) {
        Runnable previous = (Runnable) view.getTag(METRICS_TAG);
        view.setTag(METRICS_TAG, previous == null ? apply : (Runnable) () -> {
            previous.run();
            apply.run();
        });
        apply.run();
    }

    static void refreshMetrics(View view) {
        if (view instanceof TextView text) {
            // 当前页面使用 TextView(Context)，其默认字号为 16sp。
            text.setTextSize(16);
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) refreshMetrics(group.getChildAt(i));
        }
        Runnable apply = (Runnable) view.getTag(METRICS_TAG);
        if (apply != null) apply.run();
    }

    static void padding(View view, int dp) {
        bindMetrics(view, () -> view.setPadding(view.dp(dp), view.dp(dp), view.dp(dp), view.dp(dp)));
    }

    static LinearLayout createCard(Context context, String category) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        padding(card, 12);
        bindMetrics(card, () -> {
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(CARD_COLOR);
        background.setCornerRadius(card.dp(5));
        background.setStroke(card.dp(1), CARD_STROKE_COLOR);
        card.setBackground(background);
        });

        TextView title = new TextView(context);
        title.setText(I18n.get(
                "gui.maimai_dialogue.config.category." + category
        ));
        bindMetrics(title, () -> title.setTextSize(18));
        padding(title, 8);
        card.addView(title, matchWidthWrapHeight());
        return card;
    }

    static LinearLayout createOptionRow(Context context, String option) {
        LinearLayout row = new ConfigOptionRow(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        padding(row, 6);
        TextView label = new TextView(context);
        label.setText(I18n.get(
                "gui.maimai_dialogue.config.option." + option
        ));
        String tooltipKey =
                "gui.maimai_dialogue.config.option." + option + ".tooltip";
        if (I18n.exists(tooltipKey)) {
            label.setTooltipText(I18n.get(tooltipKey));
        }
        row.addView(label, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0F
        ));
        return row;
    }

    static Button createOutlinedButton(Context context) {
        Button button = new Button(context);
        bindMetrics(button, () -> {
        StateListDrawable background = new StateListDrawable();
        background.addState(
                new int[]{R.attr.state_pressed},
                createButtonShape(
                        button,
                        BUTTON_PRESSED_COLOR,
                        BUTTON_HOVER_STROKE_COLOR
                )
        );
        background.addState(
                new int[]{R.attr.state_hovered},
                createButtonShape(
                        button,
                        BUTTON_HOVER_COLOR,
                        BUTTON_HOVER_STROKE_COLOR
                )
        );
        background.addState(
                StateSet.WILD_CARD,
                createButtonShape(button, BUTTON_COLOR, CARD_STROKE_COLOR)
        );
        button.setBackground(background);
        // Custom backgrounds have no content insets; keep text clear of the outline at every GUI scale.
        button.setPadding(button.dp(12), button.dp(6), button.dp(12), button.dp(6));
        });
        return button;
    }

    static ShapeDrawable createButtonShape(
            View view,
            int color,
            int strokeColor
    ) {
        ShapeDrawable shape = new ShapeDrawable();
        shape.setColor(color);
        shape.setCornerRadius(view.dp(6));
        shape.setStroke(view.dp(1), strokeColor);
        return shape;
    }

    static LinearLayout.LayoutParams controlParams(View view) {
        return new LinearLayout.LayoutParams(
                view.dp(170),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    static LinearLayout.LayoutParams cardParams(View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int margin = view.dp(8);
        params.setMargins(0, margin, 0, margin);
        return params;
    }

    static LinearLayout.LayoutParams matchWidthWrapHeight() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }
}
