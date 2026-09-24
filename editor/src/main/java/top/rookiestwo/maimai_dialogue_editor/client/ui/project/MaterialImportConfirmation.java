package top.rookiestwo.maimai_dialogue_editor.client.ui.project;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorModalLayout;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialWorkspace;
import java.nio.file.Path;
import java.util.List;

/** Confirms native file selections and their logical resource names; never browses the filesystem. */
public final class MaterialImportConfirmation extends EditorModalLayout {
    private final ProjectWorkspace project;
    private final MaterialWorkspace model;
    private final LinearLayout panel, files;
    private final ScrollView scroll;
    private final EditText target;
    private final TextView source, feedback, targetLabel;
    private final Button choose, submit, cancel;
    private List<Path> displayed = List.of();
    private boolean refreshing;

    public MaterialImportConfirmation(Context context, ProjectWorkspace project) {
        super(context, 560, 420); this.project = project; model = project.materials();
        panel = new LinearLayout(context); panel.setOrientation(LinearLayout.VERTICAL);
        EditorWidgets.bindMetrics(panel, () -> panel.setPadding(dp(12), dp(8), dp(12), dp(8)));
        panel.addView(EditorWidgets.label(context, model.replacing() ? "material.replace" : "material.import", 18, EditorWidgets.ACCENT));
        choose = EditorWidgets.button(context, "material.choose_files", model::chooseFiles); panel.addView(choose);
        source = EditorWidgets.paragraph(context, ""); panel.addView(source);
        files = new LinearLayout(context); files.setOrientation(LinearLayout.VERTICAL);
        scroll = EditorWidgets.formScroll(context, files);
        panel.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        targetLabel = EditorWidgets.label(context, "browser.path", 12, EditorWidgets.MUTED); panel.addView(targetLabel);
        EditorWidgets.bindMetrics(targetLabel, () -> targetLabel.setLayoutParams(
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_ROW_DP))));
        target = EditorWidgets.input(context, model.target(), text -> { if (!refreshing) model.setTarget(text); }, () -> {});
        panel.addView(target);
        feedback = EditorWidgets.paragraph(context, ""); panel.addView(feedback);
        LinearLayout actions = new LinearLayout(context);
        submit = EditorWidgets.button(context, "material.import_confirm", model::submit);
        cancel = EditorWidgets.button(context, "project.cancel", project::cancel);
        actions.addView(submit); actions.addView(cancel); panel.addView(actions);
        setPanel(panel);
        refresh();
    }

    public void refresh() {
        refreshing = true;
        try {
            if (!target.getText().toString().equals(model.target())) target.setText(model.target());
        } finally { refreshing = false; }
        List<Path> selected = model.selectedFiles();
        if (!displayed.equals(selected)) {
            displayed = selected; files.removeAllViews();
            for (Path file : selected) {
                TextView row = EditorWidgets.label(getContext(), "", 13, EditorWidgets.TEXT);
                row.setText(file.getFileName().toString()); row.setTooltipText(file.toString());
                row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                EditorWidgets.bindMetrics(row, () -> {
                    row.setPadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0, dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0);
                    row.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_ROW_DP)));
                });
                files.addView(row);
            }
            scroll.scrollTo(0, 0);
        }
        int count = selected.size();
        source.setText(EditorWidgets.tr("material.selected_count") + " " + count);
        targetLabel.setText(EditorWidgets.tr(model.batch() ? "material.target_folder" : "browser.path"));
        String error = project.errorReason() == null ? model.error()
                : EditorWidgets.tr("project.error." + project.errorReason()) + " " + project.errorDetail();
        String message = project.busy() ? EditorWidgets.tr(project.message()) : error;
        feedback.setText(message); feedback.setTextColor(project.busy() ? EditorWidgets.MUTED : EditorWidgets.ERROR);
        feedback.setVisibility(message.isEmpty() ? GONE : VISIBLE);
        target.setEnabled(!project.busy() && !model.selectingFiles() && !model.replacing() && count > 0);
        submit.setText(EditorWidgets.tr("material.import_confirm") + (count > 1 ? " (" + count + ")" : ""));
        EditorWidgets.enabled(submit, !project.busy() && !model.selectingFiles() && count > 0);
        EditorWidgets.enabled(cancel, !project.busy());
        EditorWidgets.enabled(choose, model.canSelectFiles());
    }

}
