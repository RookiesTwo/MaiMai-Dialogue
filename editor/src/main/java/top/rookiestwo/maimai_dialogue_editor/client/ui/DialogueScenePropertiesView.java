package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.*;
import top.rookiestwo.maimai_dialogue.client.bootstrap.ClientServices;
import top.rookiestwo.maimai_dialogue_editor.document.SceneWorkspace;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.util.*;

/** A Dialogue owns only a Scene ID; the referenced resource has its own inspector and history. */
final class DialogueScenePropertiesView extends LinearLayout {
    private final ProjectWorkspace project;
    private final EditText input;
    private final Button choose;
    private final EditorPropertySection section;
    private ResourceKey bound;
    private long generation;
    private boolean refreshing;

    DialogueScenePropertiesView(Context context, ProjectWorkspace project, ChoicePresenter choices, EditorLayoutState layout) {
        super(context); this.project = project; setOrientation(VERTICAL);
        section = new EditorPropertySection(context, "edit.scene", layout); addView(section);
        input = EditorWidgets.compactInput(context, "", ignored -> {}, () -> {});
        input.setTag(EditorWidgets.DEFERRED_INPUT_TAG, Boolean.TRUE);
        input.setOnFocusChangeListener((view, focused) -> {
            if (!focused && accepts(bound, generation)) {
                String entered = input.getText().toString();
                if (!entered.equals(value())) project.content().editScene(entered);
                project.endEdit();
            }
        });
        EditorWidgets.propertyRow(section.body(), "edit.scene", input, false);
        choose = EditorWidgets.button(context, "edit.choose_resource", () -> {
            ResourceKey expected = bound; long expectedGeneration = generation;
            if (!accepts(expected, expectedGeneration)) return;
            choices.show(chooseButton(), items(), value(), selected -> {
                if (accepts(expected, expectedGeneration)) {
                    project.endEdit(); project.content().editScene(selected); project.endEdit();
                }
            });
        });
        EditorWidgets.propertyRow(section.body(), null, choose, false);
    }

    private Button chooseButton() { return choose; }
    private String value() { return SceneWorkspace.text(project.content().snapshot().data(), "scene", ""); }
    private boolean accepts(ResourceKey key, long projectGeneration) {
        var selection = project.resources().selection();
        return !refreshing && isAttachedToWindow() && project.content().active() && key != null
                && project.projectGeneration() == projectGeneration && key.kind() == ResourceKind.DIALOGUE
                && key.equals(project.content().snapshot().key()) && key.equals(selection.resource())
                && selection.type() == ResourceTree.Type.RESOURCE;
    }

    private List<ChoicePresenter.Item> items() {
        if (project.draft() == null) return List.of();
        var ids = new TreeSet<String>();
        ClientServices.get().content().current().scenes().ids().stream()
                .filter(id -> !id.getNamespace().equals(project.draft().namespace())).forEach(id -> ids.add(id.toString()));
        project.resources().catalog().keys().stream().filter(key -> key.kind() == ResourceKind.SCENE)
                .forEach(key -> ids.add(key.id(project.draft().namespace())));
        return ids.stream().map(id -> new ChoicePresenter.Item(id, id)).toList();
    }

    void refresh() {
        var next = project.content().snapshot().key();
        refreshing = true;
        try {
            if (!Objects.equals(bound, next) || generation != project.projectGeneration()) {
                clearFocus(); bound = next; generation = project.projectGeneration();
                input.setText(value());
            } else if (!input.isFocused() && !input.getText().toString().equals(value())) input.setText(value());
            section.refresh();
        } finally { refreshing = false; }
        boolean active = accepts(bound, generation);
        input.setEnabled(active); EditorWidgets.enabled(choose, active);
    }
}
