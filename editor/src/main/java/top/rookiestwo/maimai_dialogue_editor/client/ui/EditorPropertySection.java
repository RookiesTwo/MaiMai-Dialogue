package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;

/** Collapsible inspector group; preferences belong to the Fragment's layout state. */
final class EditorPropertySection extends LinearLayout {
    private final String key;
    private final EditorLayoutState state;
    private final Button header;
    private final LinearLayout body;

    EditorPropertySection(Context context, String key, EditorLayoutState state) {
        this(context, key, state, null);
    }

    EditorPropertySection(Context context, String key, EditorLayoutState state, View accessory) {
        super(context);
        this.key = key;
        this.state = state;
        setOrientation(VERTICAL);
        var divider = new View(context);
        divider.setBackground(EditorWidgets.shape(EditorWidgets.BORDER, 0));
        addView(divider);
        EditorWidgets.bindMetrics(divider, () -> {
            var params = new LayoutParams(LayoutParams.MATCH_PARENT, 1);
            params.topMargin = dp(4);
            params.bottomMargin = dp(4);
            divider.setLayoutParams(params);
        });
        body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        header = EditorWidgets.sectionButton(context, key, () -> {
            boolean collapse = !state.collapsedPropertySections.contains(key);
            if (collapse) state.collapsedPropertySections.add(key);
            else state.collapsedPropertySections.remove(key);
            state.changed.run();
            // Commit deferred fields before hiding their controls, including keyboard activation.
            if (collapse) body.clearFocus();
            refresh();
        });
        header.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.setTextColor(EditorWidgets.TEXT);
        header.setTextStyle(TextPaint.BOLD);
        header.setTooltipText(EditorWidgets.tr(key));
        if (accessory == null) addView(header);
        else {
            var row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            row.addView(header);
            row.addView(accessory);
        }
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        EditorWidgets.bindMetrics(header, () -> {
            header.setPadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0,
                    dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0);
            header.setLayoutParams(accessory == null
                    ? new LayoutParams(LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_CONTROL_DP))
                    : new LayoutParams(0, dp(EditorWidgets.COMPACT_CONTROL_DP), 1));
        });
        EditorWidgets.bindMetrics(body, () -> body.setPadding(dp(4), 0, 0, dp(2)));
        refresh();
    }

    LinearLayout body() { return body; }

    static void expandAncestors(View field) {
        for (View view = field; view != null; view = view.getParent() instanceof View parent ? parent : null) {
            if (view instanceof EditorPropertySection section) {
                section.state.collapsedPropertySections.remove(section.key);
                section.state.changed.run();
                section.refresh();
            }
        }
    }

    void refresh() {
        boolean collapsed = state.collapsedPropertySections.contains(key);
        String text = (collapsed ? "▸  " : "▾  ") + EditorWidgets.tr(key);
        if (!header.getText().toString().equals(text)) header.setText(text);
        body.setVisibility(collapsed ? GONE : VISIBLE);
    }
}
