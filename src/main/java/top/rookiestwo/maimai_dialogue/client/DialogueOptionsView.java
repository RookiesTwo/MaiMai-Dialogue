package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueOption;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeOption;
import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;

import java.util.List;
import java.util.function.Consumer;

final class DialogueOptionsView extends LinearLayout {
    private final View divider;
    private final LinearLayout section;
    private final TextView loading;
    private final OptionScrollView scroll;
    private final LinearLayout list;
    private final Consumer<DialogueOption> selectionConsumer;
    private ThemeDefinition theme = ThemeDefinition.DEFAULT;
    private DialogueTypography typography = DialogueTypography.resolve(
            ClientConfig.get()
    );
    private Button expandButton;
    private Consumer<Boolean> expandVisibilityChanged = ignored -> {
    };

    DialogueOptionsView(
            Context context,
            Consumer<DialogueOption> selectionConsumer
    ) {
        super(context);
        this.selectionConsumer = selectionConsumer;
        setOrientation(VERTICAL);
        divider = new View(context);
        addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));
        section = new LinearLayout(context);
        section.setOrientation(VERTICAL);
        loading = new TextView(context);
        loading.setText(I18n.get("gui.maimai_dialogue.loading_options"));
        int loadingPadding = loading.dp(6);
        loading.setPadding(
                loadingPadding,
                loadingPadding,
                loadingPadding,
                loadingPadding
        );
        section.addView(loading, matchWidthWrapHeight());
        scroll = new OptionScrollView(context);
        list = new LinearLayout(context);
        list.setOrientation(VERTICAL);
        scroll.addView(list, new OptionScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        section.addView(scroll, matchWidthWrapHeight());
        addView(section, matchWidthWrapHeight());
        setVisibility(View.GONE);
        applyTheme(ThemeDefinition.DEFAULT);
    }

    void bindExpandButton(
            Button button,
            Consumer<Boolean> visibilityChanged
    ) {
        expandButton = button;
        expandVisibilityChanged = visibilityChanged;
        button.setOnClickListener(view -> toggleExpanded());
    }

    // 根据 session state 重建当前可见选项并刷新展开按钮。
    void render(
            List<DialogueOption> options,
            boolean loadingOptions,
            boolean requestingTarget
    ) {
        boolean visible = loadingOptions || !options.isEmpty();
        setVisibility(visible ? View.VISIBLE : View.GONE);
        divider.setVisibility(visible ? View.VISIBLE : View.GONE);
        section.setVisibility(visible ? View.VISIBLE : View.GONE);
        loading.setVisibility(loadingOptions ? View.VISIBLE : View.GONE);
        list.removeAllViews();
        for (DialogueOption option : options) {
            list.addView(
                    createOptionButton(option, requestingTarget),
                    optionLayoutParams(option)
            );
        }
        scroll.post(() -> updateExpandVisibility(false));
    }

    // 切换 Dialogue 时复位滚动和展开状态。
    void reset() {
        scroll.setExpanded(false);
        scroll.scrollTo(0, 0);
        updateExpandVisibility(false);
    }

    void setTypography(DialogueTypography typography) {
        this.typography = typography;
        applyTheme(theme);
    }

    boolean isExpanded() {
        return scroll.isExpanded();
    }

    // 应用选项间距、文字和滚动条 Theme。
    void applyTheme(ThemeDefinition theme) {
        this.theme = theme;
        ShapeDrawable dividerBackground = new ShapeDrawable();
        dividerBackground.setColor(theme.box().divider().argb());
        divider.setBackground(dividerBackground);
        int padding = section.dp(theme.spacing().optionsPaddingDp());
        section.setPadding(padding, padding, padding, padding);
        loading.setTextColor(theme.text().primary().argb());
        typography.apply(loading, theme.text().auxiliarySizeSp());
        scroll.setOptionLimits(
                theme.spacing().optionsCollapsedLimit(),
                theme.spacing().optionsExpandedLimit()
        );
        DialogueScrollbarStyle.apply(scroll, theme);
    }

    private void toggleExpanded() {
        boolean collapsing = scroll.isExpanded();
        scroll.setExpanded(!collapsing);
        updateExpandVisibility(collapsing);
    }

    private void updateExpandVisibility(boolean animateCollapse) {
        Button button = expandButton;
        if (button == null) {
            return;
        }
        boolean show = scroll.isExpandable();
        if (!show && scroll.isExpanded()) {
            scroll.setExpanded(false);
        }
        button.setText(I18n.get(scroll.isExpanded()
                ? "gui.maimai_dialogue.collapse"
                : "gui.maimai_dialogue.expand"));
        button.setVisibility(show ? View.VISIBLE : View.GONE);
        expandVisibilityChanged.accept(
                animateCollapse && !scroll.isExpanded()
        );
    }

    private Button createOptionButton(
            DialogueOption option,
            boolean requestingTarget
    ) {
        Button button = new Button(getContext());
        button.setText(optionLabel(option));
        ThemeOption optionTheme = theme.option();
        button.setTextColor(theme.text().primary().argb());
        typography.apply(button, theme.text().optionSizeSp());
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        int horizontal = button.dp(optionTheme.horizontalPaddingDp());
        int vertical = button.dp(optionTheme.verticalPaddingDp());
        button.setPadding(horizontal, vertical, horizontal, vertical);
        button.setBackground(createOptionBackground(button, optionTheme));
        button.setEnabled(!requestingTarget);
        button.setOnClickListener(view -> selectionConsumer.accept(option));
        return button;
    }

    private LinearLayout.LayoutParams optionLayoutParams(DialogueOption option) {
        LinearLayout.LayoutParams params = matchWidthWrapHeight();
        int margin = dp(theme.option().spacingDp());
        params.setMargins(0, margin, 0, margin);
        return params;
    }

    private CharSequence optionLabel(DialogueOption option) {
        String prefix = switch (option.icon()) {
            case NONE -> "";
            case QUESTION -> "?  ";
            case EXCLAMATION -> "!  ";
            case DIALOGUE -> "›  ";
        };
        if (prefix.isEmpty()) {
            return option.text();
        }
        SpannableString label = new SpannableString(prefix + option.text());
        label.setSpan(
                new ForegroundColorSpan(theme.controls().icon().argb()),
                0,
                prefix.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return label;
    }

    private static StateListDrawable createOptionBackground(
            View view,
            ThemeOption theme
    ) {
        StateListDrawable background = new StateListDrawable();
        background.addState(
                new int[]{R.attr.state_pressed},
                createShape(view, theme.pressedBackground().argb(),
                        theme.hoverBorder().argb(), theme.cornerRadiusDp(),
                        theme.borderWidthDp())
        );
        background.addState(
                new int[]{R.attr.state_hovered},
                createShape(view, theme.hoverBackground().argb(),
                        theme.hoverBorder().argb(), theme.cornerRadiusDp(),
                        theme.borderWidthDp())
        );
        background.addState(
                StateSet.WILD_CARD,
                createShape(view, theme.background().argb(),
                        theme.border().argb(), theme.cornerRadiusDp(),
                        theme.borderWidthDp())
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

    private static LinearLayout.LayoutParams matchWidthWrapHeight() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }
}
