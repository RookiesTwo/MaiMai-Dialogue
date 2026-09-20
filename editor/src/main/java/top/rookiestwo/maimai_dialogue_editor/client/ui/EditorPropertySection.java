package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;

/** Collapsible inspector group; preferences belong to the Fragment's layout state. */
final class EditorPropertySection extends LinearLayout {
    private final String key;
    private final EditorLayoutState state;
    private final Button header;
    private final LinearLayout body;

    EditorPropertySection(Context context, String key, EditorLayoutState state) {
        super(context);
        this.key = key;
        this.state = state;
        setOrientation(VERTICAL);
        body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        header = EditorWidgets.button(context, key, () -> {
            boolean collapse = !state.collapsedPropertySections.contains(key);
            if (collapse) state.collapsedPropertySections.add(key);
            else state.collapsedPropertySections.remove(key);
            // Commit deferred fields before hiding their controls, including keyboard activation.
            if (collapse) body.clearFocus();
            refresh();
        });
        header.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.setTextColor(EditorWidgets.ACCENT);
        header.setTooltipText(EditorWidgets.tr(key));
        addView(header);
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        EditorWidgets.bindMetrics(header, () -> {
            header.setPadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0,
                    dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0);
            header.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_CONTROL_DP)));
        });
        EditorWidgets.bindMetrics(body, () -> body.setPadding(dp(4), 0, 0, dp(2)));
        refresh();
    }

    LinearLayout body() { return body; }

    void refresh() {
        boolean collapsed = state.collapsedPropertySections.contains(key);
        String text = (collapsed ? "▸  " : "▾  ") + EditorWidgets.tr(key);
        if (!header.getText().toString().equals(text)) header.setText(text);
        body.setVisibility(collapsed ? GONE : VISIBLE);
    }
}
