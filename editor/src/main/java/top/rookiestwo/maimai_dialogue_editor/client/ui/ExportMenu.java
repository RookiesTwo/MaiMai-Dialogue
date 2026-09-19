package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import top.rookiestwo.maimai_dialogue_editor.export.*;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;

/** Anchored export menu with scrollable validation results. */
final class ExportMenu extends LinearLayout {
    private final ProjectWorkspace project;
    private final ExportWorkspace exports;
    private final Button validate, export, folder;
    private final TextView status, path, dependencies;
    private final LinearLayout issues;
    private ValidationReport shown;

    ExportMenu(Context context, ProjectWorkspace project, ExportWorkspace exports) {
        super(context);
        this.project = project; this.exports = exports;
        setOrientation(VERTICAL);
        EditorWidgets.bindMetrics(this, () -> setPadding(dp(8), dp(4), dp(8), dp(4)));
        validate = action("export.validate", exports::validate);
        export = action("export.write", exports::export);
        status = EditorWidgets.paragraph(context, "export.unchecked");
        addView(status);
        path = EditorWidgets.paragraph(context, "");
        path.setTextIsSelectable(true);
        addView(path);
        folder = action("export.open_folder", () -> {
            var output = exports.output();
            if (output != null) Minecraft.getInstance().execute(() -> Util.getPlatform().openFile(output.toFile()));
        });
        dependencies = EditorWidgets.paragraph(context, "");
        addView(dependencies);
        issues = new LinearLayout(context);
        issues.setOrientation(VERTICAL);
        addView(issues);
        refresh();
    }

    private Button action(String key, Runnable action) {
        Button button = EditorWidgets.button(getContext(), key, () -> {
            if (isAttachedToWindow() && project.page() == ProjectWorkspace.Page.EXPORT) action.run();
        });
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        addView(button);
        EditorWidgets.bindMetrics(button, () -> button.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(28))));
        return button;
    }

    void refresh() {
        EditorWidgets.enabled(validate, exports.canRun());
        EditorWidgets.enabled(export, exports.canRun());
        status.setText(EditorWidgets.tr(exports.status()) + (exports.error().isEmpty() ? "" : "\n" + exports.error()));
        var output = exports.output();
        path.setVisibility(output == null ? GONE : VISIBLE);
        path.setText(output == null ? "" : output.toString());
        folder.setVisibility(output == null ? GONE : VISIBLE);
        var report = exports.report();
        dependencies.setVisibility(report == null || report.dependencies().isEmpty() ? GONE : VISIBLE);
        dependencies.setText(report == null ? "" : EditorWidgets.tr("export.dependencies") + "\n" + String.join("\n", report.dependencies()));
        if (shown == report) return;
        shown = report;
        issues.removeAllViews();
        if (report == null) return;
        for (ValidationIssue issue : report.issues()) {
            Button button = EditorWidgets.button(getContext(), "", () -> {
                if (exports.report() == report && isAttachedToWindow()) project.locateIssue(issue);
            });
            String owner = issue.resource() == null ? EditorWidgets.tr("project") : issue.resource().id(report.source().namespace());
            button.setText(owner + " · " + issue.field() + "\n" + describe(issue));
            button.setSingleLine(false);
            button.setEllipsize(null);
            button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            button.setTooltipText(button.getText());
            issues.addView(button, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            EditorWidgets.bindMetrics(button, () -> button.setPadding(dp(6), dp(4), dp(6), dp(4)));
        }
    }

    static String describe(ValidationIssue issue) {
        return EditorWidgets.tr("export.issue." + issue.reason()) + (issue.detail().isEmpty() ? "" : "\n" + issue.detail());
    }
}
