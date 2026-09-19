package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.material.*;
import java.nio.file.Path;
import java.util.*;

/** In-game source browser. Project destination directories are never editable. */
final class MaterialImportDialog extends FrameLayout {
    private static final long DOUBLE_CLICK_NANOS = 400_000_000L;
    private record FileRow(MaterialFiles.Entry file, Button button, String name) {}
    private final ProjectWorkspace project;
    private final MaterialWorkspace model;
    private final LinearLayout panel, rows;
    private final ScrollView scroll;
    private final EditText target;
    private final TextView location, source, feedback, targetLabel;
    private final Button roots, selectAll, clear, submit, cancel;
    private Button parent;
    private View pendingNavigation;
    private long navigationClickTime;
    private List<MaterialFiles.Entry> displayed = List.of();
    private Path displayedDirectory;
    private final List<FileRow> entries = new ArrayList<>();
    private boolean refreshing;

    MaterialImportDialog(Context context, ProjectWorkspace project) {
        super(context); this.project = project; model = project.materials();
        setBackground(EditorWidgets.shape(0x80788088, 0)); setClickable(true);
        setFocusable(true); setFocusableInTouchMode(true);
        panel = new LinearLayout(context); panel.setOrientation(LinearLayout.VERTICAL);
        EditorWidgets.bindMetrics(panel, () -> {
            panel.setPadding(dp(12), dp(8), dp(12), dp(8));
            panel.setBackground(EditorWidgets.shape(EditorWidgets.PANEL, dp(1)));
        });
        panel.addView(EditorWidgets.label(context, model.replacing() ? "material.replace" : "material.import", 18, EditorWidgets.ACCENT));
        LinearLayout navigation = new LinearLayout(context);
        roots = EditorWidgets.button(context, "material.drives", () -> {
            pendingNavigation = null;
            model.browse(null);
        });
        selectAll = EditorWidgets.button(context, "material.select_all", model::selectAll);
        clear = EditorWidgets.button(context, "material.clear_selection", model::clearSelection);
        navigation.addView(roots); navigation.addView(selectAll); navigation.addView(clear); panel.addView(navigation);
        selectAll.setVisibility(model.replacing() ? GONE : VISIBLE);
        clear.setVisibility(model.replacing() ? GONE : VISIBLE);
        location = EditorWidgets.paragraph(context, ""); panel.addView(location);
        rows = new LinearLayout(context); rows.setOrientation(LinearLayout.VERTICAL);
        scroll = EditorWidgets.formScroll(context, rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        source = EditorWidgets.paragraph(context, "material.choose_file"); panel.addView(source);
        targetLabel = EditorWidgets.label(context, "browser.path", 12, EditorWidgets.MUTED);
        panel.addView(targetLabel);
        EditorWidgets.bindMetrics(targetLabel, () -> targetLabel.setLayoutParams(
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_ROW_DP))));
        target = EditorWidgets.input(context, model.target(), text -> { if (!refreshing) model.setTarget(text); }, () -> {});
        panel.addView(target);
        feedback = EditorWidgets.paragraph(context, ""); panel.addView(feedback);
        LinearLayout actions = new LinearLayout(context);
        submit = EditorWidgets.button(context, "material.import_confirm", model::submit);
        cancel = EditorWidgets.button(context, "project.cancel", project::cancel);
        actions.addView(submit); actions.addView(cancel); panel.addView(actions);
        addView(panel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
        refresh();
    }

    private Button row(String text, String tooltip, boolean directory, Runnable action) {
        Button button = EditorWidgets.button(getContext(), "", () -> {});
        button.setOnClickListener(view -> {
            long now = System.nanoTime();
            if (directory && (pendingNavigation != view || now - navigationClickTime > DOUBLE_CLICK_NANOS)) {
                pendingNavigation = view;
                navigationClickTime = now;
                return;
            }
            pendingNavigation = null;
            action.run();
        });
        button.setText(text); button.setTooltipText(tooltip);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        EditorWidgets.bindMetrics(button, () -> {
            button.setPadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0, dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0);
            button.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_ROW_DP)));
        });
        rows.addView(button);
        return button;
    }

    void refresh() {
        refreshing = true;
        try {
            if (!target.getText().toString().equals(model.target())) target.setText(model.target());
        } finally { refreshing = false; }
        if (displayed != model.files() || !Objects.equals(displayedDirectory, model.browsing())) {
            displayed = model.files(); displayedDirectory = model.browsing();
            pendingNavigation = null;
            rows.removeAllViews(); entries.clear(); parent = null;
            if (displayedDirectory != null) {
                Path parentDirectory = displayedDirectory.getParent();
                parent = row("▸ …", EditorWidgets.tr("material.parent"), true, () -> model.browse(parentDirectory));
            }
            for (var file : displayed) {
                String name = file.path().getFileName() == null ? file.path().toString() : file.path().getFileName().toString();
                Button button = row(name, file.path().toString(), file.directory(), () -> model.choose(file));
                entries.add(new FileRow(file, button, name));
            }
            scroll.scrollTo(0, 0);
        }
        for (FileRow row : entries) {
            boolean checked = model.isSelected(row.file().path());
            String marker = row.file().directory() ? "▸ " : model.replacing() ? "" : checked ? "☑ " : "☐ ";
            row.button().setText(marker + row.name());
            row.button().setSelected(checked);
            EditorWidgets.enabled(row.button(), !project.busy());
        }
        if (parent != null) EditorWidgets.enabled(parent, !project.busy());
        location.setText(model.browsing() == null ? EditorWidgets.tr("material.drives") : model.browsing().toString());
        location.setTooltipText(location.getText());
        List<Path> selected = model.selectedFiles();
        int count = selected.size();
        source.setText(count == 0 ? EditorWidgets.tr("material.choose_file") : count == 1
                ? selected.getFirst().getFileName().toString() : EditorWidgets.tr("material.selected_count") + " " + count);
        source.setTooltipText(count == 1 ? selected.getFirst().toString() : source.getText());
        targetLabel.setText(EditorWidgets.tr(model.batch() ? "material.target_folder" : "browser.path"));
        String error = project.errorReason() == null ? model.error()
                : EditorWidgets.tr("project.error." + project.errorReason()) + " " + project.errorDetail();
        String message = project.busy() ? EditorWidgets.tr(project.message()) : error;
        feedback.setText(message);
        feedback.setVisibility(message.isEmpty() ? GONE : VISIBLE);
        target.setEnabled(!project.busy() && !model.replacing() && count > 0);
        submit.setText(EditorWidgets.tr("material.import_confirm") + (count > 1 ? " (" + count + ")" : ""));
        EditorWidgets.enabled(submit, !project.busy() && count > 0);
        EditorWidgets.enabled(cancel, !project.busy());
        EditorWidgets.enabled(roots, !project.busy());
        EditorWidgets.enabled(selectAll, !project.busy() && displayed.stream().anyMatch(file -> !file.directory()));
        EditorWidgets.enabled(clear, !project.busy() && count > 0);
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) pendingNavigation = null;
    }

    @Override protected void onDetachedFromWindow() {
        pendingNavigation = null;
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        var params = (LayoutParams)panel.getLayoutParams();
        params.width = Math.max(0, Math.min(dp(660), width - Math.min(dp(24), width / 8)));
        params.height = Math.max(0, Math.min(dp(580), height - Math.min(dp(24), height / 8)));
        super.onMeasure(widthSpec, heightSpec);
    }
}
