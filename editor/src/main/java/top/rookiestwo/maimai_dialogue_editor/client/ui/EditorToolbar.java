package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.HorizontalScrollView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;

import java.util.HashMap;
import java.util.Map;

// Keep close reachable while project actions scroll in narrow windows.
final class EditorToolbar extends FrameLayout {
    private static final int CLOSE_SIZE_DP = 20;
    private final Button close;
    private final TextView title;
    private final HorizontalScrollView scroll;
    private int inset;
    private int closeSize;
    private int controlHeight;
    private int closeGap;
    private int titleWidth;
    private final Map<String, Button> businessButtons = new HashMap<>();

    EditorToolbar(Context context, Runnable closeAction, ProjectWorkspace workspace) {
        super(context);
        setBackground(EditorWidgets.shape(EditorWidgets.HEADER, 0));
        close = EditorWidgets.icon(context, EditorButtonIcon.CLOSE, "close", closeAction);
        EditorWidgets.toolbarButton(close);
        LinearLayout items = new LinearLayout(context);
        items.setOrientation(LinearLayout.HORIZONTAL);
        items.setGravity(Gravity.CENTER_VERTICAL);
        items.setBaselineAligned(false);
        title = EditorWidgets.label(context, "title", 13, EditorWidgets.ACCENT);
        title.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        EditorWidgets.bindMetrics(title, () -> title.setPadding(title.dp(12), 0, title.dp(4), 0));
        for (String key : new String[]{"project", "save", "undo", "redo", "export"}) {
            if (key.equals("save") || key.equals("export")) addSeparator(items);
            Runnable action = switch (key) {
                case "project" -> workspace::showMenu;
                case "save" -> workspace::save;
                case "undo" -> workspace::undo;
                case "redo" -> workspace::redo;
                case "export" -> workspace::showExportMenu;
                default -> null;
            };
            Button button = EditorWidgets.button(context, key, action);
            EditorWidgets.toolbarButton(button);
            if (key.equals("project") || key.equals("export")) button.setText(EditorWidgets.tr(key) + " ▾");
            String hint = EditorWidgets.tr(key.equals("redo") ? "redo_hint" : key);
            String shortcut = switch (key) {
                case "save" -> "S";
                case "undo" -> "Z";
                case "redo" -> "Y";
                default -> "";
            };
            String modifier = KeyEvent.META_SHORTCUT_ON == KeyEvent.META_SUPER_ON ? "⌘" : "Ctrl+";
            button.setTooltipText(shortcut.isEmpty() ? hint : hint + " (" + modifier + shortcut + ")");
            businessButtons.put(key, button);
            items.addView(button);
            EditorWidgets.bindMetrics(button, () -> {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
                params.rightMargin = button.dp(2);
                params.gravity = Gravity.CENTER_VERTICAL;
                button.setLayoutParams(params);
            });
        }
        scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(items, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(scroll);
        addView(close);
        addView(title);
        refresh(workspace);
    }

    private void addSeparator(LinearLayout items) {
        View separator = new View(getContext());
        separator.setBackground(EditorWidgets.shape(EditorWidgets.BORDER, 0));
        items.addView(separator);
        EditorWidgets.bindMetrics(separator, () -> {
            var params = new LinearLayout.LayoutParams(1, dp(12));
            params.setMargins(dp(6), 0, dp(6), 0);
            params.gravity = Gravity.CENTER_VERTICAL;
            separator.setLayoutParams(params);
        });
    }

    void refresh(ProjectWorkspace workspace) {
        EditorWidgets.enabled(businessButtons.get("project"), !workspace.busy());
        EditorWidgets.enabled(businessButtons.get("save"), !workspace.busy() && workspace.dirty());
        EditorWidgets.enabled(businessButtons.get("undo"), workspace.canUndo());
        EditorWidgets.enabled(businessButtons.get("redo"), workspace.canRedo());
        EditorWidgets.enabled(businessButtons.get("export"), !workspace.busy() && workspace.draft() != null);
        EditorWidgets.enabled(close, !workspace.busy());
        businessButtons.get("project").setSelected(workspace.page() == ProjectWorkspace.Page.MENU);
        businessButtons.get("export").setSelected(workspace.page() == ProjectWorkspace.Page.EXPORT);
    }

    View projectMenuAnchor() {
        return businessButtons.get("project");
    }

    View exportMenuAnchor() { return businessButtons.get("export"); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        controlHeight = Math.min(dp(EditorWidgets.COMPACT_CONTROL_DP), height);
        inset = Math.min(dp(4), Math.max(0, (width - dp(CLOSE_SIZE_DP)) / 2));
        int available = width - inset * 2;
        closeSize = Math.min(dp(CLOSE_SIZE_DP), Math.min(controlHeight, available));
        closeGap = Math.min(dp(4), available - closeSize);
        title.setVisibility(width < dp(600) ? GONE : VISIBLE);
        EditorPanel.measureExact(close, closeSize, closeSize);
        int actionSpace = available - closeSize - closeGap;
        if (title.getVisibility() == GONE) {
            EditorPanel.measureExact(title, 0, 0);
            titleWidth = 0;
        } else {
            title.measure(MeasureSpec.makeMeasureSpec(actionSpace / 2, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(controlHeight, MeasureSpec.EXACTLY));
            titleWidth = title.getMeasuredWidth();
        }
        EditorPanel.measureExact(scroll, actionSpace - titleWidth, controlHeight);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        int closeTop = (height - closeSize) / 2;
        int controlsTop = (height - controlHeight) / 2;
        int closeLeft = width - inset - closeSize;
        close.layout(closeLeft, closeTop, width - inset, closeTop + closeSize);
        int titleRight = closeLeft - closeGap;
        int titleLeft = titleRight - titleWidth;
        scroll.layout(inset, controlsTop, titleLeft, controlsTop + controlHeight);
        if (title.getVisibility() == GONE) title.layout(0, 0, 0, 0);
        else title.layout(titleLeft, controlsTop, titleRight, controlsTop + controlHeight);
    }
}
