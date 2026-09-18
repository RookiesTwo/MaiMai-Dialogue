package top.rookiestwo.maimai_dialogue_editor.project;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectStoreTest {
    @TempDir Path temporary;
    private Path root;
    private Path directory;
    private ProjectStore store;

    @BeforeEach
    void setUp() throws IOException {
        root = temporary.resolve("projects");
        directory = root.resolve("example");
        Files.createDirectories(directory);
        store = new ProjectStore(root);
    }

    @Test
    void roundTripsIncompleteDraftsWithoutResolvingOrDroppingFields() throws Exception {
        JsonObject json = JsonParser.parseString("""
                {"format_version":1,"name":"未完成的工程","namespace":"",
                 "resources":{"dialogues":{"intro":{"text":null,"speaker":"","future_field":[1,2]}}},
                 "future_metadata":{"enabled":true}}
                """).getAsJsonObject();
        ProjectDraft draft = ProjectDraft.fromJson(json);
        String token = store.save(directory, draft, null);
        ProjectStore.Loaded loaded = store.open(directory);
        assertEquals(draft, loaded.draft());
        assertEquals(json, loaded.draft().toJson());
        assertEquals(token, loaded.fingerprint());
        assertFalse(loaded.draft().hasValidMetadata());
    }

    @Test
    void replacesOnlyTheVersionPreviouslyReadAndLeavesNoTemporaryFiles() throws Exception {
        Path nested = root.resolve("another_project");
        ProjectDraft original = ProjectDraft.create("First", "example");
        String token = store.save(nested, original, null);
        ProjectDraft changed = original.withName("Second");
        store.save(nested, changed, token);
        assertEquals(changed, store.open(nested).draft());
        try (var paths = Files.list(nested)) {
            assertEquals(1, paths.count());
        }
    }

    @Test
    void newSaveNeverOverwritesAnExistingFile() throws Exception {
        Path target = directory.resolve(ProjectStore.FILE_NAME);
        Files.writeString(target, "unrelated content");
        ProjectException error = assertThrows(ProjectException.class,
                () -> store.save(directory, ProjectDraft.create("", ""), null));
        assertEquals("already_exists", error.reason());
        assertEquals("unrelated content", Files.readString(target));
    }

    @Test
    void refusesExternalChangesAndDeletion() throws Exception {
        ProjectDraft draft = ProjectDraft.create("Original", "example");
        String token = store.save(directory, draft, null);
        Path target = directory.resolve(ProjectStore.FILE_NAME);
        Files.writeString(target, "externally edited");
        ProjectException changed = assertThrows(ProjectException.class,
                () -> store.save(directory, draft.withName("Mine"), token));
        assertEquals("external_change", changed.reason());
        assertEquals("externally edited", Files.readString(target));
        Files.delete(target);
        assertEquals("external_change", assertThrows(ProjectException.class,
                () -> store.save(directory, draft, token)).reason());
        assertFalse(Files.exists(target));
    }

    @Test
    void rejectsMalformedFilesAndUnsupportedVersions() throws Exception {
        for (String text : new String[]{"{", "[]", "null", "{}", "{unquoted:1}", "{} {}",
                "{\"format_version\":1,\"name\":123,\"namespace\":\"demo\",\"resources\":{}}"}) {
            Files.writeString(directory.resolve(ProjectStore.FILE_NAME), text);
            assertEquals("invalid_format", assertThrows(ProjectException.class, () -> store.open(directory)).reason());
        }
        for (String version : new String[]{"2", "1.5", "99999999999999999999"}) {
            JsonObject json = ProjectDraft.create("", "").toJson();
            json.add("format_version", JsonParser.parseString(version));
            Files.writeString(directory.resolve(ProjectStore.FILE_NAME), json.toString());
            assertEquals("unsupported_version", assertThrows(ProjectException.class,
                    () -> store.open(directory)).reason());
        }
    }

    @Test
    void errorsOnAFileUsedAsDirectoryWithoutChangingIt() throws Exception {
        Path notDirectory = root.resolve("file");
        Files.writeString(notDirectory, "keep");
        assertEquals("not_directory", assertThrows(ProjectException.class,
                () -> store.save(notDirectory, ProjectDraft.create("", ""), null)).reason());
        assertThrows(IOException.class, () -> store.save(notDirectory, ProjectDraft.create("", ""), null));
        assertEquals("keep", Files.readString(notDirectory));
    }

    @Test
    void draftDefensivelyCopiesSourceAndReturnedJson() throws Exception {
        JsonObject json = ProjectDraft.create("Original", "example").toJson();
        ProjectDraft draft = ProjectDraft.fromJson(json);
        json.addProperty("name", "Mutated outside");
        draft.toJson().addProperty("name", "Also mutated outside");
        assertEquals("Original", draft.name());
        assertEquals("New", draft.withName("New").name());
        assertEquals("Original", draft.name());
    }

    @Test
    void allocatesUniqueImmediateChildrenWithoutCreatingOrOverwritingFolders() throws Exception {
        Files.createDirectories(root.resolve("story"));
        Files.writeString(root.resolve("story_2"), "reserved");
        Path assigned = store.allocateDirectory("Story");
        assertEquals(root.resolve("story_3"), assigned);
        assertFalse(Files.exists(assigned));
        assertEquals(root, store.allocateDirectory("../../outside").getParent());
        assertEquals("reserved", Files.readString(root.resolve("story_2")));
    }

    @Test
    void rejectsOutsideRootAndNestedTargetsBeforeReadingOrWriting() throws Exception {
        Path outside = Files.createDirectories(temporary.resolve("external"));
        Path file = outside.resolve(ProjectStore.FILE_NAME);
        Files.writeString(file, "keep outside content");
        for (Path forbidden : List.of(outside, root.resolve("../external"), root,
                root.resolve("parent/nested"))) {
            assertEquals("outside_projects", assertThrows(ProjectException.class,
                    () -> store.open(forbidden)).reason());
            assertEquals("outside_projects", assertThrows(ProjectException.class,
                    () -> store.save(forbidden, ProjectDraft.create("", ""), null)).reason());
        }
        assertEquals("keep outside content", Files.readString(file));
        assertFalse(Files.exists(root.resolve("parent")));
    }

    @Test
    void scansOnlyProjectsAndSortsByFileModificationTimeDespiteBrokenProject() throws Exception {
        Path older = root.resolve("older");
        Path newer = root.resolve("newer");
        String oldToken = store.save(older, ProjectDraft.create("Old", "old"), null);
        store.save(newer, ProjectDraft.create("New", "new"), null);
        Path broken = Files.createDirectories(root.resolve("broken"));
        Files.writeString(broken.resolve(ProjectStore.FILE_NAME), "{broken");
        Path nested = root.resolve("container");
        new ProjectStore(nested).save(nested.resolve("hidden"), ProjectDraft.create("Hidden", "hidden"), null);
        Files.writeString(root.resolve("unrelated.txt"), "ignore");
        Files.setLastModifiedTime(older.resolve(ProjectStore.FILE_NAME), FileTime.fromMillis(1000));
        Files.setLastModifiedTime(broken.resolve(ProjectStore.FILE_NAME), FileTime.fromMillis(1500));
        Files.setLastModifiedTime(newer.resolve(ProjectStore.FILE_NAME), FileTime.fromMillis(2000));
        List<ProjectStore.Entry> projects = store.listProjects();
        assertEquals(List.of("New", "broken", "Old"), projects.stream().map(ProjectStore.Entry::name).toList());
        assertEquals(List.of(2000L, 1500L, 1000L), projects.stream().map(ProjectStore.Entry::modifiedMillis).toList());
        assertEquals("invalid_format", projects.get(1).errorReason());
        assertFalse(projects.get(1).canOpen());
        assertTrue(projects.getFirst().canOpen());
        store.save(older, ProjectDraft.create("Just saved", "old"), oldToken);
        assertEquals("Just saved", store.listProjects().getFirst().name());
    }

    @Test
    void missingRootIsEmptyButRootBlockedByAFileIsAnError() throws Exception {
        Path missing = temporary.resolve("missing");
        assertTrue(new ProjectStore(missing).listProjects().isEmpty());
        assertFalse(Files.exists(missing));
        Path blocked = temporary.resolve("blocked");
        Files.writeString(blocked, "keep");
        assertEquals("not_directory", assertThrows(ProjectException.class,
                () -> new ProjectStore(blocked).listProjects()).reason());
        assertEquals("keep", Files.readString(blocked));
    }

    @Test
    void equalModificationTimesUseStableFolderOrderAndKeepDuplicateNames() throws Exception {
        for (String folder : List.of("b", "a")) {
            Path path = root.resolve(folder);
            store.save(path, ProjectDraft.create("Same name", folder), null);
            Files.setLastModifiedTime(path.resolve(ProjectStore.FILE_NAME), FileTime.fromMillis(5000));
        }
        assertEquals(List.of("a", "b"), store.listProjects().stream().map(ProjectStore.Entry::namespace).toList());
    }
}
