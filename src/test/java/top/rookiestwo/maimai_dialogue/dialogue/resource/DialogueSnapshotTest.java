package top.rookiestwo.maimai_dialogue.dialogue.resource;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.EndStep;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;
import top.rookiestwo.maimai_dialogue.dialogue.ReturnExit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueSnapshotTest {
    @Test
    void derivesDialogueIdFromResourcePath() {
        ResourceLocation dialogueId = DialogueResourceLoader.toDialogueId(
                ResourceLocation.parse("example:dialogues/skier/job_intro.json")
        );

        assertEquals(
                ResourceLocation.parse("example:skier/job_intro"),
                dialogueId
        );
    }

    @Test
    void rejectsNonDialogueResourcePaths() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DialogueResourceLoader.toDialogueId(
                        ResourceLocation.parse("example:speakers/skier.json")
                )
        );
    }

    @Test
    void snapshotDefensivelyCopiesDefinitions() {
        ResourceLocation id = ResourceLocation.parse("example:intro");
        Map<ResourceLocation, DialogueDefinition> mutable = new HashMap<>();
        mutable.put(id, publicReturnDialogue());

        DialogueSnapshot snapshot = new DialogueSnapshot(mutable);
        mutable.clear();

        assertTrue(snapshot.contains(id));
        assertFalse(snapshot.isEmpty());
        assertEquals(1, snapshot.size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.definitions().clear()
        );
    }

    private static DialogueDefinition publicReturnDialogue() {
        return new DialogueDefinition(
                Optional.empty(),
                new Presentation(ResourceLocation.parse("maimai_dialogue:default")),
                List.of(),
                new EndStep(
                        Optional.empty(),
                        Optional.empty(),
                        ReturnExit.INSTANCE
                )
        );
    }
}
