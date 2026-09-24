package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
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
        this.source = List.copyOf(source); this.selected = selected; this.chosen = chosen;
        results = new LinearLayout(context); results.setOrientation(VERTICAL);
        more = EditorWidgets.button(context, "audio.more", this::append);
        var search = EditorWidgets.compactInput(context, "", this::filter, () -> {});
        search.setHint(EditorWidgets.tr("edit.search_resources"));
        addView(search); addView(results); addView(more); filter("");
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
