package top.rookiestwo.maimai_dialogue_editor.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectHistory;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ResourceWorkspaceTest {
    private final ProjectHistory history = new ProjectHistory(ProjectDraft.create("Story", "story"), true);
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final ResourceWorkspace resources = new ResourceWorkspace(history::current,
            next -> history.edit(next, null), enabled::get, () -> {});

    private ResourceKey create(ResourceKind kind, String path) {
        resources.select(ResourceTree.Node.category(kind));
        resources.beginCreate();
        resources.setFormKind(kind);
        resources.setFormPath(path);
        resources.submit();
        return new ResourceKey(kind, path);
    }

    @Test
    void createCopyAndDeleteShareDocumentHistoryAndNeverMutateTheCopiedSource() {
        ResourceKey intro = create(ResourceKind.DIALOGUE, "chapter/intro");
        assertEquals(intro, resources.opened());
        assertEquals(intro, resources.selection().resource());
        assertTrue(history.dirty());
        history.edit(history.current().withResource(intro,
                JsonParser.parseString("{\"unknown\":[null,2],\"end\":null}")), null);
        resources.beginCopy();
        assertEquals("chapter/intro_copy", resources.formPath());
        resources.submit();
        ResourceKey copy = resources.opened();
        assertNotEquals(intro, copy);
        assertEquals(history.current().resource(intro), history.current().resource(copy));
        resources.beginDelete();
        resources.submit();
        assertNull(resources.opened());
        assertNull(resources.selection().resource());
        assertNotNull(history.current().resource(intro));
        assertNull(history.current().resource(copy));
        history.undo();
        assertTrue(resources.catalog().contains(copy));
        resources.open(copy);
        history.redo();
        assertNull(resources.opened());
        assertNull(resources.selection().resource());
    }

    @Test
    void duplicateOrInvalidIdsAndUnavailableTypesDoNotChangeDocument() {
        create(ResourceKind.DIALOGUE, "intro");
        ProjectDraft saved = history.current();
        resources.beginCreate();
        resources.setFormPath("intro");
        resources.submit();
        assertEquals("duplicate", resources.error());
        resources.setFormPath("../intro");
        resources.submit();
        assertEquals("invalid_path", resources.error());
        resources.cancel();
        resources.select(ResourceTree.Node.category(ResourceKind.SCENE));
        resources.beginCreate();
        assertEquals(ResourceWorkspace.Form.NONE, resources.form());
        assertFalse(resources.canCreate());
        assertEquals(saved, history.current());
    }

    @Test
    void deletionIsBlockedByReferencesIncludingChangesAfterConfirmationOpened() {
        ResourceKey hero = create(ResourceKind.SPEAKER, "hero");
        resources.beginDelete();
        ResourceKey intro = new ResourceKey(ResourceKind.DIALOGUE, "intro");
        history.edit(history.current().withResource(intro, JsonParser.parseString("""
                {"steps":[{"speaker":{"type":"set","id":"story:hero"}}]}
                """)), null);
        resources.submit();
        assertEquals("referenced", resources.error());
        assertEquals(ResourceWorkspace.Form.DELETE, resources.form());
        assertEquals(intro, resources.blockers().getFirst().source());
        assertNotNull(history.current().resource(hero));
        resources.cancel();
        resources.open(intro);
        resources.beginDelete();
        resources.submit();
        resources.open(hero);
        resources.beginDelete();
        resources.submit();
        assertNull(history.current().resource(hero));
    }

    @Test
    void inspectionDoesNotReplaceOpenDocumentAndRevealingSearchResultKeepsIdentitiesConsistent() {
        ResourceKey intro = create(ResourceKind.DIALOGUE, "a/intro");
        ResourceKey hero = create(ResourceKind.SPEAKER, "people/hero");
        resources.select(ResourceTree.Node.resource(intro));
        assertEquals(intro, resources.selection().resource());
        assertEquals(hero, resources.opened());
        resources.toggle(ResourceTree.Node.category(ResourceKind.DIALOGUE));
        resources.setQuery("intro");
        resources.open(intro);
        assertEquals("", resources.query());
        assertEquals(intro, resources.opened());
        assertEquals(intro, resources.selection().resource());
        assertTrue(resources.rows().stream().anyMatch(row -> row.node().equals(ResourceTree.Node.resource(intro))));
        resources.closeDocument();
        assertNull(resources.opened());
        assertEquals(intro, resources.selection().resource());
    }

    @Test
    void fileOperationsLockMutationsAndCancelDoesNotLoseDraftOrNavigation() {
        ResourceKey intro = create(ResourceKind.DIALOGUE, "intro");
        ProjectDraft before = history.current();
        resources.beginCopy();
        resources.setFormPath("copy");
        enabled.set(false);
        resources.submit();
        assertEquals(before, history.current());
        assertEquals(ResourceWorkspace.Form.COPY, resources.form());
        enabled.set(true);
        resources.cancel();
        assertEquals(intro, resources.opened());
        assertEquals(before, history.current());
        resources.reset();
        assertNull(resources.opened());
        assertEquals(ResourceTree.Node.project(), resources.selection());
        assertFalse(resources.catalog().keys().isEmpty());
    }
}
