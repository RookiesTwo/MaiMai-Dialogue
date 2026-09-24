package top.rookiestwo.maimai_dialogue_editor.client.ui.workbench;

import top.rookiestwo.maimai_dialogue_editor.client.preview.EditorPreviewHost;

import top.rookiestwo.maimai_dialogue_editor.client.ui.project.ExportMenu;
import top.rookiestwo.maimai_dialogue_editor.client.ui.project.MaterialImportConfirmation;
import top.rookiestwo.maimai_dialogue_editor.client.ui.project.ProjectDialog;
import top.rookiestwo.maimai_dialogue_editor.client.ui.project.ProjectMenu;
import top.rookiestwo.maimai_dialogue_editor.client.ui.resource.ResourceDialog;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.ChoicePresenter;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorColorPalette;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorDropdownMenu;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorSearchChoices;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewTreeObserver;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import top.rookiestwo.maimai_dialogue.client.ui.layout.ResponsiveFrameLayout;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceWorkspace;
import top.rookiestwo.maimai_dialogue_editor.export.ExportWorkspace;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    private EditorColorPalette colorPalette;
    private long paletteRevision;
    private MaterialImportConfirmation materialDialog;
    private Object nativeInput;
    private ViewTreeObserver tooltipObserver;
    private final ViewTreeObserver.OnGlobalLayoutListener tooltipLayoutListener =
            () -> EditorWidgets.styleTooltips(this);

    EditorWorkspaceView(Context context, EditorLayoutState layout, ProjectWorkspace workspace, EditorPreviewHost preview,
                        ExportWorkspace exports, EditorFragment owner) {
        super(context);
        this.workspace = workspace;
        this.exports = exports;
        setFocusable(true);
        setFocusableInTouchMode(true);
        ChoicePresenter presenter = new ChoicePresenter() {
            @Override public void editCommand(View anchor, String initial, Consumer<String> confirmed) {
                if (nativeInput != null || !workspace.content().active() || !workspace.windowFocused() || !anchor.isAttachedToWindow()) return;
                dismissChoices(); finishDeferredInput(); cancelDrags(); workspace.endEdit();
                var request = new Object(); nativeInput = request;
                requestFocus();
                top.rookiestwo.maimai_dialogue_editor.client.EditorScreens.editCommand(owner, initial, value -> {
                    if (isAttachedToWindow() && anchor.isAttachedToWindow() && workspace.content().active()) confirmed.accept(value);
                }, () -> {
                    if (nativeInput == request) { nativeInput = null; if (isAttachedToWindow()) requestFocus(); }
                });
            }
            @Override public void show(View anchor, List<Item> items, String selected, Consumer<String> chosen) {
                showChoices(anchor, items, selected, chosen);
            }
            @Override public void showMenu(View anchor, List<Item> items, String selected, Consumer<String> chosen) {
                showChoices(anchor, items, selected, chosen, false);
            }
            @Override public void showMenuAt(View anchor, float x, float y, List<Item> items, Consumer<String> chosen) {
                showChoices(anchor, items, "", chosen, false, new icyllis.modernui.graphics.PointF(x, y));
            }
            @Override public void showSearchable(View anchor, List<Item> items, String selected, Consumer<String> chosen) {
                if (!workspace.content().active() || !workspace.windowFocused() || !anchor.isAttachedToWindow()) return;
                dismissChoices(); workspace.endEdit();
                var content = new EditorSearchChoices(getContext(), items, selected, value -> {
                    dismissChoices();
                    if (anchor.isAttachedToWindow() && workspace.content().active()) chosen.accept(value);
                });
                choices = resourceChoices(content, anchor);
                workbench.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
                addView(choices, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                choices.requestFocus();
            }
            @Override public void showColor(View anchor, Supplier<String> value, Consumer<String> changed) {
                showColorPalette(anchor, value, changed, null);
            }
            @Override public void showColor(View anchor, Supplier<String> value, Consumer<String> changed,
                    Supplier<? extends top.rookiestwo.maimai_dialogue_editor.document.EditGesture> gesture) {
                showColorPalette(anchor, value, changed, gesture);
            }
        };
        workbench = new EditorWorkbench(context, layout, workspace,
                () -> workspace.request(ProjectWorkspace.Action.CLOSE_EDITOR), presenter, preview, exports);
        addView(workbench, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        refresh();
    }

    void refresh() {
        workbench.refreshProject();
        ProjectWorkspace.Page page = workspace.page();
        if (page != ProjectWorkspace.Page.NONE || workspace.resources().form() != ResourceWorkspace.Form.NONE) dismissChoices();
        if (choices != null && !choices.hasAnchor()) dismissChoices();
        if (colorPalette != null) colorPalette.refresh();
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
                projectMenu = new ProjectMenu(getContext(), workspace, () -> {
                    workspace.dismissMenu();
                    workbench.restoreDefaultLayout();
                });
                dropdown = new EditorDropdownMenu(projectMenu, workbench.projectMenuAnchor(), workspace::dismissMenu);
                addView(dropdown, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                dropdown.requestFocus();
            } else if (page == ProjectWorkspace.Page.EXPORT) {
                exportMenu = new ExportMenu(getContext(), workspace, exports);
                dropdown = new EditorDropdownMenu(exportMenu, workbench.exportMenuAnchor(), workspace::dismissMenu);
                addView(dropdown, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                dropdown.requestFocus();
            } else if (page == ProjectWorkspace.Page.IMPORT) {
                materialDialog = new MaterialImportConfirmation(getContext(), workspace);
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

    void refreshSaveState() {
        workbench.refreshSaveState();
        if (projectMenu != null) projectMenu.refreshSaveState();
    }

    void escape() {
        if (nativeInputOpen()) return;
        if (workspace.actions().editing()) { workspace.actions().endGesture(false); return; }
        if (workspace.audio().editing()) { workspace.audio().endGesture(false); return; }
        if (workspace.themes().editing()) {
            workspace.themes().endGesture(false);
            if (choices != null) dismissChoices();
            return;
        }
        if (workspace.scenes().numberPreview() != null) {
            workspace.scenes().endNumberDrag(false);
            return;
        }
        if (workspace.scenes().dragPosition() != null) {
            workspace.scenes().endPositionDrag(false);
            return;
        }
        finishDeferredInput();
        if (choices != null) dismissChoices();
        else workspace.escape();
    }

    private void finishDeferredInput() {
        View focused = findFocus();
        if (focused != null && Boolean.TRUE.equals(focused.getTag(EditorWidgets.DEFERRED_INPUT_TAG))) requestFocus();
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        // ModernUI also receives raw mouse input while the native GUI layer is on top.
        if (nativeInputOpen()) return true;
        View focused = findFocus();
        if (event.getAction() == MotionEvent.ACTION_DOWN && focused != null
                && Boolean.TRUE.equals(focused.getTag(EditorWidgets.DEFERRED_INPUT_TAG))) {
            int[] inputLocation = new int[2], rootLocation = new int[2];
            focused.getLocationInWindow(inputLocation);
            getLocationInWindow(rootLocation);
            float x = event.getX() + rootLocation[0] - inputLocation[0];
            float y = event.getY() + rootLocation[1] - inputLocation[1];
            if (x < 0 || y < 0 || x >= focused.getWidth() || y >= focused.getHeight()) {
                // Commit before the clicked control takes a project snapshot or changes the selected variant.
                finishDeferredInput();
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private EditorDropdownMenu resourceChoices(View content, View anchor) {
        // Resource menus retain their reading width when their launch button follows its text width.
        return EditorWidgets.propertyAction(anchor)
                ? EditorDropdownMenu.forContent(content, anchor, 280, this::dismissChoices)
                : EditorDropdownMenu.forField(content, anchor, this::dismissChoices);
    }

    private void showChoices(View anchor, List<ChoicePresenter.Item> items, String selected, Consumer<String> chosen) {
        showChoices(anchor, items, selected, chosen, true);
    }

    private void showChoices(View anchor, List<ChoicePresenter.Item> items, String selected, Consumer<String> chosen, boolean matchAnchorWidth) {
        showChoices(anchor, items, selected, chosen, matchAnchorWidth, null);
    }
    private void showChoices(View anchor, List<ChoicePresenter.Item> items, String selected, Consumer<String> chosen,
                             boolean matchAnchorWidth, icyllis.modernui.graphics.PointF point) {
        if (!workspace.content().active() || !workspace.windowFocused() || !anchor.isAttachedToWindow()) return;
        dismissChoices();
        workspace.endEdit();
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        EditorDropdownMenu menu = point != null ? EditorDropdownMenu.atPoint(list, anchor, point.x, point.y, this::dismissChoices)
                : matchAnchorWidth ? resourceChoices(list, anchor)
                : EditorDropdownMenu.forContent(list, anchor, 180, this::dismissChoices);
        for (ChoicePresenter.Item item : items) {
            list.addView(EditorWidgets.choiceRow(getContext(), item, selected, value -> {
                if (choices != menu) return;
                dismissChoices();
                if (anchor.isAttachedToWindow() && workspace.content().active()) chosen.accept(value);
            }));
        }
        if (items.isEmpty()) list.addView(EditorWidgets.paragraph(getContext(), "browser.empty"));
        choices = menu;
        workbench.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        addView(menu, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        menu.requestFocus();
    }

    private void showColorPalette(View anchor, Supplier<String> value, Consumer<String> changed,
            Supplier<? extends top.rookiestwo.maimai_dialogue_editor.document.EditGesture> gesture) {
        if (!workspace.content().active() || !workspace.windowFocused() || !anchor.isAttachedToWindow()) return;
        dismissChoices();
        workspace.endEdit();
        long request = ++paletteRevision;
        EditorColorPalette palette = new EditorColorPalette(getContext(), value, selected -> {
            if (request == paletteRevision && colorPalette != null && anchor.isAttachedToWindow()
                    && workspace.content().active()) changed.accept(selected);
        }, workspace::endEdit, () -> { if (request == paletteRevision) dismissChoices(); }, gesture);
        colorPalette = palette;
        choices = EditorDropdownMenu.forContent(palette, anchor, 300, this::dismissChoices);
        workbench.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        addView(choices, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        choices.requestFocus();
    }

    void dismissChoices() {
        if (choices == null) return;
        EditorDropdownMenu previous = choices;
        choices = null;
        if (colorPalette != null) colorPalette.finishGesture();
        colorPalette = null;
        paletteRevision++;
        previous.dispose();
        removeView(previous);
        workspace.endEdit();
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
        tooltipObserver = getViewTreeObserver();
        tooltipObserver.addOnGlobalLayoutListener(tooltipLayoutListener);
        post(() -> {
            if (isAttachedToWindow()) refresh();
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        if (tooltipObserver != null && tooltipObserver.isAlive()) {
            tooltipObserver.removeOnGlobalLayoutListener(tooltipLayoutListener);
        }
        tooltipObserver = null;
        releaseDropdown();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (nativeInputOpen()) return true;
        if (event.getKeyCode() == KeyEvent.KEY_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) escape();
            return true;
        }
        Shortcut shortcut = Shortcut.from(event);
        if (shortcut != null) {
            // Handle once before ViewRoot's normal key -> text shortcut dispatch, including key repeats/up.
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) runShortcut(shortcut);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    boolean nativeInputOpen() { return nativeInput != null; }

    @Override public boolean dispatchGenericMotionEvent(@NonNull MotionEvent event) {
        return nativeInputOpen() || super.dispatchGenericMotionEvent(event);
    }

    private void runShortcut(Shortcut shortcut) {
        if (!isAttachedToWindow() || !workspace.windowFocused() || workspace.busy()) return;
        View focused = findFocus();
        if (shortcut != Shortcut.SAVE && focused instanceof EditText input) {
            // Use ModernUI's text history even when empty; never fall through to project history.
            if (input.isEnabled()) input.onTextContextMenuItem(shortcut == Shortcut.UNDO ? R.id.undo : R.id.redo);
            return;
        }
        // Saving from the Project menu is valid; modal forms and pickers must not edit the project behind them.
        boolean saveFromMenu = shortcut == Shortcut.SAVE && workspace.page() == ProjectWorkspace.Page.MENU;
        if ((workspace.page() != ProjectWorkspace.Page.NONE && !saveFromMenu)
                || workspace.resources().form() != ResourceWorkspace.Form.NONE || choices != null
                || workspace.draft() == null) return;
        // Saving has the same commit boundary as clicking Save outside a deferred input.
        finishDeferredInput();
        if (saveFromMenu) workspace.dismissMenu();
        cancelDrags();
        switch (shortcut) {
            case SAVE -> { if (workspace.dirty()) workspace.save(); }
            case UNDO -> workspace.undo();
            case REDO -> workspace.redo();
        }
    }

    private enum Shortcut {
        SAVE, UNDO, REDO;

        static Shortcut from(KeyEvent event) {
            // Ignore Caps/Num Lock, but do not capture AltGr or unrelated modifier combinations.
            int modifiers = event.getModifiers() & (KeyEvent.META_SHIFT_ON | KeyEvent.META_CONTROL_ON
                    | KeyEvent.META_ALT_ON | KeyEvent.META_SUPER_ON);
            if (modifiers == KeyEvent.META_SHORTCUT_ON) {
                return switch (event.getKeyCode()) {
                    case KeyEvent.KEY_S -> SAVE;
                    case KeyEvent.KEY_Z -> UNDO;
                    case KeyEvent.KEY_Y -> REDO;
                    default -> null;
                };
            }
            return modifiers == (KeyEvent.META_SHORTCUT_ON | KeyEvent.META_SHIFT_ON)
                    && event.getKeyCode() == KeyEvent.KEY_Z ? REDO : null;
        }
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
