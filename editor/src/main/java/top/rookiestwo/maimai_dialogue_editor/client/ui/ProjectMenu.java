package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;

import java.util.ArrayList;
import java.util.List;

/** “项目”下拉菜单：常用操作和当前项目设置在同一个入口中。 */
final class ProjectMenu extends LinearLayout {
    private final ProjectWorkspace workspace;
    private final List<Button> items = new ArrayList<>();
    private final ProjectProperties properties;
    private final Button save;

    ProjectMenu(Context context, ProjectWorkspace workspace, Runnable resetLayout) {
        super(context);
        this.workspace = workspace;
        setOrientation(VERTICAL);
        EditorWidgets.bindMetrics(this, () -> setPadding(dp(4), dp(4), dp(4), dp(4)));
        addItem("project.new", () -> workspace.request(ProjectWorkspace.Action.NEW));
        addItem("project.open", () -> workspace.request(ProjectWorkspace.Action.OPEN));
        if (workspace.draft() != null) {
            separator();
            properties = new ProjectProperties(context, workspace);
            addView(properties, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            separator();
            save = addItem("save", () -> {
                workspace.cancel();
                workspace.save();
            });
            addItem("project.save_as", workspace::showSaveAs);
            addItem("project.close", () -> workspace.request(ProjectWorkspace.Action.CLOSE_PROJECT));
        } else {
            properties = null;
            save = null;
        }
        separator();
        addItem("reset_layout", resetLayout);
        refresh();
    }

    private Button addItem(String key, Runnable action) {
        Button button = EditorWidgets.button(getContext(), key, () -> {
            if (isAttachedToWindow() && workspace.page() == ProjectWorkspace.Page.MENU) action.run();
        });
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        items.add(button);
        addView(button);
        EditorWidgets.bindMetrics(button, () -> button.setLayoutParams(
                new LayoutParams(LayoutParams.MATCH_PARENT, dp(32))));
        return button;
    }

    private void separator() {
        View line = new View(getContext());
        line.setBackground(EditorWidgets.shape(EditorWidgets.BORDER, 0));
        addView(line);
        EditorWidgets.bindMetrics(line, () -> {
            LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            line.setLayoutParams(params);
        });
    }

    void refresh() {
        items.forEach(button -> EditorWidgets.enabled(button, !workspace.busy()));
        if (save != null) EditorWidgets.enabled(save, !workspace.busy() && workspace.dirty());
        if (properties != null) properties.refresh();
    }
}
