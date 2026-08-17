package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.markflow.Markflow;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import javax.annotation.Nullable;
import java.util.Objects;

// 对话页内共用的模态确认框，支持有标题的长正文和无标题的紧凑正文。
final class DialogueConfirmationView extends FrameLayout {
    private static final float PANEL_WIDTH_FRACTION = 0.62F;
    private static final float COMPACT_PANEL_WIDTH_FRACTION =
            PANEL_WIDTH_FRACTION / 3.0F;
    private static final float PANEL_HEIGHT_FRACTION = 0.65F;
    private static final int PANEL_MAX_WIDTH_DP = 720;
    private static final int PANEL_MAX_HEIGHT_DP = 560;
    private static final int OVERLAY_COLOR = 0x99000000;

    private final LinearLayout panel;
    @Nullable
    private final TextView title;
    private final ScrollView scroll;
    private final TextView body;
    private final Button cancelButton;
    private final Button confirmButton;
    private final Markflow markflow;
    private final String markdownBody;
    private final boolean expandedBody;
    private DialogueTypography typography = DialogueTypography.resolve(
            ClientConfig.get()
    );

    DialogueConfirmationView(
            Context context,
            @Nullable String titleText,
            String markdownBody,
            String cancelText,
            String confirmText,
            boolean expandedBody,
            Runnable cancelledAction,
            Runnable confirmedAction
    ) {
        super(context);
        this.markdownBody = Objects.requireNonNull(
                markdownBody,
                "markdownBody"
        );
        this.expandedBody = expandedBody;
        Objects.requireNonNull(cancelledAction, "cancelledAction");
        Objects.requireNonNull(confirmedAction, "confirmedAction");
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOnClickListener(ignored -> {
        });

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);

        if (titleText != null) {
            title = new TextView(context);
            title.setText(titleText);
            title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            panel.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        } else {
            title = null;
        }

        scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(expandedBody);
        body = new TextView(context);
        body.setGravity(expandedBody
                ? Gravity.START | Gravity.TOP
                : Gravity.CENTER);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(scroll, expandedBody
                ? new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1.0F
                )
                : new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(expandedBody
                ? Gravity.END | Gravity.CENTER_VERTICAL
                : Gravity.CENTER);
        cancelButton = new Button(context);
        cancelButton.setText(Objects.requireNonNull(cancelText, "cancelText"));
        cancelButton.setOnClickListener(ignored -> cancelledAction.run());
        actions.addView(cancelButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        confirmButton = new Button(context);
        confirmButton.setText(Objects.requireNonNull(confirmText, "confirmText"));
        confirmButton.setOnClickListener(ignored -> confirmedAction.run());
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        confirmParams.leftMargin = dp(8);
        actions.addView(confirmButton, confirmParams);
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        markflow = DialogueMarkdown.create(context);
        applyTheme(ThemeDefinition.DEFAULT);
        requestFocus();
    }

    void applyTheme(ThemeDefinition theme) {
        var box = theme.box();
        var text = theme.text();
        var spacing = theme.spacing();

        ShapeDrawable overlay = new ShapeDrawable();
        overlay.setColor(OVERLAY_COLOR);
        setBackground(overlay);

        ShapeDrawable panelBackground = new ShapeDrawable();
        panelBackground.setColor(box.background().argb());
        panelBackground.setCornerRadius(dp(box.cornerRadiusDp()));
        panelBackground.setStroke(
                dp(box.borderWidthDp()),
                box.border().argb()
        );
        panel.setBackground(panelBackground);
        int horizontal = dp(Math.max(16, spacing.contentHorizontalDp()));
        int vertical = dp(Math.max(12, spacing.contentVerticalDp()));
        panel.setPadding(horizontal, vertical, horizontal, vertical);

        if (title != null) {
            title.setTextColor(text.primary().argb());
            typography.apply(title, text.speakerSizeSp());
        }
        body.setTextColor(text.primary().argb());
        typography.apply(body, text.dialogueSizeSp());
        Spanned renderedBody = markflow.convert(markdownBody);
        markflow.setRenderedMarkdown(body, renderedBody);
        int bodyPadding = dp(12);
        body.setPadding(0, bodyPadding, 0, bodyPadding);
        cancelButton.setTextColor(text.primary().argb());
        typography.apply(cancelButton, text.auxiliarySizeSp());
        confirmButton.setTextColor(text.primary().argb());
        typography.apply(confirmButton, text.auxiliarySizeSp());
        DialogueBoxView.applyControlButtonTheme(cancelButton, theme);
        DialogueBoxView.applyControlButtonTheme(confirmButton, theme);
        DialogueScrollbarStyle.apply(scroll, theme);
    }

    void setTypography(
            DialogueTypography typography,
            ThemeDefinition theme
    ) {
        this.typography = Objects.requireNonNull(typography, "typography");
        applyTheme(theme);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) panel.getLayoutParams();
        params.width = Math.max(
                1,
                Math.min(
                        Math.round(width * (expandedBody
                                ? PANEL_WIDTH_FRACTION
                                : COMPACT_PANEL_WIDTH_FRACTION)),
                        dp(expandedBody
                                ? PANEL_MAX_WIDTH_DP
                                : PANEL_MAX_WIDTH_DP / 3)
                )
        );
        params.height = expandedBody
                ? Math.max(
                        1,
                        Math.min(
                                Math.round(height * PANEL_HEIGHT_FRACTION),
                                dp(PANEL_MAX_HEIGHT_DP)
                        )
                )
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
