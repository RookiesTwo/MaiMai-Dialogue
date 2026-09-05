package top.rookiestwo.maimai_dialogue.client.ui.style;

import icyllis.modernui.R;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeOption;

public final class DialogueButtonStyle {
    private DialogueButtonStyle() {
    }

    public static void apply(
            Button button,
            ThemeDefinition theme
    ) {
        ThemeOption optionTheme = theme.option();
        button.setBackground(background(button, optionTheme));
        int horizontal = button.dp(optionTheme.horizontalPaddingDp());
        int vertical = button.dp(Math.max(
                2,
                optionTheme.verticalPaddingDp() / 2
        ));
        button.setPadding(horizontal, vertical, horizontal, vertical);
    }

    public static StateListDrawable background(
            View view,
            ThemeOption theme
    ) {
        StateListDrawable background = new StateListDrawable();
        background.addState(
                new int[]{R.attr.state_pressed},
                createShape(
                        view,
                        theme.pressedBackground().argb(),
                        theme.hoverBorder().argb(),
                        theme.cornerRadiusDp(),
                        theme.borderWidthDp()
                )
        );
        background.addState(
                new int[]{R.attr.state_hovered},
                createShape(
                        view,
                        theme.hoverBackground().argb(),
                        theme.hoverBorder().argb(),
                        theme.cornerRadiusDp(),
                        theme.borderWidthDp()
                )
        );
        background.addState(
                StateSet.WILD_CARD,
                createShape(
                        view,
                        theme.background().argb(),
                        theme.border().argb(),
                        theme.cornerRadiusDp(),
                        theme.borderWidthDp()
                )
        );
        return background;
    }

    public static ShapeDrawable createShape(
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
}
