package top.rookiestwo.maimai_dialogue_editor.client.ui.properties;

import top.rookiestwo.maimai_dialogue_editor.client.preview.EditorPreviewHost;


import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.ChoicePresenter;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.PropertySectionState;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceTree;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;

/** Read-only resource metadata. Project settings remain in the Project menu. */
public final class ResourcePropertiesView extends LinearLayout {
    private final ProjectWorkspace workspace;
    private final TextView details;
    private final ContentPropertiesView content;
    private final TextView diagnostic;
    private final MaterialPropertiesView materials;
    private final ScenePropertiesView scenes;
    private final SceneLayoutPropertiesView sceneLayout;
    private final DialogueScenePropertiesView dialogueScene;
    private final ThemePropertiesView themes;
    private final AudioPropertiesView audio;
    private final ActionPropertiesView actions;
    private final DialogueSimulationView simulation;

    public ResourcePropertiesView(Context context, ProjectWorkspace workspace, ChoicePresenter choices, PropertySectionState layout, EditorPreviewHost preview) {
        super(context);
        this.workspace = workspace;
        setOrientation(VERTICAL);
        EditorWidgets.bindMetrics(this, () -> setPadding(dp(6), dp(4), dp(6), dp(4)));
        details = EditorWidgets.compactParagraph(context, "no_selection");
        addView(details, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        diagnostic = EditorWidgets.compactParagraph(context, "");
        diagnostic.setTextIsSelectable(true);
        addView(diagnostic);
        content = new ContentPropertiesView(context, workspace, choices, layout);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        materials = new MaterialPropertiesView(context, workspace, choices, layout);
        addView(materials, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        scenes = new ScenePropertiesView(context, workspace, choices, layout);
        addView(scenes, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        sceneLayout = new SceneLayoutPropertiesView(context, workspace, choices, layout);
        addView(sceneLayout, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        dialogueScene = new DialogueScenePropertiesView(context, workspace, choices, layout);
        addView(dialogueScene, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        themes = new ThemePropertiesView(context, workspace, choices, layout);
        addView(themes, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        audio = new AudioPropertiesView(context, workspace, choices, layout, preview.audition());
        addView(audio, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        actions = new ActionPropertiesView(context, workspace, choices, layout, preview.timeline());
        addView(actions, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        simulation = new DialogueSimulationView(context, workspace, preview.dialogue(), choices, layout);
        addView(simulation, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        refresh();
    }

    public void refresh() {
        ResourceTree.Node node = workspace.resources().selection();
        ResourceKey key = node.owner();
        boolean opened = key != null && key.equals(workspace.resources().opened());
        boolean editingAction = workspace.actions().inspecting();
        boolean editing = !editingAction && opened && (node.isStep() || key.kind() == ResourceKind.SPEAKER
                || key.kind() == ResourceKind.DIALOGUE && node.type() == ResourceTree.Type.RESOURCE);
        boolean editingMaterial = opened && (key.kind().material() || key.kind() == ResourceKind.VISUAL_ASSET);
        var issue = workspace.focusedIssue();
        boolean showIssue = issue != null && key != null && key.equals(issue.resource());
        diagnostic.setVisibility(showIssue ? VISIBLE : GONE);
        diagnostic.setText(showIssue ? issue.field() + "\n" + top.rookiestwo.maimai_dialogue_editor.client.EditorIssueText.describe(issue) : "");
        if (workspace.draft() == null || node.type() == ResourceTree.Type.PROJECT) {
            details.setText(EditorWidgets.tr("no_selection"));
        } else if (editingAction && node.isStep()) {
            details.setText(EditorWidgets.tr("action.call") + " " + (workspace.actions().selected() + 1)
                    + " · " + key.id(workspace.draft().namespace()));
        } else {
            String text = EditorWidgets.tr("resource." + node.kind().key());
            if (key != null) {
                text += " · " + key.id(workspace.draft().namespace());
                if (!editing) {
                    String name = workspace.resources().catalog().displayName(key);
                    if (!name.isBlank()) text += "\n" + EditorWidgets.tr("browser.display_name") + ": " + name;
                    text += "\n" + EditorWidgets.tr("browser.references") + " " + workspace.resources().catalog().users(key).size();
                }
            } else {
                if (node.type() == ResourceTree.Type.FOLDER) text += "\n" + node.path();
                long count = workspace.resources().catalog().keys().stream().filter(item -> item.kind() == node.kind()
                        && (node.path().isEmpty() || item.path().startsWith(node.path() + "/"))).count();
                text += "\n" + EditorWidgets.tr("browser.count") + " " + count;
            }
            if (!node.kind().available()) text += "\n" + EditorWidgets.tr("unavailable");
            details.setText(text);
        }
        details.setTooltipText(details.getText());
        content.setVisibility(editing ? VISIBLE : GONE);
        content.refresh();
        materials.setVisibility(editingMaterial ? VISIBLE : GONE);
        materials.refresh();
        scenes.setVisibility(opened && key.kind() == ResourceKind.SCENE ? VISIBLE : GONE);
        scenes.refresh();
        sceneLayout.setVisibility(opened && key.kind() == ResourceKind.SCENE ? VISIBLE : GONE);
        sceneLayout.refresh();
        dialogueScene.setVisibility(opened && node.type() == ResourceTree.Type.RESOURCE && key.kind() == ResourceKind.DIALOGUE ? VISIBLE : GONE);
        dialogueScene.refresh();
        themes.setVisibility(opened && key.kind() == ResourceKind.THEME ? VISIBLE : GONE);
        themes.refresh();
        audio.refresh();
        actions.refresh();
        simulation.setVisibility(editing && key.kind() == ResourceKind.DIALOGUE ? VISIBLE : GONE);
        simulation.refresh();
    }
}
