package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceCatalog;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceTree;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceWorkspace;

import java.util.ArrayList;
import java.util.List;

/** Search and tree bindings. Resource data and navigation survive outside this View. */
final class ResourceBrowserView extends LinearLayout {
    private record Controls(ResourceTree.Row row, Button label, Button toggle) {}
    private final ProjectWorkspace workspace;
    private final ResourceWorkspace resources;
    private final EditText search;
    private final Button create;
    private final Button copy;
    private final Button delete;
    private final LinearLayout rows;
    private final ScrollView scroll;
    private final TextView empty;
    private final List<Controls> controls = new ArrayList<>();
    private List<ResourceTree.Row> displayed = List.of();
    private ResourceCatalog displayedCatalog;
    private boolean refreshing;
    private ResourceKey lastClick;
    private long lastClickNanos;

    ResourceBrowserView(Context context, ProjectWorkspace workspace) {
        super(context);
        this.workspace = workspace;
        resources = workspace.resources();
        setOrientation(VERTICAL);
        search = EditorWidgets.input(context, resources.query(), value -> {
            if (!refreshing) resources.setQuery(value);
        }, () -> {});
        search.setHint(EditorWidgets.tr("browser.search"));
        addView(search, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LinearLayout actions = new LinearLayout(context);
        create = EditorWidgets.button(context, "browser.new", resources::beginCreate);
        copy = EditorWidgets.button(context, "browser.copy", resources::beginCopy);
        delete = EditorWidgets.button(context, "browser.delete", resources::beginDelete);
        for (Button button : new Button[]{create, copy, delete}) {
            actions.addView(button);
            EditorWidgets.bindMetrics(button, () -> button.setLayoutParams(new LayoutParams(0, dp(30), 1)));
        }
        addView(actions);
        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        scroll = EditorWidgets.formScroll(context, rows);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        empty = EditorWidgets.paragraph(context, "browser.no_results");
        addView(empty);
        refresh();
    }

    void refresh() {
        refreshing = true;
        try {
            if (!search.getText().toString().equals(resources.query())) search.setText(resources.query());
            search.setEnabled(resources.active() && resources.form() == ResourceWorkspace.Form.NONE);
        } finally {
            refreshing = false;
        }
        EditorWidgets.enabled(create, resources.canCreate());
        EditorWidgets.enabled(copy, resources.canModifySelected());
        EditorWidgets.enabled(delete, resources.canModifySelected());
        List<ResourceTree.Row> visible = workspace.draft() == null ? List.of() : resources.rows();
        if (!displayed.equals(visible) || displayedCatalog != resources.catalog()) {
            displayed = visible;
            displayedCatalog = resources.catalog();
            int offset = scroll.getScrollY();
            rows.removeAllViews();
            controls.clear();
            for (ResourceTree.Row row : visible) addRow(row);
            scroll.post(() -> { if (scroll.isAttachedToWindow()) scroll.scrollTo(0, offset); });
        }
        for (Controls control : controls) {
            ResourceTree.Node node = control.row().node();
            String text = switch (node.type()) {
                case PROJECT -> workspace.draft().name().isBlank()
                        ? EditorWidgets.tr("project.untitled") : workspace.draft().name();
                case CATEGORY -> EditorWidgets.tr("resource." + node.kind().key());
                case FOLDER, RESOURCE -> node.name();
            };
            if (node.type() == ResourceTree.Type.PROJECT && workspace.dirty()) text += " *";
            if (node.resource() != null && node.resource().equals(resources.opened())) text += " •";
            control.label().setText(text);
            control.label().setSelected(node.equals(resources.selection()));
            EditorWidgets.enabled(control.label(), resources.active());
            if (node.kind() != null && !node.kind().available()) control.label().setTextColor(EditorWidgets.MUTED);
            control.label().setTooltipText(node.resource() == null ? text : node.resource().id(workspace.draft().namespace()));
            if (control.toggle() != null) EditorWidgets.enabled(control.toggle(), resources.active() && resources.query().isBlank());
        }
        empty.setText(EditorWidgets.tr(workspace.draft() == null ? "no_project" : "browser.no_results"));
        empty.setVisibility(workspace.draft() == null || (!resources.query().isBlank() && visible.size() == 1) ? VISIBLE : GONE);
    }

    private void addRow(ResourceTree.Row row) {
        LinearLayout line = new LinearLayout(getContext());
        line.setGravity(Gravity.CENTER_VERTICAL);
        rows.addView(line);
        EditorWidgets.bindMetrics(line, () -> {
            line.setPadding(dp(Math.min(row.depth(), 6) * 12), 0, dp(4), 0);
            line.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(30)));
        });
        Button toggle = null;
        if (row.branch()) {
            toggle = EditorWidgets.icon(getContext(), row.expanded() ? "▾" : "▸", "browser.toggle", () -> {
                lastClick = null;
                resources.toggle(row.node());
            });
            line.addView(toggle);
            Button arrow = toggle;
            EditorWidgets.bindMetrics(arrow, () -> arrow.setLayoutParams(new LayoutParams(dp(22), LayoutParams.MATCH_PARENT)));
        }
        Button label = EditorWidgets.button(getContext(), "", () -> click(row.node()));
        label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        line.addView(label, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        controls.add(new Controls(row, label, toggle));
        if (row.node().type() == ResourceTree.Type.CATEGORY && row.expanded()
                && resources.catalog().keys().stream().noneMatch(key -> key.kind() == row.node().kind())) {
            boolean validGroup = workspace.draft().hasResourceGroup(row.node().kind());
            TextView hint = EditorWidgets.label(getContext(), !validGroup ? "browser.error.invalid_group"
                    : row.node().kind().available() ? "browser.empty" : "unavailable", 12, EditorWidgets.MUTED);
            rows.addView(hint);
            EditorWidgets.bindMetrics(hint, () -> {
                hint.setPadding(dp(36), 0, dp(6), 0);
                hint.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(26)));
            });
        }
    }

    private void click(ResourceTree.Node node) {
        long now = System.nanoTime();
        ResourceKey key = node.resource();
        boolean doubleClick = key != null && key.equals(lastClick) && now - lastClickNanos < 350_000_000L;
        lastClick = doubleClick ? null : key;
        lastClickNanos = now;
        resources.select(node);
        if (doubleClick) resources.open(key);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) lastClick = null;
    }
}
