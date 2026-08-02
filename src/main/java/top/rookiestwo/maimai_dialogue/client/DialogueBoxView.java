package top.rookiestwo.maimai_dialogue.client;

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
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueOption;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeOption;

import java.util.function.Consumer;

final class DialogueBoxView extends LinearLayout {
    private final LinearLayout header;
    private final TextView speakerName;
    private final Button expandButton;
    private final View headerDivider;
    private final LinearLayout content;
    private final TextView dialogueText;
    private final DialogueTextPlayer textPlayer;
    private final TextView errorText;
    private final DialogueOptionsView options;
    private ThemeDefinition theme = ThemeDefinition.DEFAULT;

    DialogueBoxView(
            Context context,
            Consumer<DialogueOption> optionSelected
    ) {
        super(context);
        setOrientation(VERTICAL);

        header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        speakerName = new TextView(context);
        speakerName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.addView(speakerName, new LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        expandButton = new Button(context);
        expandButton.setText(I18n.get("gui.maimai_dialogue.expand"));
        expandButton.setVisibility(GONE);
        header.addView(expandButton, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        headerDivider = createDivider(this);
        addView(header, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        addView(headerDivider);

        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        dialogueText = new TextView(context);
        dialogueText.setGravity(Gravity.START);
        content.addView(dialogueText, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        errorText = new TextView(context);
        errorText.setGravity(Gravity.START);
        errorText.setVisibility(GONE);
        content.addView(errorText, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        addView(content, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        textPlayer = new DialogueTextPlayer(dialogueText);
        options = new DialogueOptionsView(context, optionSelected);
        options.bindExpandButton(expandButton, this::updateHeaderVisibility);
        addView(options, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        applyTheme(ThemeDefinition.DEFAULT);
    }

    // 切换 Dialogue 时清理旧播放状态并应用新 Theme。
    void reset(ThemeDefinition nextTheme) {
        textPlayer.clear();
        options.reset();
        applyTheme(nextTheme);
    }

    // 根据 screen state 更新 Speaker、正文、错误和选项。
    void render(DialogueScreenState state, Runnable textFinished) {
        boolean hasSpeaker = state.speaker().isPresent();
        speakerName.setText(state.speaker().orElse(""));
        speakerName.setVisibility(hasSpeaker ? VISIBLE : GONE);
        updateHeaderVisibility();

        long textToken = state.scenePlayback()
                .map(ScenePlayback::token)
                .orElse(Long.MIN_VALUE);
        textPlayer.render(
                textToken,
                state.text().orElse(""),
                state.typewriterIntervalMs(),
                state.playbackSkipped(),
                textFinished
        );

        boolean hasError = state.error().isPresent();
        errorText.setText(state.error().orElse(""));
        errorText.setVisibility(hasError ? VISIBLE : GONE);
        options.render(
                state.options(),
                state.loadingOptions(),
                state.requestingTarget()
        );
    }

    void clear() {
        textPlayer.clear();
        options.reset();
    }

    // 把 Theme 应用到对话框及其直接子视图。
    void applyTheme(ThemeDefinition nextTheme) {
        theme = nextTheme;
        var boxTheme = theme.box();
        var textTheme = theme.text();
        var spacing = theme.spacing();

        setBackground(createShape(
                this,
                boxTheme.background().argb(),
                boxTheme.border().argb(),
                boxTheme.cornerRadiusDp(),
                boxTheme.borderWidthDp()
        ));
        setDividerTheme(headerDivider, boxTheme.divider().argb());
        header.setPadding(
                header.dp(spacing.headerHorizontalDp()),
                header.dp(spacing.headerVerticalDp()),
                header.dp(spacing.headerHorizontalDp()),
                header.dp(spacing.headerVerticalDp())
        );
        content.setPadding(
                content.dp(spacing.contentHorizontalDp()),
                content.dp(spacing.contentVerticalDp()),
                content.dp(spacing.contentHorizontalDp()),
                content.dp(spacing.contentVerticalDp())
        );
        options.applyTheme(theme);

        speakerName.setTextColor(textTheme.primary().argb());
        speakerName.setTextSize(textTheme.speakerSizeSp());
        expandButton.setTextColor(textTheme.primary().argb());
        expandButton.setTextSize(textTheme.auxiliarySizeSp());
        applyControlButtonTheme(expandButton, theme);
        dialogueText.setTextColor(textTheme.primary().argb());
        dialogueText.setTextSize(textTheme.dialogueSizeSp());
        errorText.setTextColor(textTheme.error().argb());
        errorText.setTextSize(textTheme.auxiliarySizeSp());
    }

    static void applyControlButtonTheme(
            Button button,
            ThemeDefinition theme
    ) {
        ThemeOption optionTheme = theme.option();
        button.setBackground(createOptionBackground(button, optionTheme));
        int horizontal = button.dp(optionTheme.horizontalPaddingDp());
        int vertical = button.dp(Math.max(
                2,
                optionTheme.verticalPaddingDp() / 2
        ));
        button.setPadding(horizontal, vertical, horizontal, vertical);
    }

    private void updateHeaderVisibility() {
        boolean show = speakerName.getVisibility() == VISIBLE
                || expandButton.getVisibility() == VISIBLE;
        header.setVisibility(show ? VISIBLE : GONE);
        headerDivider.setVisibility(show ? VISIBLE : GONE);
    }

    private static StateListDrawable createOptionBackground(
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
                        1
                )
        );
        background.addState(
                new int[]{R.attr.state_hovered},
                createShape(
                        view,
                        theme.hoverBackground().argb(),
                        theme.hoverBorder().argb(),
                        theme.cornerRadiusDp(),
                        1
                )
        );
        background.addState(
                StateSet.WILD_CARD,
                createShape(
                        view,
                        theme.background().argb(),
                        theme.border().argb(),
                        theme.cornerRadiusDp(),
                        1
                )
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
        divider.setLayoutParams(new LayoutParams(
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
}
