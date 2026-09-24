package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceCatalog;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceWorkspace;

import java.util.LinkedHashMap;
import java.util.Map;

/** Resource ID forms and deletion confirmation in the existing workspace View tree. */
final class ResourceDialog extends EditorModalLayout {
    private final ProjectWorkspace workspace;
    private final ResourceWorkspace resources;
    private final LinearLayout content;
    private final EditText path;
    private final TextView id;
    private final TextView feedback;
    private final Button submit;
    private final Map<ResourceKind, Button> kinds = new LinkedHashMap<>();
    private boolean refreshing;

    ResourceDialog(Context context, ProjectWorkspace workspace) {
        super(context, 520, 480);
        this.workspace = workspace;
        resources = workspace.resources();
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        EditorWidgets.bindMetrics(content, () -> content.setPadding(dp(18), dp(12), dp(18), dp(12)));
        String titleKey = switch (resources.form()) {
            case CREATE -> "browser.create_title";
            case COPY -> "browser.copy_title";
            case DELETE -> "browser.delete_title";
            case EXTRACT -> "browser.extract_title";
            default -> throw new IllegalStateException("No resource form");
        };
        TextView title = EditorWidgets.label(context, titleKey, 18, EditorWidgets.ACCENT);
        content.addView(title);
        EditorWidgets.bindMetrics(title, () -> title.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(40))));
        if (resources.form() == ResourceWorkspace.Form.CREATE) {
            for (ResourceKind kind : ResourceKind.values()) {
                if (kind.creatable()) kinds.put(kind, EditorWidgets.formButton(content, "resource." + kind.key(), () -> resources.setFormKind(kind)));
            }
        }
        if (resources.form() != ResourceWorkspace.Form.DELETE) {
            EditorWidgets.formLabel(content, "browser.path");
            path = EditorWidgets.input(context, resources.formPath(), value -> {
                if (!refreshing) resources.setFormPath(value);
            }, () -> {});
            content.addView(path, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            content.addView(EditorWidgets.paragraph(context, "browser.path_help"));
        } else {
            path = null;
            content.addView(EditorWidgets.paragraph(context, "browser.delete_confirm"));
        }
        id = EditorWidgets.paragraph(context, "");
        content.addView(id);
        feedback = EditorWidgets.paragraph(context, "");
        content.addView(feedback);
        submit = EditorWidgets.formButton(content, resources.form() == ResourceWorkspace.Form.DELETE ? "browser.delete" : "browser.confirm", resources::submit);
        EditorWidgets.formButton(content, "project.cancel", resources::cancel);
        setPanel(EditorWidgets.formScroll(context, content));
        refresh();
    }

    void focusFirst() { if (path != null) path.requestFocus(); else requestFocus(); }

    void refresh() {
        refreshing = true;
        try {
            if (path != null && !path.getText().toString().equals(resources.formPath())) path.setText(resources.formPath());
        } finally {
            refreshing = false;
        }
        kinds.forEach((kind, button) -> button.setSelected(kind == resources.formKind()));
        id.setText(EditorWidgets.tr("resource." + resources.formKind().key()) + " · "
                + workspace.draft().namespace() + ":" + resources.formPath());
        String text = resources.error() == null ? "" : EditorWidgets.tr("browser.error." + resources.error());
        boolean blocked = resources.form() == ResourceWorkspace.Form.DELETE && !resources.blockers().isEmpty();
        if (blocked) {
            StringBuilder uses = new StringBuilder(EditorWidgets.tr("browser.error.referenced"));
            for (ResourceCatalog.Use use : resources.blockers()) {
                uses.append("\n").append(EditorWidgets.tr("resource." + use.source().kind().key())).append(" · ")
                        .append(use.source().id(workspace.draft().namespace())).append("\n  ").append(use.field());
            }
            text = uses.toString();
        }
        feedback.setText(text);
        feedback.setVisibility(text.isEmpty() ? GONE : VISIBLE);
        EditorWidgets.enabled(submit, resources.active() && !blocked);
    }

}
