package top.rookiestwo.maimai_dialogue_editor.client.ui.properties;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorPropertySection;

import icyllis.modernui.graphics.Rect;
import icyllis.modernui.view.View;
import top.rookiestwo.maimai_dialogue_editor.export.ValidationIssue;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;

/** One reveal per issue request and inspector; field mapping and expansion scope stay with the inspector. */
final class EditorIssueFocus {
    private final View owner;
    private final ProjectWorkspace project;
    private long revision = -1;

    EditorIssueFocus(View owner, ProjectWorkspace project) {
        this.owner = owner; this.project = project;
    }

    boolean pending() { return revision != project.issueFocusRevision(); }

    void reveal(ValidationIssue issue, View field) {
        reveal(issue, field, () -> { if (field != null) EditorPropertySection.expandAncestors(field); });
    }

    void reveal(ValidationIssue issue, View field, Runnable expand) {
        long requested = project.issueFocusRevision();
        revision = requested;
        expand.run();
        if (field == null) return;
        owner.post(() -> {
            if (!owner.isAttachedToWindow() || !field.isAttachedToWindow()
                    || requested != project.issueFocusRevision() || project.focusedIssue() != issue) return;
            field.requestFocus();
            field.requestRectangleOnScreen(new Rect(0, 0, field.getWidth(), field.getHeight()));
        });
    }
}
