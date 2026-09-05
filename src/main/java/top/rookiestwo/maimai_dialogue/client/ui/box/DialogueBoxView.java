package top.rookiestwo.maimai_dialogue.client.ui.box;

import top.rookiestwo.maimai_dialogue.client.ui.style.DialogueButtonStyle;

import top.rookiestwo.maimai_dialogue.client.ui.style.DialogueTypography;
import top.rookiestwo.maimai_dialogue.client.ui.text.DialogueTextPlayer;
import top.rookiestwo.maimai_dialogue.client.ui.text.DialogueTextViewport;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;
import top.rookiestwo.maimai_dialogue.dialogue.branch.DialogueOption;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class DialogueBoxView extends LinearLayout {
    private final LinearLayout header;
    private final TextView speakerName;
    private final Button expandButton;
    private final View headerDivider;
    private final DialogueTextViewport textViewport;
    private final TextView dialogueText;
    private final DialogueTextPlayer textPlayer;
    private final TextView errorText;
    private final DialogueOptionsView options;
    private Runnable advanceAction = () -> {
    };
    private long renderedTextToken = Long.MIN_VALUE;
    private ThemeDefinition theme = ThemeDefinition.DEFAULT;
    private BiConsumer<Boolean, Boolean> optionsExpandedChanged =
            (ignoredExpanded, ignoredAnimateCollapse) -> {
    };
    private DialogueTypography typography = DialogueTypography.resolve(
            ClientConfig.get()
    );

    public DialogueBoxView(
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

        textViewport = new DialogueTextViewport(context);
        textViewport.setOnClickListener(view -> advanceAction.run());
        dialogueText = new TextView(context);
        dialogueText.setGravity(Gravity.START);
        textViewport.addView(dialogueText, new DialogueTextViewport.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        addView(textViewport, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        errorText = new TextView(context);
        errorText.setGravity(Gravity.START);
        errorText.setVisibility(GONE);
        addView(errorText, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        textPlayer = new DialogueTextPlayer(
                dialogueText,
                textViewport::onTextUpdated
        );
        options = new DialogueOptionsView(context, optionSelected);
        options.bindExpandButton(
                expandButton,
                this::onExpandVisibilityChanged
        );
        addView(options, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        applyTheme(ThemeDefinition.DEFAULT);
    }

    // 切换 Dialogue 时清理旧播放状态并应用新 Theme。
    public void reset(ThemeDefinition nextTheme) {
        renderedTextToken = Long.MIN_VALUE;
        textViewport.resetForStep();
        textPlayer.clear();
        options.reset();
        applyTheme(nextTheme);
    }

    // 根据 screen state 更新 Speaker、正文、错误和选项。
    public void render(DialogueScreenState state, Runnable textFinished) {
        boolean hasSpeaker = state.speaker().isPresent();
        speakerName.setText(state.speaker().orElse(""));
        speakerName.setVisibility(hasSpeaker ? VISIBLE : GONE);
        updateHeaderVisibility();

        long textToken = state.scenePlayback()
                .map(ScenePlayback::token)
                .orElse(Long.MIN_VALUE);
        if (textToken != renderedTextToken) {
            renderedTextToken = textToken;
            textViewport.resetForStep();
        }
        textPlayer.render(
                textToken,
                state.text().orElse(""),
                state.typewriterIntervalMs(),
                state.playbackSkipped(),
                textFinished
        );

        boolean hasError = state.error().isPresent();
        errorText.setText(state.error().map(message -> I18n.get(
                message.key(), message.arguments().toArray()
        )).orElse(""));
        errorText.setVisibility(hasError ? VISIBLE : GONE);
        updateContentPadding();
        options.render(
                state.options(),
                state.loadingOptions(),
                state.requestingTarget()
        );
    }

    public void clear() {
        renderedTextToken = Long.MIN_VALUE;
        textViewport.resetForStep();
        textPlayer.clear();
        options.reset();
    }

    public void setTypography(DialogueTypography typography) {
        this.typography = typography;
        options.setTypography(typography);
        applyTheme(theme);
    }

    public void setPlaybackRate(float playbackRate) {
        textPlayer.setPlaybackRate(playbackRate);
    }

    public void setAdvanceAction(Runnable advanceAction) {
        this.advanceAction = Objects.requireNonNull(
                advanceAction,
                "advanceAction"
        );
    }

    public void setOptionsExpandedChanged(BiConsumer<Boolean, Boolean> listener) {
        optionsExpandedChanged = Objects.requireNonNull(listener, "listener");
        optionsExpandedChanged.accept(options.isExpanded(), false);
    }

    // 把 Theme 应用到对话框及其直接子视图。
    void applyTheme(ThemeDefinition nextTheme) {
        theme = nextTheme;
        var boxTheme = theme.box();
        var textTheme = theme.text();
        var spacing = theme.spacing();

        setBackground(DialogueButtonStyle.createShape(
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
        updateContentPadding();
        options.applyTheme(theme);
        textViewport.applyTheme(theme);

        speakerName.setTextColor(textTheme.primary().argb());
        typography.apply(speakerName, textTheme.speakerSizeSp());
        expandButton.setTextColor(textTheme.primary().argb());
        typography.apply(expandButton, textTheme.auxiliarySizeSp());
        DialogueButtonStyle.apply(expandButton, theme);
        dialogueText.setTextColor(textTheme.primary().argb());
        typography.apply(dialogueText, textTheme.dialogueSizeSp());
        errorText.setTextColor(textTheme.error().argb());
        typography.apply(errorText, textTheme.auxiliarySizeSp());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            textViewport.setHeightLimit(Integer.MAX_VALUE);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        int reservedHeight = getPaddingTop() + getPaddingBottom();
        reservedHeight = measureReservedHeight(
                header,
                widthMeasureSpec,
                heightMeasureSpec,
                reservedHeight
        );
        reservedHeight = measureReservedHeight(
                headerDivider,
                widthMeasureSpec,
                heightMeasureSpec,
                reservedHeight
        );
        reservedHeight = measureReservedHeight(
                errorText,
                widthMeasureSpec,
                heightMeasureSpec,
                reservedHeight
        );
        reservedHeight = measureReservedHeight(
                options,
                widthMeasureSpec,
                heightMeasureSpec,
                reservedHeight
        );
        textViewport.setHeightLimit(Math.max(
                0,
                MeasureSpec.getSize(heightMeasureSpec) - reservedHeight
        ));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void updateHeaderVisibility() {
        boolean show = speakerName.getVisibility() == VISIBLE
                || expandButton.getVisibility() == VISIBLE;
        header.setVisibility(show ? VISIBLE : GONE);
        headerDivider.setVisibility(show ? VISIBLE : GONE);
    }

    private void onExpandVisibilityChanged(boolean animateCollapse) {
        updateHeaderVisibility();
        optionsExpandedChanged.accept(
                options.isExpanded(),
                animateCollapse
        );
    }

    private int measureReservedHeight(
            View child,
            int widthMeasureSpec,
            int heightMeasureSpec,
            int usedHeight
    ) {
        if (child.getVisibility() == GONE) {
            return usedHeight;
        }
        measureChildWithMargins(
                child,
                widthMeasureSpec,
                0,
                heightMeasureSpec,
                usedHeight
        );
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) child.getLayoutParams();
        return usedHeight
                + child.getMeasuredHeight()
                + params.topMargin
                + params.bottomMargin;
    }

    private void updateContentPadding() {
        int horizontal = dp(theme.spacing().contentHorizontalDp());
        int vertical = dp(theme.spacing().contentVerticalDp());
        boolean hasError = errorText.getVisibility() == VISIBLE;
        textViewport.setPadding(
                horizontal,
                vertical,
                horizontal,
                hasError ? 0 : vertical
        );
        errorText.setPadding(horizontal, 0, horizontal, vertical);
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
