package top.rookiestwo.maimai_dialogue_editor.document.edit;

import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;

/** An edit belongs to a project generation and the exact draft that started it. */
public record EditOrigin(ProjectDraft draft, long generation) {
    public boolean matches(ProjectDraft currentDraft, long currentGeneration) {
        return draft == currentDraft && generation == currentGeneration;
    }
}
