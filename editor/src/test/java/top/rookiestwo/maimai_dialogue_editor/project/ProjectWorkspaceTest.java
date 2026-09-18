package top.rookiestwo.maimai_dialogue_editor.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** State/lifecycle tests only: no View construction, dimensions, styles or rendering assertions. */
class ProjectWorkspaceTest {
    @TempDir Path directory;
    private final Deque<Runnable> io = new ArrayDeque<>();
    private final Deque<Runnable> ui = new ArrayDeque<>();
    private final AtomicInteger closed = new AtomicInteger();
    private ProjectWorkspace workspace;
    private ProjectStore store;

    @BeforeEach
    void setUp() {
        store = new ProjectStore(directory);
        workspace = new ProjectWorkspace(store, io::add, ui::add, closed::incrementAndGet);
    }

    private void completeIo() {
        io.removeFirst().run();
        ui.removeFirst().run();
    }

    private void create() {
        workspace.request(ProjectWorkspace.Action.NEW);
        workspace.setFormName("My project");
        workspace.submitNew();
        completeIo();
    }

    @Test
    void createsUnsavedDraftWithoutWritingAndRoundTripsOnlyAfterSave() throws Exception {
        create();
        assertTrue(workspace.dirty());
        assertFalse(Files.exists(workspace.directory()));
        workspace.save();
        assertTrue(workspace.busy());
        assertTrue(workspace.dirty());
        completeIo();
        assertFalse(workspace.dirty());
        assertEquals(workspace.draft(), store.open(workspace.directory()).draft());
        workspace.request(ProjectWorkspace.Action.CLOSE_PROJECT);
        assertNull(workspace.draft());
        workspace.request(ProjectWorkspace.Action.OPEN);
        completeIo();
        workspace.openProject(workspace.projects().getFirst());
        completeIo();
        assertEquals("My project", workspace.draft().name());
        assertFalse(workspace.canUndo());
    }

    @Test
    void cancelKeepsDraftAndSaveContinuesOnlyAfterSuccessfulCompletion() {
        create();
        workspace.request(ProjectWorkspace.Action.CLOSE_EDITOR);
        assertEquals(ProjectWorkspace.Page.CONFIRM, workspace.page());
        assertEquals(0, closed.get());
        workspace.cancel();
        assertTrue(workspace.dirty());
        workspace.request(ProjectWorkspace.Action.CLOSE_EDITOR);
        workspace.saveAndContinue();
        workspace.discardAndContinue();
        assertEquals(0, closed.get());
        completeIo();
        assertEquals(1, closed.get());
        assertFalse(workspace.dirty());
    }

    @Test
    void discardCloseDoesNotWriteOrDeleteAnyProjectFile() throws Exception {
        create();
        workspace.save();
        completeIo();
        workspace.editName("Unsaved name");
        workspace.request(ProjectWorkspace.Action.CLOSE_PROJECT);
        workspace.discardAndContinue();
        assertNull(workspace.draft());
        assertEquals("My project", store.open(directory.resolve("my_project")).draft().name());
    }

    @Test
    void externalChangeBlocksSaveAndCloseWithoutLosingDraft() throws Exception {
        create();
        workspace.save();
        completeIo();
        workspace.editName("My new draft");
        Path file = workspace.directory().resolve(ProjectStore.FILE_NAME);
        Files.writeString(file, "external content");
        workspace.request(ProjectWorkspace.Action.CLOSE_EDITOR);
        workspace.saveAndContinue();
        completeIo();
        assertEquals("external_change", workspace.errorReason());
        assertEquals(ProjectWorkspace.Page.CONFIRM, workspace.page());
        assertTrue(workspace.dirty());
        assertEquals("My new draft", workspace.draft().name());
        assertEquals("external content", Files.readString(file));
        assertEquals(0, closed.get());
    }

    @Test
    void saveAsRecoversDraftAfterConflictWithoutOverwritingEitherProject() throws Exception {
        create();
        workspace.save();
        completeIo();
        workspace.editName("Recovered draft");
        Path original = workspace.directory().resolve(ProjectStore.FILE_NAME);
        Files.writeString(original, "external content");
        workspace.showSaveAs();
        workspace.setFormName("Recovered copy");
        workspace.submitSaveAs();
        completeIo();
        assertFalse(workspace.dirty());
        assertEquals(directory.resolve("my_project_copy"), workspace.directory());
        assertEquals("Recovered copy", store.open(workspace.directory()).draft().name());
        assertEquals("external content", Files.readString(original));
    }

    @Test
    void failedReplacementAndCancelledNewProjectKeepTheOriginal() throws Exception {
        create();
        ProjectDraft original = workspace.draft();
        Path other = directory.resolve("other");
        store.save(other, ProjectDraft.create("Other", "other"), null);
        workspace.request(ProjectWorkspace.Action.OPEN);
        assertEquals(ProjectWorkspace.Page.CONFIRM, workspace.page());
        workspace.discardAndContinue();
        completeIo();
        ProjectStore.Entry selected = workspace.projects().getFirst();
        Files.delete(other.resolve(ProjectStore.FILE_NAME));
        workspace.openProject(selected);
        completeIo();
        assertNotNull(workspace.errorReason());
        assertEquals(original, workspace.draft());
        assertTrue(workspace.dirty());
        workspace.cancel();
        workspace.request(ProjectWorkspace.Action.NEW);
        workspace.discardAndContinue();
        workspace.cancel();
        assertEquals(original, workspace.draft());
    }

    @Test
    void asyncWorkDoesNotUpdateStateBeforeUiCallbackAndIgnoresDisposedOwner() {
        workspace.request(ProjectWorkspace.Action.NEW);
        workspace.submitNew();
        workspace.request(ProjectWorkspace.Action.CLOSE_EDITOR);
        assertTrue(workspace.busy());
        io.removeFirst().run();
        assertNull(workspace.draft());
        workspace.dispose();
        ui.removeFirst().run();
        assertNull(workspace.draft());
        assertEquals(0, closed.get());
    }

    @Test
    void replacingViewListenerKeepsDocumentHistoryAndFormInput() {
        create();
        workspace.editName("Changed");
        workspace.endEdit();
        workspace.setListener(() -> {});
        AtomicInteger notifications = new AtomicInteger();
        workspace.setListener(notifications::incrementAndGet);
        workspace.undo();
        assertEquals("My project", workspace.draft().name());
        workspace.redo();
        assertEquals("Changed", workspace.draft().name());
        workspace.request(ProjectWorkspace.Action.NEW);
        workspace.discardAndContinue();
        workspace.setFormName("New form");
        workspace.setListener(() -> {});
        assertEquals("New form", workspace.formName());
        assertEquals(ProjectWorkspace.Page.NEW, workspace.page());
        assertTrue(notifications.get() > 0);
    }

    @Test
    void newProjectNameSuggestsNamespaceWhilePreservingManualOverrides() {
        workspace.request(ProjectWorkspace.Action.NEW);
        workspace.setFormName("First Story");
        assertEquals("first_story", workspace.formNamespace());
        workspace.setFormNamespace("custom_id");
        workspace.setFormName("Second Story");
        assertEquals("custom_id", workspace.formNamespace());
        workspace.setFormNamespace("another_id");
        workspace.setFormName("Third Story");
        assertEquals("another_id", workspace.formNamespace());
        workspace.submitNew();
        completeIo();
        assertEquals(directory.resolve("another_id"), workspace.directory());
    }

    @Test
    void newFormResetsNamespaceOverrides() {
        workspace.request(ProjectWorkspace.Action.NEW);
        workspace.setFormName("测试项目");
        assertEquals("ce_shi_xiang_mu", workspace.formNamespace());
        workspace.setFormNamespace("explicit");
        workspace.setListener(() -> {});
        workspace.setFormName("Changed Again");
        assertEquals("explicit", workspace.formNamespace());
        workspace.cancel();
        workspace.request(ProjectWorkspace.Action.NEW);
        workspace.setFormName("Fresh");
        assertEquals("fresh", workspace.formNamespace());
        workspace.submitNew();
        completeIo();
        assertEquals(directory.resolve("fresh"), workspace.directory());
    }

    @Test
    void renamingEstablishedProjectNeverChangesNamespaceOrMovesItsDirectory() {
        create();
        String namespace = workspace.draft().namespace();
        Path path = workspace.directory();
        workspace.showMenu();
        workspace.editName("Renamed Project");
        assertEquals(namespace, workspace.draft().namespace());
        assertEquals(path, workspace.directory());
        workspace.escape();
        assertEquals(ProjectWorkspace.Page.NONE, workspace.page());
        assertEquals("Renamed Project", workspace.draft().name());
        assertEquals(0, closed.get());
    }

    @Test
    void openingListsProjectsAutomaticallyAndOnlyAcceptsReadableListedEntries() throws Exception {
        Files.createDirectories(directory.resolve("broken"));
        Files.writeString(directory.resolve("broken").resolve(ProjectStore.FILE_NAME), "not json");
        workspace.request(ProjectWorkspace.Action.OPEN);
        assertTrue(workspace.busy());
        completeIo();
        assertEquals(1, workspace.projects().size());
        workspace.openProject(workspace.projects().getFirst());
        workspace.openProject(new ProjectStore.Entry(directory.resolve("absent"), "Unlisted", "", 0, null));
        assertTrue(io.isEmpty());
        assertNull(workspace.draft());
        store.save(directory.resolve("valid"), ProjectDraft.create("Valid", "valid"), null);
        workspace.refreshProjects();
        completeIo();
        ProjectStore.Entry valid = workspace.projects().stream().filter(ProjectStore.Entry::canOpen).findFirst().orElseThrow();
        workspace.openProject(valid);
        completeIo();
        assertEquals("Valid", workspace.draft().name());
    }

    @Test
    void duplicateProjectNamespacesGetDifferentStorageFolders() {
        create();
        workspace.save();
        completeIo();
        Path original = workspace.directory();
        workspace.request(ProjectWorkspace.Action.NEW);
        workspace.setFormName("My project");
        workspace.submitNew();
        completeIo();
        assertEquals(directory.resolve("my_project_2"), workspace.directory());
        assertNotEquals(original, workspace.directory());
        assertEquals("my_project", workspace.draft().namespace());
    }

    @Test
    void disposedWorkspaceIgnoresLateProjectListResult() {
        workspace.request(ProjectWorkspace.Action.OPEN);
        io.removeFirst().run();
        workspace.dispose();
        ui.removeFirst().run();
        assertTrue(workspace.projects().isEmpty());
        assertNull(workspace.draft());
    }

    @Test
    void closingSavedProjectFromMenuUnloadsDocumentButKeepsFilesAndEditor() {
        create();
        workspace.save();
        completeIo();
        Path savedDirectory = workspace.directory();
        workspace.showMenu();
        workspace.request(ProjectWorkspace.Action.CLOSE_PROJECT);
        assertEquals(ProjectWorkspace.Page.NONE, workspace.page());
        assertNull(workspace.draft());
        assertNull(workspace.directory());
        assertFalse(workspace.dirty());
        assertFalse(workspace.canUndo());
        assertTrue(Files.exists(savedDirectory.resolve(ProjectStore.FILE_NAME)));
        assertEquals(0, closed.get());
    }

    @Test
    void losingWindowFocusDismissesOnlyMenuWithoutDroppingDraftOrPendingConfirmation() {
        create();
        workspace.editName("Keep my draft");
        workspace.showMenu();
        workspace.windowFocusChanged(false);
        assertEquals(ProjectWorkspace.Page.NONE, workspace.page());
        assertEquals("Keep my draft", workspace.draft().name());
        assertTrue(workspace.dirty());
        workspace.showMenu();
        assertEquals(ProjectWorkspace.Page.NONE, workspace.page());
        workspace.windowFocusChanged(true);
        assertEquals(ProjectWorkspace.Page.NONE, workspace.page());
        workspace.showMenu();
        workspace.request(ProjectWorkspace.Action.CLOSE_PROJECT);
        assertEquals(ProjectWorkspace.Page.CONFIRM, workspace.page());
        workspace.windowFocusChanged(false);
        workspace.dismissMenu();
        assertEquals(ProjectWorkspace.Page.CONFIRM, workspace.page());
        workspace.cancel();
        assertNotNull(workspace.draft());
        assertEquals(0, closed.get());
    }
}
