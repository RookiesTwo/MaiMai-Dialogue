package top.rookiestwo.maimai_dialogue_editor.project;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Document history outlives Views; saved content is compared independently of the undo cursor. */
public final class ProjectHistory {
    private static final int LIMIT = 100;
    private final Deque<ProjectDraft> undo = new ArrayDeque<>();
    private final Deque<ProjectDraft> redo = new ArrayDeque<>();
    private ProjectDraft current;
    private ProjectDraft saved;
    private String editGroup;

    public ProjectHistory(ProjectDraft initial, boolean alreadySaved) {
        current = Objects.requireNonNull(initial);
        saved = alreadySaved ? initial : null;
    }

    public ProjectDraft current() {
        return current;
    }

    public boolean dirty() {
        return !current.equals(saved);
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void edit(ProjectDraft next, String group) {
        Objects.requireNonNull(next);
        if (next.equals(current)) {
            return;
        }
        if (group == null || !group.equals(editGroup)) {
            undo.addLast(current);
            if (undo.size() > LIMIT) {
                undo.removeFirst();
            }
        }
        current = next;
        redo.clear();
        editGroup = group;
    }

    public void endEdit() {
        editGroup = null;
    }

    public void undo() {
        endEdit();
        if (canUndo()) {
            redo.addLast(current);
            current = undo.removeLast();
        }
    }

    public void redo() {
        endEdit();
        if (canRedo()) {
            undo.addLast(current);
            current = redo.removeLast();
        }
    }

    public void markSaved(ProjectDraft written) {
        saved = Objects.requireNonNull(written);
        endEdit();
    }
}
