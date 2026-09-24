package top.rookiestwo.maimai_dialogue_editor.client.ui.resource;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceCatalog;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceTree;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceWorkspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.nio.file.Path;

/** Search and tree bindings. Resource data and navigation survive outside this View. */
public final class ResourceBrowserView extends LinearLayout {
    private static final int RESOURCE_LEAF_GAP_DP = 6;
    private record Controls(ResourceTree.Row row, LinearLayout line, Button label, Button toggle) {}
    private final ProjectWorkspace workspace;
    private final ResourceWorkspace resources;
    private final EditText search;
    private final Button create;
    private final Button copy;
    private final Button delete;
    private final Button importMaterial;
    private final ResourceSelectionLayout rows;
    private final ScrollView scroll;
    private final TextView empty;
    private final List<Controls> controls = new ArrayList<>();
    private List<ResourceTree.Row> displayed = List.of();
    private ResourceCatalog displayedCatalog;
    private boolean refreshing;
    private ResourceTree.Node lastSelection;
    private ResourceTree.Node revealAfterLayout;
    private Path displayedDirectory;
    private int restoreOffset = -1;
    private long displayedRevealRevision = -1;
    private long displayedPreviewRevision = -1;
    private long displayedSelectionRevision = -1;
    private boolean selectionUpdatePending;
    private boolean animateSelectionPending;
    private long selectionLayoutRevision;

    public ResourceBrowserView(Context context, ProjectWorkspace workspace) {
        super(context);
        this.workspace = workspace;
        resources = workspace.resources();
        setOrientation(VERTICAL);
        search = EditorWidgets.compactInput(context, resources.query(), value -> {
            if (!refreshing) resources.setQuery(value);
        }, () -> {});
        EditorWidgets.bindMetrics(search, () -> search.setBackground(EditorWidgets.panelControlShape(EditorWidgets.PANEL)));
        search.setHint(EditorWidgets.tr("browser.search"));
        addView(search, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LinearLayout actions = new LinearLayout(context);
        create = EditorWidgets.button(context, "browser.new", () -> {
            if (resources.selection().isStep()) workspace.content().addStep();
            else resources.beginCreate();
        });
        copy = EditorWidgets.button(context, "browser.copy", () -> {
            if (resources.selection().isStep()) workspace.content().copyStep();
            else resources.beginCopy();
        });
        delete = EditorWidgets.button(context, "browser.delete", () -> {
            if (resources.selection().isStep()) workspace.content().deleteStep();
            else resources.beginDelete();
        });
        for (Button button : new Button[]{create, copy, delete}) {
            actions.addView(button);
            EditorWidgets.bindMetrics(button, () -> {
                button.setPadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0,
                        dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0);
                button.setLayoutParams(new LayoutParams(0, dp(EditorWidgets.COMPACT_CONTROL_DP), 1));
            });
        }
        addView(actions);
        importMaterial = EditorWidgets.button(context, "material.import", () -> workspace.materials().begin(null));
        addView(importMaterial);
        EditorWidgets.bindMetrics(importMaterial, () -> importMaterial.setLayoutParams(
                new LayoutParams(LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_ROW_DP))));
        rows = new ResourceSelectionLayout(context);
        scroll = EditorWidgets.formScroll(context, rows);
        scroll.setVerticalScrollBarEnabled(true);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        empty = EditorWidgets.compactParagraph(context, "browser.no_results");
        addView(empty);
        refresh();
    }

    public void refresh() {
        workspace.content().snapshot();
        ResourceTree.Node selection = resources.selection();
        boolean step = selection.isStep();
        EditorWidgets.enabled(importMaterial, workspace.materials().canSelectFiles() && resources.active());
        refreshing = true;
        try {
            if (!search.getText().toString().equals(resources.query())) search.setText(resources.query());
            search.setEnabled(resources.active() && resources.form() == ResourceWorkspace.Form.NONE);
        } finally {
            refreshing = false;
        }
        create.setText(EditorWidgets.tr(step ? "edit.add_step" : "browser.new"));
        create.setTooltipText(EditorWidgets.tr(step ? "edit.add_step" : "browser.create_title"));
        copy.setTooltipText(EditorWidgets.tr(step ? "edit.copy_step" : "browser.copy_title"));
        delete.setTooltipText(EditorWidgets.tr(step ? "edit.delete_step" : "browser.delete_title"));
        EditorWidgets.enabled(create, step ? workspace.content().canAddStep() : resources.canCreate());
        EditorWidgets.enabled(copy, step ? workspace.content().canModifyStep() : resources.canModifySelected());
        EditorWidgets.enabled(delete, step ? workspace.content().canModifyStep() : resources.canModifySelected());
        List<ResourceTree.Row> visible = workspace.draft() == null ? List.of() : resources.rows();
        boolean rebuild = !displayed.equals(visible) || displayedCatalog != resources.catalog();
        boolean sameProject = Objects.equals(displayedDirectory, workspace.directory());
        boolean selectionChanged = !Objects.equals(lastSelection, selection);
        boolean explicitSelection = displayedSelectionRevision != resources.selectionRevision();
        if (selectionChanged || explicitSelection || rebuild) {
            selectionUpdatePending = true;
            animateSelectionPending = lastSelection != null && sameProject
                    && (explicitSelection || (selectionChanged
                    && displayedPreviewRevision != workspace.previewSelectionRevision()));
        }
        displayedPreviewRevision = workspace.previewSelectionRevision();
        displayedSelectionRevision = resources.selectionRevision();
        if (rebuild) {
            displayed = visible;
            displayedCatalog = resources.catalog();
            restoreOffset = Objects.equals(displayedDirectory, workspace.directory()) ? scroll.getScrollY() : 0;
            displayedDirectory = workspace.directory();
            rows.clearRows();
            controls.clear();
            for (ResourceTree.Row row : visible) addRow(row);
        }
        if (!Objects.equals(lastSelection, selection) || displayedRevealRevision != resources.revealRevision()) {
            revealAfterLayout = selection;
        }
        displayedRevealRevision = resources.revealRevision();
        lastSelection = selection;
        for (Controls control : controls) {
            ResourceTree.Node node = control.row().node();
            String text = switch (node.type()) {
                case PROJECT -> workspace.draft().name().isBlank()
                        ? EditorWidgets.tr("project.untitled") : workspace.draft().name();
                case CATEGORY -> EditorWidgets.tr("resource." + node.kind().key());
                case FOLDER, RESOURCE -> node.name();
                case STEP, END -> {
                    String body = resources.catalog().stepText(node.owner(), node.stepIndex());
                    String number = node.type() == ResourceTree.Type.END ? "End" : Integer.toString(node.stepIndex() + 1);
                    yield number + "  " + (body.isBlank() ? EditorWidgets.tr("edit.text.absent") : body);
                }
            };
            if (node.type() == ResourceTree.Type.PROJECT && workspace.dirty()) text += " *";
            if (node.resource() != null && node.resource().equals(resources.opened())) text += " •";
            control.label().setText(text.replace('\n', ' ').replace('\r', ' '));
            control.label().setSelected(node.equals(resources.selection()));
            EditorWidgets.enabled(control.label(), resources.active());
            if (node.kind() != null && !node.kind().available()) control.label().setTextColor(EditorWidgets.MUTED);
            control.label().setTooltipText(node.isStep() ? node.owner().id(workspace.draft().namespace()) + "\n" + text
                    : node.resource() == null ? text : node.resource().id(workspace.draft().namespace()));
            if (control.toggle() != null) EditorWidgets.enabled(control.toggle(), resources.active()
                    && (resources.query().isBlank() || node.type() == ResourceTree.Type.RESOURCE));
        }
        empty.setText(EditorWidgets.tr(workspace.draft() == null ? "no_project" : "browser.no_results"));
        empty.setVisibility(workspace.draft() == null || (!resources.query().isBlank() && visible.size() == 1) ? VISIBLE : GONE);
        if (revealAfterLayout != null || selectionUpdatePending) requestLayout();
    }

    private void addRow(ResourceTree.Row row) {
        LinearLayout line = new LinearLayout(getContext());
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setBaselineAligned(false);
        EditorWidgets.bindMetrics(line, () -> {
            // Leaf resources need a small connector gap, not an unused full-width expand button.
            int leading = row.branch() ? 0 : row.node().type() == ResourceTree.Type.RESOURCE
                    ? RESOURCE_LEAF_GAP_DP : ResourceSelectionLayout.TOGGLE_WIDTH_DP;
            int indent = ResourceSelectionLayout.indent(row.depth())
                    + leading;
            line.setPadding(dp(indent), 0, dp(4), 0);
            var params = new LayoutParams(LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_ROW_DP));
            if (row.node().type() == ResourceTree.Type.CATEGORY) params.topMargin = dp(4);
            line.setLayoutParams(params);
        });
        Button toggle = null;
        if (row.branch()) {
            toggle = EditorWidgets.icon(getContext(), row.expanded() ? "▾" : "▸", "browser.toggle",
                    () -> resources.toggle(row.node()));
            line.addView(toggle);
            Button arrow = toggle;
            EditorWidgets.bindMetrics(arrow, () -> arrow.setLayoutParams(new LayoutParams(
                    dp(ResourceSelectionLayout.TOGGLE_WIDTH_DP), LayoutParams.MATCH_PARENT)));
        }
        Button label = EditorWidgets.button(getContext(), "", () -> resources.select(row.node()));
        label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        if (row.node().type() == ResourceTree.Type.PROJECT || row.node().type() == ResourceTree.Type.CATEGORY)
            label.setTextStyle(TextPaint.BOLD);
        ResourceTreeIcon icon = row.node().isStep() ? null : new ResourceTreeIcon(row.node());
        EditorWidgets.bindMetrics(label, () -> {
            label.setPadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP),
                    0, dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0);
            if (icon != null) {
                icon.setBounds(0, 0, dp(ResourceTreeIcon.SIZE_DP), dp(ResourceTreeIcon.SIZE_DP));
                label.setCompoundDrawables(icon, null, null, null);
                label.setCompoundDrawablePadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP));
            }
            label.setBackground(EditorWidgets.treeRowBackground());
        });
        line.addView(label, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        rows.addTreeRow(line, row.depth(), row.branch() && row.expanded(), toggle == null ? label : toggle, toggle != null);
        controls.add(new Controls(row, line, label, toggle));
        if (row.node().type() == ResourceTree.Type.CATEGORY && row.expanded()
                && resources.catalog().keys().stream().noneMatch(key -> key.kind() == row.node().kind())) {
            boolean validGroup = workspace.draft().hasResourceGroup(row.node().kind());
            TextView hint = EditorWidgets.label(getContext(), !validGroup ? "browser.error.invalid_group"
                    : row.node().kind().available() ? "browser.empty" : "unavailable", 12, EditorWidgets.MUTED);
            rows.addTreeRow(hint, row.depth() + 1, false, hint, false);
            EditorWidgets.bindMetrics(hint, () -> {
                int indent = ResourceSelectionLayout.indent(row.depth() + 1) + RESOURCE_LEAF_GAP_DP
                        + ResourceTreeIcon.SIZE_DP + EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP * 2;
                hint.setPadding(dp(indent), 0, dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0);
                hint.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_ROW_DP)));
            });
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (restoreOffset >= 0) {
            scroll.scrollTo(0, restoreOffset);
            restoreOffset = -1;
        }
        // Keep pending selection/reveal intent until the latest layout callback consumes it.
        ResourceTree.Node target = resources.selection();
        long revision = ++selectionLayoutRevision;
        post(() -> {
            if (!isAttachedToWindow() || revision != selectionLayoutRevision || !target.equals(resources.selection())) return;
            boolean reveal = target.equals(revealAfterLayout);
            revealAfterLayout = null;
            boolean updateSelection = selectionUpdatePending;
            boolean animate = animateSelectionPending;
            selectionUpdatePending = false;
            animateSelectionPending = false;
            for (Controls control : controls) {
                if (!control.row().node().equals(target)) continue;
                int start = control.line().getTop();
                int end = control.line().getBottom();
                int visibleTop = scroll.getScrollY();
                int visibleBottom = visibleTop + scroll.getHeight();
                if (reveal) {
                    if (start < visibleTop) scroll.scrollTo(0, start);
                    else if (end > visibleBottom) scroll.scrollTo(0, Math.max(0, end - scroll.getHeight()));
                }
                Rect bounds = new Rect(control.line().getLeft() + control.label().getLeft(),
                        start + control.label().getTop(), control.line().getLeft() + control.label().getRight(),
                        start + control.label().getBottom());
                if (updateSelection) rows.select(bounds, animate, scroll.getScrollY() - visibleTop);
                else rows.syncBounds(bounds);
                return;
            }
            rows.clearSelection();
        });
    }

    @Override protected void onDetachedFromWindow() {
        selectionLayoutRevision++;
        selectionUpdatePending = false;
        animateSelectionPending = false;
        revealAfterLayout = null;
        super.onDetachedFromWindow();
    }
}
