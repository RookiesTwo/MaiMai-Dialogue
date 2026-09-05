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

    static LinearLayout createCard(Context context, String category) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = card.dp(12);
        card.setPadding(padding, padding, padding, padding);
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(CARD_COLOR);
        background.setCornerRadius(card.dp(5));
        background.setStroke(card.dp(1), CARD_STROKE_COLOR);
        card.setBackground(background);

        TextView title = new TextView(context);
        title.setText(I18n.get(
                "gui.maimai_dialogue.config.category." + category
        ));
        title.setTextSize(18);
        int titlePadding = title.dp(8);
        title.setPadding(
                titlePadding,
                titlePadding,
                titlePadding,
                titlePadding
        );
        card.addView(title, matchWidthWrapHeight());
        return card;
    }

    static LinearLayout createOptionRow(Context context, String option) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padding = row.dp(6);
        row.setPadding(padding, padding, padding, padding);
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
