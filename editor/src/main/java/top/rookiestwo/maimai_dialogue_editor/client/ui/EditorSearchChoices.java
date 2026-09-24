package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Shared resource search with bounded View creation for large registries. */
final class EditorSearchChoices extends LinearLayout {
    private final List<ChoicePresenter.Item> source;
    private final LinearLayout results;
    private final String selected;
    private final Consumer<String> chosen;
    private final Button more;
    private List<ChoicePresenter.Item> filtered;
    private int shown;
    EditorSearchChoices(Context context, List<ChoicePresenter.Item> source, String selected, Consumer<String> chosen) {
        super(context); setOrientation(VERTICAL);
        EditorWidgets.bindMetrics(this, () -> setPadding(dp(1), dp(1), dp(1), dp(1)));
        this.source = List.copyOf(source); this.selected = selected; this.chosen = chosen;
        results = new LinearLayout(context); results.setOrientation(VERTICAL);
        more = EditorWidgets.button(context, "audio.more", this::append);
        var search = EditorWidgets.compactInput(context, "", this::filter, () -> {});
        search.setHint(EditorWidgets.tr("edit.search_resources"));
        search.setHintTextColor(EditorWidgets.MUTED);
        addView(search);
        EditorWidgets.bindMetrics(search, () -> {
            // 搜索区沿用菜单外框，避免输入框的描边紧贴外框形成双线。
            var background = EditorWidgets.shape(EditorWidgets.PANEL, 0);
            background.setCornerRadius(search.dp(EditorWidgets.CONTROL_CORNER_DP));
            search.setBackground(background);
            // 空提示与已输入文本从首次测量起就使用同一个单行高度。
            search.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_CONTROL_DP)));
        });
        var divider = new View(context);
        divider.setBackground(EditorWidgets.shape(EditorWidgets.BORDER, 0));
        addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));
        addView(results); addView(more); filter("");
    }
    private void filter(String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        filtered = source.stream().filter(item -> item.label().toLowerCase(Locale.ROOT).contains(needle)
                || item.value().toLowerCase(Locale.ROOT).contains(needle)).toList();
        results.removeAllViews(); shown = 0; append();
        if (filtered.isEmpty()) results.addView(EditorWidgets.compactParagraph(getContext(), "browser.empty"));
    }
    private void append() {
        int end = Math.min(shown + 100, filtered.size());
        for (; shown < end; shown++) {
            var item = filtered.get(shown);
            results.addView(EditorWidgets.choiceRow(getContext(), item, selected, chosen));
        }
        more.setVisibility(shown < filtered.size() ? VISIBLE : GONE);
    }
}
