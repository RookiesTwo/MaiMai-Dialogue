package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import top.rookiestwo.maimai_dialogue.client.ui.layout.ResponsiveFrameLayout;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceWorkspace;
import top.rookiestwo.maimai_dialogue_editor.export.ExportWorkspace;

import java.util.List;
import java.util.function.Consumer;

/** View binding only. The Fragment owns the document, forms and IO lifecycle. */
final class EditorWorkspaceView extends ResponsiveFrameLayout {
    private final ProjectWorkspace workspace;
    private final EditorWorkbench workbench;
    private ProjectWorkspace.Page shown = ProjectWorkspace.Page.NONE;
    private ProjectDialog dialog;
    private EditorDropdownMenu dropdown;
    private ProjectMenu projectMenu;
    private ExportMenu exportMenu;
    private final ExportWorkspace exports;
    private ResourceWorkspace.Form shownResourceForm = ResourceWorkspace.Form.NONE;
    private ResourceDialog resourceDialog;
    private EditorDropdownMenu choices;
    private MaterialImportDialog materialDialog;

    EditorWorkspaceView(Context context, EditorLayoutState layout, ProjectWorkspace workspace, EditorPreviewHost preview,
                        ExportWorkspace exports) {
        super(context);
        this.workspace = workspace;
        this.exports = exports;
        setFocusable(true);
        setFocusableInTouchMode(true);
        workbench = new EditorWorkbench(context, layout, workspace,
                () -> workspace.request(ProjectWorkspace.Action.CLOSE_EDITOR), this::showChoices, preview, exports);
        addView(workbench, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        refresh();
    }

    void refresh() {
        workbench.refreshProject();
        ProjectWorkspace.Page page = workspace.page();
        if (page != ProjectWorkspace.Page.NONE || workspace.resources().form() != ResourceWorkspace.Form.NONE) dismissChoices();
        if (shown != page) {
            workbench.cancelDrags();
            if (dialog != null) removeView(dialog);
            dialog = null;
            if (materialDialog != null) removeView(materialDialog);
            materialDialog = null;
            releaseDropdown();
            shown = page;
            workbench.setDescendantFocusability(page == ProjectWorkspace.Page.NONE
                    ? FOCUS_AFTER_DESCENDANTS : FOCUS_BLOCK_DESCENDANTS);
            if (page == ProjectWorkspace.Page.MENU) {
                projectMenu = new ProjectMenu(getContext(), workspace);
                dropdown = new EditorDropdownMenu(projectMenu, workbench.projectMenuAnchor(), workspace::dismissMenu);
                addView(dropdown, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                dropdown.requestFocus();
            } else if (page == ProjectWorkspace.Page.EXPORT) {
                exportMenu = new ExportMenu(getContext(), workspace, exports);
                dropdown = new EditorDropdownMenu(exportMenu, workbench.exportMenuAnchor(), workspace::dismissMenu);
                addView(dropdown, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                dropdown.requestFocus();
            } else if (page == ProjectWorkspace.Page.IMPORT) {
                materialDialog = new MaterialImportDialog(getContext(), workspace);
                addView(materialDialog, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                materialDialog.requestFocus();
            } else if (page != ProjectWorkspace.Page.NONE) {
                dialog = new ProjectDialog(getContext(), workspace);
                addView(dialog, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                dialog.focusFirst();
            } else {
                requestFocus();
            }
        }
        if (dialog != null) dialog.refresh();
        if (materialDialog != null) materialDialog.refresh();
        if (projectMenu != null) projectMenu.refresh();
        if (exportMenu != null) exportMenu.refresh();
        if (dropdown != null) dropdown.requestLayout();
        ResourceWorkspace.Form resourceForm = workspace.resources().form();
        if (shownResourceForm != resourceForm) {
            workbench.cancelDrags();
            if (resourceDialog != null) removeView(resourceDialog);
            resourceDialog = null;
            shownResourceForm = resourceForm;
            if (resourceForm != ResourceWorkspace.Form.NONE) {
                resourceDialog = new ResourceDialog(getContext(), workspace);
                addView(resourceDialog, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                resourceDialog.focusFirst();
            } else if (page == ProjectWorkspace.Page.NONE) {
                requestFocus();
            }
        }
        workbench.setDescendantFocusability(page == ProjectWorkspace.Page.NONE && resourceForm == ResourceWorkspace.Form.NONE && choices == null
                ? FOCUS_AFTER_DESCENDANTS : FOCUS_BLOCK_DESCENDANTS);
        if (resourceDialog != null) resourceDialog.refresh();
    }

    void escape() {
        if (choices != null) dismissChoices();
        else workspace.escape();
    }

    private void showChoices(View anchor, List<ChoicePresenter.Item> items, String selected, Consumer<String> chosen) {
        if (!workspace.content().active() || !workspace.windowFocused() || !anchor.isAttachedToWindow()) return;
        dismissChoices();
        workspace.endEdit();
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        EditorDropdownMenu menu = EditorDropdownMenu.forField(list, anchor, this::dismissChoices);
        for (ChoicePresenter.Item item : items) {
            Button button = EditorWidgets.button(getContext(), "", () -> {
                if (choices != menu) return;
                dismissChoices();
                if (anchor.isAttachedToWindow() && workspace.content().active()) chosen.accept(item.value());
            });
            button.setText(item.label());
            button.setTooltipText(item.label());
            button.setSelected(item.value().equals(selected));
            button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            list.addView(button);
            EditorWidgets.bindMetrics(button, () -> {
                button.setPadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP),
                        0, dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0);
                button.setLayoutParams(new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_ROW_DP)));
            });
        }
        if (items.isEmpty()) list.addView(EditorWidgets.paragraph(getContext(), "browser.empty"));
        choices = menu;
        workbench.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        addView(menu, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        menu.requestFocus();
    }

    void dismissChoices() {
        if (choices == null) return;
        choices.dispose();
        removeView(choices);
        choices = null;
        if (workspace.page() == ProjectWorkspace.Page.NONE && workspace.resources().form() == ResourceWorkspace.Form.NONE) {
            workbench.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
            requestFocus();
        }
    }

    void cancelDrags() {
        workbench.cancelDrags();
    }

    void releaseDropdown() {
        dismissChoices();
        if (dropdown != null) {
            dropdown.dispose();
            removeView(dropdown);
            dropdown = null;
            projectMenu = null;
            exportMenu = null;
            shown = ProjectWorkspace.Page.NONE;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(() -> {
            if (isAttachedToWindow()) refresh();
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseDropdown();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEY_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) escape();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onViewportChanged(boolean densityChanged) {
        cancelDrags();
        if (densityChanged && dialog != null) EditorWidgets.refreshMetrics(dialog);
        if (densityChanged && materialDialog != null) EditorWidgets.refreshMetrics(materialDialog);
        if (densityChanged && dropdown != null) EditorWidgets.refreshMetrics(dropdown);
        if (densityChanged && resourceDialog != null) EditorWidgets.refreshMetrics(resourceDialog);
        if (densityChanged && choices != null) EditorWidgets.refreshMetrics(choices);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            cancelDrags();
            workspace.dismissMenu();
            dismissChoices();
        }
    }
}
