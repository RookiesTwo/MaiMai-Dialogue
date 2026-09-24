package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.R;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;
import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;
import top.rookiestwo.maimai_dialogue.client.ui.style.DialogueTypography;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;

import java.util.function.Consumer;

final class EditorWidgets {
    // 单行资源列表与属性下拉菜单共用的紧凑密度。
    static final int COMPACT_ROW_DP = 22;
    static final int COMPACT_HORIZONTAL_PADDING_DP = 4;
    static final int COMPACT_CONTROL_DP = 24;
    static final int CONTROL_CORNER_DP = 4;

    // 白色内容区、浅灰框架和亮蓝交互反馈；文字使用深灰以保持可读性。
    static final int BACKGROUND = 0xFFF2F4F7;
    static final int PANEL = 0xFFFFFFFF;
    static final int HEADER = 0xFFE9EDF2;
    static final int PREVIEW = 0xFFFFFFFF;
    static final int BORDER = 0xFFCDD3DA;
    static final int ACCENT = 0xFF0088FF;
    static final int TEXT = 0xFF2F3742;
    static final int MUTED = 0xFF6B7280;
    static final int ERROR = 0xFFC62828;
    static final int SPLITTER_HOVER = 0xFF70B8FF;
    private static final int BUTTON_HOVER = 0xFFE5F2FF;
    private static final int TOOLBAR_HOVER = 0xFFDCE2E9;
    static final int SELECTION = 0xFFCDE6FF;
    private static final int BUTTON_PRESSED = SELECTION;
    private static final int TREE_ROW_HOVER = 0x1A0088FF;
    private static final int TREE_ROW_PRESSED = 0x330088FF;
    private static final int DISABLED_TEXT = 0xFF9BA3AF;
    private static final int SCROLLBAR_THUMB = 0xFFA6AFBB;
    private static final int METRICS_TAG = 0x6D650001;
    private static final int TOOLTIP_STYLE_TAG = 0x6D650002;
    static final int DEFERRED_INPUT_TAG = 0x6D650003;
    private static final int PROPERTY_BUTTON_SCOPE_TAG = 0x6D650004;
    private static final int BUTTON_ROLE_TAG = 0x6D650005;
    private static final int EDITOR_TEXT_TAG = 0x6D650006;
    private enum ButtonRole { ACTION, FIELD, SECTION, PANEL_CONTROL, TOOLBAR }
    // UI-thread cache: resolving a configured family creates a new fallback chain.
    private static String fontFamily;
    private static Typeface fallbackTypeface;
    private static Typeface editorTypeface;

    private EditorWidgets() {
    }

    static String tr(String key) {
        return I18n.get("gui.maimai_dialogue_editor." + key);
    }

    private static Typeface configuredTypeface() {
        String family = ClientConfig.get().fontFamily();
        Typeface fallback = ModernUI.getSelectedTypeface();
        if (editorTypeface == null || !family.equals(fontFamily) || fallback != fallbackTypeface) {
            editorTypeface = DialogueTypography.resolveTypeface(family);
            fontFamily = family;
            fallbackTypeface = fallback;
        }
        return editorTypeface;
    }

    private static void bindTypeface(TextView text) {
        text.setTag(EDITOR_TEXT_TAG, Boolean.TRUE);
        text.setTypeface(configuredTypeface());
    }

    static TextView label(Context context, String key, int size, int color) {
        TextView text = new TextView(context);
        bindTypeface(text);
        text.setText(tr(key));
        text.setTextColor(color);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setGravity(Gravity.CENTER_VERTICAL);
        bindMetrics(text, () -> text.setTextSize(size));
        return text;
    }

    static TextView placeholder(Context context, String key) {
        TextView text = label(context, key, 14, MUTED);
        text.setSingleLine(false);
        text.setMaxLines(3);
        text.setGravity(Gravity.CENTER);
        bindMetrics(text, () -> text.setPadding(text.dp(12), text.dp(8), text.dp(12), text.dp(8)));
        return text;
    }

    static Button button(Context context, String key, Runnable action) {
        return button(context, key, action, ButtonRole.ACTION);
    }

    static Button fieldButton(Context context, String key, Runnable action) {
        return button(context, key, action, ButtonRole.FIELD);
    }

    // 普通菜单与资源搜索共用同一套紧凑选项行。
    static Button choiceRow(Context context, ChoicePresenter.Item item, String selected, Consumer<String> chosen) {
        Button row = button(context, "", () -> { if (item.enabled()) chosen.accept(item.value()); });
        row.setText(item.label());
        row.setTooltipText(item.label());
        row.setSelected(item.value().equals(selected));
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        enabled(row, item.enabled());
        bindMetrics(row, () -> {
            int padding = row.dp(COMPACT_HORIZONTAL_PADDING_DP);
            row.setPadding(padding, 0, padding, 0);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, row.dp(COMPACT_ROW_DP)));
        });
        return row;
    }

    static Button sectionButton(Context context, String key, Runnable action) {
        return button(context, key, action, ButtonRole.SECTION);
    }

    private static Button button(Context context, String key, Runnable action, ButtonRole role) {
        return button(context, key, action, role, null);
    }

    private static Button button(Context context, String key, Runnable action, ButtonRole role, EditorButtonIcon icon) {
        Button button = new Button(context) {
            private final Paint iconPaint = icon == null ? null : new Paint();

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (icon != null) icon.draw(canvas, this, iconPaint);
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                // The parent chain is available here, including for asynchronously rebuilt fields.
                if (inPropertyPanel(this) || getTag(BUTTON_ROLE_TAG) == ButtonRole.PANEL_CONTROL
                        || getTag(BUTTON_ROLE_TAG) == ButtonRole.TOOLBAR) refreshButtonStyle(this);
            }
        };
        bindTypeface(button);
        button.setTag(BUTTON_ROLE_TAG, role);
        button.setText(tr(key));
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setEnabled(action != null);
        button.setTextColor(action == null ? DISABLED_TEXT : TEXT);
        if (action != null) {
            button.setOnClickListener(view -> action.run());
        } else {
            button.setTooltipText(tr("unavailable"));
        }
        bindMetrics(button, () -> {
            button.setTextSize(13);
            button.setPadding(button.dp(8), 0, button.dp(8), 0);
            refreshButtonStyle(button);
        });
        return button;
    }

    static Button icon(Context context, EditorButtonIcon icon, String tooltip, Runnable action) {
        Button button = button(context, tooltip, action, ButtonRole.ACTION, icon);
        button.setText("");
        // Geometry uses the visible button bounds, not TextView's wide single-line layout.
        button.setHorizontallyScrolling(false);
        button.setContentDescription(tr(tooltip));
        button.setTooltipText(tr(tooltip));
        bindMetrics(button, () -> button.setPadding(0, 0, 0, 0));
        return button;
    }

    static Button icon(Context context, String glyph, String tooltip, Runnable action) {
        Button button = button(context, tooltip, action);
        button.setText(glyph);
        button.setTooltipText(tr(tooltip));
        bindMetrics(button, () -> {
            button.setTextSize(16);
            button.setPadding(0, 0, 0, 0);
        });
        return button;
    }

    static Button panelIcon(Context context, EditorButtonIcon icon, String tooltip, Runnable action) {
        Button button = icon(context, icon, tooltip, action);
        button.setTag(BUTTON_ROLE_TAG, ButtonRole.PANEL_CONTROL);
        refreshButtonStyle(button);
        return button;
    }

    static void toolbarButton(Button button) {
        button.setTag(BUTTON_ROLE_TAG, ButtonRole.TOOLBAR);
        refreshButtonStyle(button);
    }

    static LinearLayout.LayoutParams squareIconParams(View icon) {
        var params = new LinearLayout.LayoutParams(icon.dp(COMPACT_CONTROL_DP), icon.dp(COMPACT_CONTROL_DP));
        params.topMargin = icon.dp(2);
        params.bottomMargin = icon.dp(2);
        return params;
    }

    static void enabled(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setTextColor(buttonTextColor(button));
    }

    static EditText input(Context context, String value, Consumer<String> changed, Runnable endEdit) {
        EditText input = new EditText(context);
        bindTypeface(input);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setText(value);
        bindMetrics(input, () -> {
            input.setTextSize(14);
            input.setPadding(input.dp(8), input.dp(6), input.dp(8), input.dp(6));
            ShapeDrawable background = shape(PANEL, input.dp(1));
            background.setCornerRadius(input.dp(CONTROL_CORNER_DP));
            input.setBackground(background);
            input.setMinimumHeight(input.dp(34));
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                changed.accept(text.toString());
            }
        });
        input.setOnFocusChangeListener((view, focused) -> {
            if (!focused) endEdit.run();
        });
        return input;
    }

    static void formLabel(LinearLayout container, String key) {
        TextView label = label(container.getContext(), key, 13, MUTED);
        container.addView(label);
        bindMetrics(label, () -> {
            label.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, label.dp(30)));
        });
    }

    static Button formButton(LinearLayout container, String key, Runnable action) {
        Button button = button(container.getContext(), key, action);
        container.addView(button);
        bindMetrics(button, () -> {
            var params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, button.dp(34));
            params.setMargins(0, button.dp(6), 0, 0);
            button.setLayoutParams(params);
        });
        return button;
    }

    static EditText compactInput(Context context, String value, Consumer<String> changed, Runnable endEdit) {
        EditText input = input(context, value, changed, endEdit);
        bindMetrics(input, () -> {
            input.setTextSize(13);
            input.setPadding(input.dp(COMPACT_HORIZONTAL_PADDING_DP), input.dp(2),
                    input.dp(COMPACT_HORIZONTAL_PADDING_DP), input.dp(2));
            input.setMinimumHeight(input.dp(COMPACT_CONTROL_DP));
        });
        return input;
    }

    static void propertyRow(LinearLayout container, String key, View control, boolean multiline) {
        container.addView(new EditorPropertyRow(key, control, multiline),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    static void referenceRow(LinearLayout container, String key, EditText input, Button picker) {
        propertyRow(container, key, new EditorReferenceField(input, picker), false);
    }

    static TextView compactParagraph(Context context, String key) {
        TextView text = paragraph(context, key);
        bindMetrics(text, () -> text.setPadding(0, text.dp(2), 0, text.dp(2)));
        return text;
    }

    static TextView paragraph(Context context, String key) {
        TextView text = label(context, key, 13, MUTED);
        text.setSingleLine(false);
        text.setEllipsize(null);
        bindMetrics(text, () -> text.setPadding(0, text.dp(6), 0, text.dp(6)));
        return text;
    }

    static ScrollView formScroll(Context context, View content) {
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollbarThumbDrawable(shape(SCROLLBAR_THUMB, 0));
        scroll.setVerticalScrollbarTrackDrawable(shape(HEADER, 0));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    // 通用背景保持直角，属性控件按操作、值选择和分组标题分别设置样式。
    static ShapeDrawable shape(int color, int stroke) {
        ShapeDrawable shape = new ShapeDrawable();
        shape.setColor(color);
        shape.setCornerRadius(0);
        if (stroke > 0) {
            shape.setStroke(stroke, BORDER);
        }
        return shape;
    }

    static ShapeDrawable panelControlShape(int color) {
        ShapeDrawable shape = shape(color, 0);
        shape.setStroke(1, HEADER);
        return shape;
    }

    static void propertyButtonScope(View root) {
        root.setTag(PROPERTY_BUTTON_SCOPE_TAG, Boolean.TRUE);
    }

    private static boolean inPropertyPanel(View view) {
        while (view != null) {
            if (Boolean.TRUE.equals(view.getTag(PROPERTY_BUTTON_SCOPE_TAG))) return true;
            view = view.getParent() instanceof View parent ? parent : null;
        }
        return false;
    }

    static boolean propertyAction(View view) {
        return view instanceof Button && view.getTag(BUTTON_ROLE_TAG) == ButtonRole.ACTION;
    }

    private static int buttonTextColor(Button button) {
        if (!button.isEnabled()) return DISABLED_TEXT;
        if (button.getTag(BUTTON_ROLE_TAG) == ButtonRole.PANEL_CONTROL
                || button.getTag(BUTTON_ROLE_TAG) == ButtonRole.TOOLBAR
                || button.getTag(BUTTON_ROLE_TAG) == ButtonRole.SECTION) return TEXT;
        if (inPropertyPanel(button)) return ACCENT;
        return TEXT;
    }

    private static void refreshButtonStyle(Button button) {
        boolean property = inPropertyPanel(button);
        boolean panelControl = button.getTag(BUTTON_ROLE_TAG) == ButtonRole.PANEL_CONTROL;
        boolean toolbar = button.getTag(BUTTON_ROLE_TAG) == ButtonRole.TOOLBAR;
        if (toolbar) {
            StateListDrawable background = new StateListDrawable();
            background.addState(new int[]{-R.attr.state_enabled}, shape(0, 0));
            background.addState(new int[]{R.attr.state_pressed}, shape(BORDER, 0));
            background.addState(new int[]{R.attr.state_selected}, shape(BORDER, 0));
            background.addState(new int[]{R.attr.state_hovered}, shape(TOOLBAR_HOVER, 0));
            background.addState(new int[]{R.attr.state_focused}, shape(TOOLBAR_HOVER, 0));
            background.addState(StateSet.WILD_CARD, shape(0, 0));
            button.setBackground(background);
        } else if (panelControl) {
            StateListDrawable background = new StateListDrawable();
            background.addState(new int[]{-R.attr.state_enabled}, panelControlShape(PANEL));
            background.addState(new int[]{R.attr.state_pressed}, panelControlShape(BUTTON_PRESSED));
            background.addState(new int[]{R.attr.state_hovered}, panelControlShape(BUTTON_HOVER));
            background.addState(new int[]{R.attr.state_focused}, panelControlShape(BUTTON_HOVER));
            background.addState(StateSet.WILD_CARD, panelControlShape(PANEL));
            button.setBackground(background);
        } else if (property && button.getTag(BUTTON_ROLE_TAG) != ButtonRole.SECTION) {
            StateListDrawable background = new StateListDrawable();
            background.addState(new int[]{-R.attr.state_enabled}, propertyButtonShape(button, PANEL, BORDER));
            background.addState(new int[]{R.attr.state_pressed}, propertyButtonShape(button, BUTTON_PRESSED, ACCENT));
            background.addState(new int[]{R.attr.state_selected}, propertyButtonShape(button, BUTTON_PRESSED, ACCENT));
            background.addState(new int[]{R.attr.state_hovered}, propertyButtonShape(button, BUTTON_HOVER, ACCENT));
            background.addState(new int[]{R.attr.state_focused}, propertyButtonShape(button, BUTTON_HOVER, ACCENT));
            background.addState(StateSet.WILD_CARD, propertyButtonShape(button, PANEL, ACCENT));
            button.setBackground(background);
        } else {
            button.setBackground(buttonBackground());
        }
        // ModernUI 3.13 setBackground() does not apply the View state to the new drawable.
        // Synchronize now: otherwise a fresh StateListDrawable stays on its disabled entry until input.
        button.refreshDrawableState();
        if (property || panelControl || toolbar) button.setTextColor(buttonTextColor(button));
    }

    private static ShapeDrawable propertyButtonShape(Button button, int color, int border) {
        ShapeDrawable shape = shape(color, 0);
        shape.setStroke(1, border);
        shape.setCornerRadius(button.dp(CONTROL_CORNER_DP));
        return shape;
    }

    private static StateListDrawable buttonBackground() {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{-R.attr.state_enabled}, shape(HEADER, 0));
        background.addState(new int[]{R.attr.state_pressed}, shape(BUTTON_PRESSED, 0));
        background.addState(new int[]{R.attr.state_selected}, shape(BUTTON_PRESSED, 0));
        background.addState(new int[]{R.attr.state_hovered}, shape(BUTTON_HOVER, 0));
        background.addState(StateSet.WILD_CARD, shape(PANEL, 0));
        return background;
    }

    // Pointer feedback remains active after selection and lets the moving selection show through.
    static StateListDrawable treeRowBackground() {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{-R.attr.state_enabled}, shape(0, 0));
        background.addState(new int[]{R.attr.state_pressed}, shape(TREE_ROW_PRESSED, 0));
        background.addState(new int[]{R.attr.state_hovered}, shape(TREE_ROW_HOVER, 0));
        background.addState(StateSet.WILD_CARD, shape(0, 0));
        return background;
    }

    // 为新创建的编辑器提示框应用浅色直角样式，不修改其他界面或反复触发布局。
    @SuppressWarnings("UnstableApiUsage")
    static void styleTooltips(View owner) {
        styleTooltips(owner, configuredTypeface());
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void styleTooltips(View owner, Typeface typeface) {
        // Only editor-owned text is updated; embedded Dialogue views keep their runtime typography.
        if (owner instanceof TextView text && owner.getTag(EDITOR_TEXT_TAG) == Boolean.TRUE) {
            text.setTypeface(typeface);
        }
        View tooltip = owner.getTooltipView();
        if (tooltip != null && tooltip.getTag(TOOLTIP_STYLE_TAG) == null) {
            tooltip.setTag(TOOLTIP_STYLE_TAG, Boolean.TRUE);
            tooltip.setBackground(shape(PANEL, tooltip.dp(1)));
            if (tooltip instanceof TextView text) {
                text.setTextColor(TEXT);
            }
        }
        if (tooltip instanceof TextView text) text.setTypeface(typeface);
        if (owner instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                styleTooltips(group.getChildAt(index), typeface);
            }
        }
    }

    // 每个 View 自己携带尺寸刷新动作，销毁后不留下全局引用。
    static void bindMetrics(View view, Runnable refresh) {
        Runnable previous = (Runnable) view.getTag(METRICS_TAG);
        view.setTag(METRICS_TAG, previous == null ? refresh : (Runnable) () -> {
            previous.run();
            refresh.run();
        });
        refresh.run();
    }

    static void refreshMetrics(View view) {
        Runnable refresh = (Runnable) view.getTag(METRICS_TAG);
        if (refresh != null) {
            refresh.run();
        }
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                refreshMetrics(group.getChildAt(index));
            }
        }
    }
}
