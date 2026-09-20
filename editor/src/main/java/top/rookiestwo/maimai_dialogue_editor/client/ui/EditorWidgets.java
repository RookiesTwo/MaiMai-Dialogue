package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.text.TextUtils;
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
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;

import java.util.function.Consumer;

final class EditorWidgets {
    // 单行资源列表与属性下拉菜单共用的紧凑密度。
    static final int COMPACT_ROW_DP = 24;
    static final int COMPACT_HORIZONTAL_PADDING_DP = 6;

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
    static final int SELECTION = 0xFFCDE6FF;
    private static final int BUTTON_PRESSED = SELECTION;
    private static final int TREE_ROW_HOVER = 0x1A0088FF;
    private static final int TREE_ROW_PRESSED = 0x330088FF;
    private static final int DISABLED_TEXT = 0xFF9BA3AF;
    private static final int SCROLLBAR_THUMB = 0xFFA6AFBB;
    private static final int METRICS_TAG = 0x6D650001;
    private static final int TOOLTIP_STYLE_TAG = 0x6D650002;
    static final int DEFERRED_INPUT_TAG = 0x6D650003;

    private EditorWidgets() {
    }

    static String tr(String key) {
        return I18n.get("gui.maimai_dialogue_editor." + key);
    }

    static TextView label(Context context, String key, int size, int color) {
        TextView text = new TextView(context);
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
        Button button = new Button(context);
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
            button.setBackground(buttonBackground());
        });
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

    static void enabled(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setTextColor(enabled ? TEXT : DISABLED_TEXT);
    }

    static EditText input(Context context, String value, Consumer<String> changed, Runnable endEdit) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setText(value);
        bindMetrics(input, () -> {
            input.setTextSize(14);
            input.setPadding(input.dp(8), input.dp(6), input.dp(8), input.dp(6));
            input.setBackground(shape(PANEL, input.dp(1)));
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

    // 编辑器组件统一使用直角，正常、悬停、按下与禁用状态保持一致。
    static ShapeDrawable shape(int color, int stroke) {
        ShapeDrawable shape = new ShapeDrawable();
        shape.setColor(color);
        shape.setCornerRadius(0);
        if (stroke > 0) {
            shape.setStroke(stroke, BORDER);
        }
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
        View tooltip = owner.getTooltipView();
        if (tooltip != null && tooltip.getTag(TOOLTIP_STYLE_TAG) == null) {
            tooltip.setTag(TOOLTIP_STYLE_TAG, Boolean.TRUE);
            tooltip.setBackground(shape(PANEL, tooltip.dp(1)));
            if (tooltip instanceof TextView text) {
                text.setTextColor(TEXT);
            }
        }
        if (owner instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                styleTooltips(group.getChildAt(index));
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
