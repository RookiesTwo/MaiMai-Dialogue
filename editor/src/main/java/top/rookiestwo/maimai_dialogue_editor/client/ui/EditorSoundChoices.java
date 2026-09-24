package top.rookiestwo.maimai_dialogue_editor.client.ui;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.ChoicePresenter;

import icyllis.modernui.view.View;
import top.rookiestwo.maimai_dialogue_editor.client.EditorResourceCandidates;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import java.util.function.*;

/** The same project/external sound picker is used by dialogue audio and action audio. */
final class EditorSoundChoices {
    private EditorSoundChoices() {}
    static void show(ProjectWorkspace project, ChoicePresenter choices, View anchor, String selected,
                     BooleanSupplier accepts, Consumer<String> chosen) {
        EditorResourceCandidates.sounds(project, accepts, items ->
                choices.showResources(anchor, items, selected, value -> { if (accepts.getAsBoolean()) chosen.accept(value); }));
    }
}
