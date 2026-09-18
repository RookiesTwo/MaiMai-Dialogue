package top.rookiestwo.maimai_dialogue_editor.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Data/index tests only. No View, layout, style or rendering assertions. */
class ResourceCatalogTest {
    private static final ResourceKey INTRO = new ResourceKey(ResourceKind.DIALOGUE, "chapter/intro");
    private static final ResourceKey NEXT = new ResourceKey(ResourceKind.DIALOGUE, "chapter/next");
    private static final ResourceKey HERO = new ResourceKey(ResourceKind.SPEAKER, "people/hero");

    private static JsonElement json(String text) { return JsonParser.parseString(text); }
    private static ProjectDraft project() { return ProjectDraft.create("Story", "story"); }

    @Test
    void resourceMutationsAreImmutableAndSavePreservesUnknownAndIncompleteDrafts(@TempDir Path root) throws Exception {
        JsonObject raw = project().toJson();
        raw.add("future_project_field", json("[null,1]"));
        raw.getAsJsonObject("resources").add("future_kind", json("{\"keep\":null}"));
        ProjectDraft original = ProjectDraft.fromJson(raw);
        JsonElement body = json("{\"text\":null,\"speaker\":\"\",\"future_field\":[1,2]}");
        ProjectDraft draft = original.withResource(INTRO, body).withResource(HERO, ResourceCatalog.emptyDraft(ResourceKind.SPEAKER));
        body.getAsJsonObject().addProperty("text", "mutated outside");
        assertNull(original.resource(INTRO));
        assertTrue(draft.resource(INTRO).getAsJsonObject().get("text").isJsonNull());
        draft.resource(INTRO).getAsJsonObject().remove("future_field");
        assertTrue(draft.resource(INTRO).getAsJsonObject().has("future_field"));
        ProjectStore store = new ProjectStore(root);
        Path folder = root.resolve("story");
        store.save(folder, draft, null);
        assertEquals(draft, store.open(folder).draft());
        assertEquals(raw.get("future_project_field"), draft.toJson().get("future_project_field"));
        assertEquals(raw.getAsJsonObject("resources").get("future_kind"), draft.resources().get("future_kind"));
        assertNotNull(draft.withoutResource(HERO).resource(INTRO));
        assertNotNull(draft.resource(HERO));
    }

    @Test
    void distinguishesReferenceTypesAndFieldsWithoutTreatingTextAsDependencies() {
        ProjectDraft draft = project().withResource(INTRO, json("""
                {"steps":[{"speaker":{"type":"set","id":"story:people/hero"}},
                          {"text":"story:chapter/next","speaker":{"type":"hide","id":"story:people/hero"}}],
                 "end":{"speaker":{"type":"set","id":"story:people/hero"},
                        "exit":{"type":"options","options":[
                          {"text":"A","target":{"type":"dialogue","dialogue":"story:chapter/next"}},
                          {"text":"story:chapter/next","command":"say story:chapter/next","target":{"type":"close"}}
                        ]}}}
                """));
        ResourceCatalog index = new ResourceCatalog(draft);
        assertEquals(List.of("steps[0].speaker.id", "end.speaker.id"), index.users(HERO).stream().map(ResourceCatalog.Use::field).toList());
        assertEquals(List.of("end.exit.options[0].target.dialogue"), index.users(NEXT).stream().map(ResourceCatalog.Use::field).toList());
        assertTrue(index.users(new ResourceKey(ResourceKind.SPEAKER, NEXT.path())).isEmpty());
    }

    @Test
    void directExitAndDefaultNamespaceMatchRuntimeSemanticsAndSelfReferencesDoNotBlockDeletion() {
        JsonElement body = json("{\"end\":{\"exit\":{\"type\":\"dialogue\",\"dialogue\":\"chapter/next\"}}}");
        assertTrue(new ResourceCatalog(project().withResource(INTRO, body)).users(NEXT).isEmpty());
        ResourceCatalog minecraft = new ResourceCatalog(project().withNamespace("minecraft").withResource(INTRO, body));
        assertEquals(1, minecraft.users(NEXT).size());
        ResourceCatalog self = new ResourceCatalog(project().withResource(NEXT,
                json("{\"end\":{\"exit\":{\"type\":\"dialogue\",\"dialogue\":\"story:chapter/next\"}}}")));
        assertEquals(1, self.users(NEXT).size());
        assertTrue(self.deletionBlockers(NEXT).isEmpty());
    }

    @Test
    void recognizesPresentationSceneVisualAndActionReferencesInIncompleteDrafts() {
        ProjectDraft draft = project().withResource(INTRO, json("""
                {"presentation":{"type":"reference","id":"story:layout"},
                 "steps":[null, {"actions":[{"action":{"type":"reference","id":"story:fade"}},null]}],
                 "end":null}
                """))
                .withResource(new ResourceKey(ResourceKind.PRESENTATION, "layout"), json("""
                {"theme":"story:theme","scene":"story:room","visual_objects":{"person":{"asset":"story:portrait"}}}
                """))
                .withResource(new ResourceKey(ResourceKind.SCENE, "room"), json("{\"visual_objects\":{\"person\":{\"asset\":\"story:portrait\"}}}"));
        ResourceCatalog index = new ResourceCatalog(draft);
        assertEquals(1, index.users(new ResourceKey(ResourceKind.PRESENTATION, "layout")).size());
        assertEquals(1, index.users(new ResourceKey(ResourceKind.ACTION, "fade")).size());
        assertEquals(1, index.users(new ResourceKey(ResourceKind.THEME, "theme")).size());
        assertEquals(1, index.users(new ResourceKey(ResourceKind.SCENE, "room")).size());
        assertEquals(2, index.users(new ResourceKey(ResourceKind.VISUAL_ASSET, "portrait")).size());
    }

    @Test
    void searchUsesFullIdsAndSpeakerNamesAndKeepsAncestorsWithoutLosingExpansionPreferences() {
        ProjectDraft draft = project().withResource(INTRO, json("{}"))
                .withResource(HERO, json("{\"name\":\"测试 Alice\"}"));
        ResourceCatalog index = new ResourceCatalog(draft);
        assertEquals(List.of(HERO), index.search("ALICE"));
        assertEquals(List.of(HERO), index.search("测试"));
        assertEquals(List.of(INTRO), index.search("STORY:CHAPTER"));
        Set<ResourceTree.Node> collapsed = Set.of(ResourceTree.Node.project(), ResourceTree.Node.category(ResourceKind.DIALOGUE));
        assertEquals(1, ResourceTree.rows(index, "", collapsed).size());
        List<ResourceTree.Node> nodes = ResourceTree.rows(index, "intro", collapsed).stream().map(ResourceTree.Row::node).toList();
        assertEquals(List.of(ResourceTree.Node.project(), ResourceTree.Node.category(ResourceKind.DIALOGUE),
                new ResourceTree.Node(ResourceTree.Type.FOLDER, ResourceKind.DIALOGUE, "chapter"), ResourceTree.Node.resource(INTRO)), nodes);
        assertEquals(1, ResourceTree.rows(index, "", collapsed).size());
    }

    @Test
    void malformedGroupsAndUnknownFieldsAreNotSilentlyOverwritten() throws Exception {
        JsonObject raw = project().toJson();
        raw.getAsJsonObject("resources").add("dialogues", json("[1,null]"));
        ProjectDraft draft = ProjectDraft.fromJson(raw);
        assertFalse(draft.hasResourceGroup(ResourceKind.DIALOGUE));
        assertThrows(IllegalStateException.class, () -> draft.withResource(INTRO, json("{}")));
        assertTrue(new ResourceCatalog(draft).keys().isEmpty());
        assertEquals(raw, draft.toJson());
    }

    @Test
    void acceptsLogicalFolderIdsButRejectsAbsoluteAndAmbiguousPaths() {
        assertTrue(ResourceKey.validPath("chapter_1/intro-part.2"));
        for (String path : List.of("", "/intro", "intro/", "chapter//intro", "../intro", "chapter/./intro", "C:/intro", "a\\b", "story:intro", "Intro", "中文")) {
            assertFalse(ResourceKey.validPath(path), path);
        }
    }
}
