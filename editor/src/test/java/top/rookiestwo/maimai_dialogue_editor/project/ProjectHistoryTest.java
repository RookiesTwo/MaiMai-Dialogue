package top.rookiestwo.maimai_dialogue_editor.project;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectHistoryTest {
    private final ProjectDraft original = ProjectDraft.create("First", "example");

    @Test
    void newDraftIsDirtyEvenAfterUndoingBackToItsInitialContent() {
        ProjectHistory history = new ProjectHistory(original, false);
        assertTrue(history.dirty());
        history.edit(original.withName("Second"), "name");
        history.undo();
        assertEquals(original, history.current());
        assertTrue(history.dirty());
    }

    @Test
    void coalescesTypingUntilFocusOrActionEndsTheEdit() {
        ProjectHistory history = new ProjectHistory(original, true);
        history.edit(original.withName("A"), "name");
        history.edit(original.withName("AB"), "name");
        history.endEdit();
        history.edit(history.current().withName("ABC"), "name");
        history.undo();
        assertEquals("AB", history.current().name());
        history.undo();
        assertEquals(original, history.current());
        assertFalse(history.dirty());
    }

    @Test
    void savingCreatesAnUndoBoundaryAndDirtyTracksSavedContent() {
        ProjectHistory history = new ProjectHistory(original, true);
        ProjectDraft firstEdit = original.withName("Saved edit");
        history.edit(firstEdit, "name");
        history.markSaved(firstEdit);
        history.edit(firstEdit.withName("Unsaved edit"), "name");
        history.undo();
        assertEquals(firstEdit, history.current());
        assertFalse(history.dirty());
        history.undo();
        assertTrue(history.dirty());
        history.redo();
        assertFalse(history.dirty());
    }

    @Test
    void branchingAfterUndoDropsTheAbandonedRedoHistory() {
        ProjectHistory history = new ProjectHistory(original, true);
        history.edit(original.withName("Branch A"), "name");
        history.undo();
        history.edit(original.withNamespace("branch_b"), "namespace");
        assertFalse(history.canRedo());
        history.undo();
        assertEquals(original, history.current());
    }

    @Test
    void markingAnOlderSnapshotSavedDoesNotHideNewerChanges() {
        ProjectHistory history = new ProjectHistory(original, true);
        history.edit(original.withName("Still unsaved"), "name");
        history.markSaved(original);
        assertTrue(history.dirty());
        assertEquals("Still unsaved", history.current().name());
    }
}
