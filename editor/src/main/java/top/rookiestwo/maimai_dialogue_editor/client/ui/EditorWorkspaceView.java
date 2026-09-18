package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.KeyEvent;
import top.rookiestwo.maimai_dialogue.client.ui.layout.ResponsiveFrameLayout;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceWorkspace;

/** View binding only. The Fragment owns the document, forms and IO lifecycle. */
final class EditorWorkspaceView extends ResponsiveFrameLayout {
    private final ProjectWorkspace workspace;
    private final EditorWorkbench workbench;
    private ProjectWorkspace.Page shown = ProjectWorkspace.Page.NONE;
    private ProjectDialog dialog;
    private EditorDropdownMenu dropdown;
    private ProjectMenu projectMenu;
    private ResourceWorkspace.Form shownResourceForm = ResourceWorkspace.Form.NONE;
    private ResourceDialog resourceDialog;

    EditorWorkspaceView(Context context, EditorLayoutState layout, ProjectWorkspace workspace) {
        super(context);
        this.workspace = workspace;
        setFocusable(true);
        setFocusableInTouchMode(true);
        workbench = new EditorWorkbench(context, layout, workspace,
                () -> workspace.request(ProjectWorkspace.Action.CLOSE_EDITOR));
        addView(workbench, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        refresh();
    }

    void refresh() {
        workbench.refreshProject();
        ProjectWorkspace.Page page = workspace.page();
        if (shown != page) {
            workbench.cancelDrags();
            if (dialog != null) removeView(dialog);
            dialog = null;
            releaseDropdown();
            shown = page;
            workbench.setDescendantFocusability(page == ProjectWorkspace.Page.NONE
                    ? FOCUS_AFTER_DESCENDANTS : FOCUS_BLOCK_DESCENDANTS);
            if (page == ProjectWorkspace.Page.MENU) {
                projectMenu = new ProjectMenu(getContext(), workspace);
                dropdown = new EditorDropdownMenu(projectMenu, workbench.projectMenuAnchor(), workspace::dismissMenu);
                addView(dropdown, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                dropdown.requestFocus();
            } else if (page != ProjectWorkspace.Page.NONE) {
                dialog = new ProjectDialog(getContext(), workspace);
                addView(dialog, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                dialog.focusFirst();
            } else {
                requestFocus();
            }
        }
        if (dialog != null) dialog.refresh();
        if (projectMenu != null) projectMenu.refresh();
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
        workbench.setDescendantFocusability(page == ProjectWorkspace.Page.NONE && resourceForm == ResourceWorkspace.Form.NONE
                ? FOCUS_AFTER_DESCENDANTS : FOCUS_BLOCK_DESCENDANTS);
        if (resourceDialog != null) resourceDialog.refresh();
    }

    void escape() {
        workspace.escape();
    }

    void cancelDrags() {
        workbench.cancelDrags();
    }

    void releaseDropdown() {
        if (dropdown != null) {
            dropdown.dispose();
            removeView(dropdown);
            dropdown = null;
            projectMenu = null;
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
        if (densityChanged && dropdown != null) EditorWidgets.refreshMetrics(dropdown);
        if (densityChanged && resourceDialog != null) EditorWidgets.refreshMetrics(resourceDialog);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            cancelDrags();
            workspace.dismissMenu();
        }
    }
}
