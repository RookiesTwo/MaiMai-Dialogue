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
import net.minecraft.client.resources.language.I18n;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;

import java.util.Objects;

/**
 * Modal confirmation panel shown when a Dialogue provides a skip summary.
 */
final class DialogueSkipConfirmationView extends FrameLayout {
    private static final float PANEL_WIDTH_FRACTION = 0.62F;
    private static final float PANEL_HEIGHT_FRACTION = 0.65F;
    private static final int PANEL_MAX_WIDTH_DP = 720;
    private static final int PANEL_MAX_HEIGHT_DP = 560;
    private static final int OVERLAY_COLOR = 0x99000000;

    private final LinearLayout panel;
    private final TextView title;
    private final ScrollView scroll;
    private final TextView summary;
    private final Button cancelButton;
    private final Button confirmButton;
    private final Markflow markflow;
    private final String markdownSummary;
    private DialogueTypography typography = DialogueTypography.resolve(
            ClientConfig.get()
    );

    DialogueSkipConfirmationView(
            Context context,
            String markdownSummary,
            Runnable cancelledAction,
            Runnable confirmedAction
    ) {
        super(context);
        this.markdownSummary = Objects.requireNonNull(
                markdownSummary,
                "markdownSummary"
        );
        Objects.requireNonNull(cancelledAction, "cancelledAction");
        Objects.requireNonNull(confirmedAction, "confirmedAction");
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOnClickListener(ignored -> {
        });

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);

        title = new TextView(context);
        title.setText(I18n.get("gui.maimai_dialogue.skip_confirm.title"));
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        summary = new TextView(context);
        summary.setGravity(Gravity.START | Gravity.TOP);
        scroll.addView(summary, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0F
        ));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        cancelButton = new Button(context);
        cancelButton.setText(I18n.get(
                "gui.maimai_dialogue.skip_confirm.cancel"
        ));
        cancelButton.setOnClickListener(ignored -> cancelledAction.run());
        actions.addView(cancelButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        confirmButton = new Button(context);
        confirmButton.setText(I18n.get(
                "gui.maimai_dialogue.skip_confirm.confirm"
        ));
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

        title.setTextColor(text.primary().argb());
        typography.apply(title, text.speakerSizeSp());
        summary.setTextColor(text.primary().argb());
        typography.apply(summary, text.dialogueSizeSp());
        Spanned renderedSummary = markflow.convert(markdownSummary);
        markflow.setRenderedMarkdown(summary, renderedSummary);
        int summaryPadding = dp(12);
        summary.setPadding(0, summaryPadding, 0, summaryPadding);
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
        this.typography = typography;
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
                        Math.round(width * PANEL_WIDTH_FRACTION),
                        dp(PANEL_MAX_WIDTH_DP)
                )
        );
        params.height = Math.max(
                1,
                Math.min(
                        Math.round(height * PANEL_HEIGHT_FRACTION),
                        dp(PANEL_MAX_HEIGHT_DP)
                )
        );
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

}
