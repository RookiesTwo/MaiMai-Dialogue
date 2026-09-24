package top.rookiestwo.maimai_dialogue_editor.client.ui.project;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectStore;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 后台扫描结果的滚动列表；列表中的路径只作为内部项目标识使用。 */
final class ProjectListView extends LinearLayout {
    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final ProjectWorkspace workspace;
    private final LinearLayout rows;
    private final TextView empty;
    private final Map<Button, ProjectStore.Entry> buttons = new LinkedHashMap<>();
    private List<ProjectStore.Entry> displayed;

    ProjectListView(Context context, ProjectWorkspace workspace) {
        super(context);
        this.workspace = workspace;
        setOrientation(VERTICAL);
        empty = EditorWidgets.paragraph(context, "project.list_empty");
        addView(empty);
        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        addView(EditorWidgets.formScroll(context, rows),
                new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        refresh();
    }

    void refresh() {
        List<ProjectStore.Entry> entries = workspace.projects();
        if (displayed != entries) {
            displayed = entries;
            rows.removeAllViews();
            buttons.clear();
            for (ProjectStore.Entry entry : entries) addProject(entry);
        }
        empty.setVisibility(entries.isEmpty() && !workspace.busy() && workspace.errorReason() == null
                ? VISIBLE : GONE);
        buttons.forEach((button, entry) -> EditorWidgets.enabled(button, !workspace.busy() && entry.canOpen()));
    }

    private void addProject(ProjectStore.Entry entry) {
        Button button = EditorWidgets.button(getContext(), "",
                entry.canOpen() ? () -> workspace.openProject(entry) : null);
        button.setSingleLine(false);
        button.setMaxLines(3);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        String name = entry.name().isBlank() ? EditorWidgets.tr("project.untitled") : entry.name();
        String modified = entry.modifiedMillis() == 0 ? EditorWidgets.tr("project.modified_unknown")
                : MODIFIED.format(Instant.ofEpochMilli(entry.modifiedMillis()));
        String details = entry.canOpen() ? entry.namespace() : EditorWidgets.tr("project.error." + entry.errorReason());
        button.setText(name + "\n" + details + "\n" + EditorWidgets.tr("project.modified") + " " + modified
                + " · " + entry.directory().getFileName());
        button.setTooltipText(name + "\n" + details);
        buttons.put(button, entry);
        rows.addView(button);
        EditorWidgets.bindMetrics(button, () -> {
            LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, dp(78));
            params.setMargins(0, 0, dp(6), dp(4));
            button.setLayoutParams(params);
        });
    }
}
