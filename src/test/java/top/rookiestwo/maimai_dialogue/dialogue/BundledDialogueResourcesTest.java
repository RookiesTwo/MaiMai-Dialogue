package top.rookiestwo.maimai_dialogue.dialogue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BundledDialogueResourcesTest {
    private static final List<String> DIALOGUES = List.of(
            "root",
            "public",
            "locked"
    );

    @Test
    void bundledClientAndServerDialoguesDecodeAndMatch() {
        for (String dialogue : DIALOGUES) {
            DialogueDefinition client = decode(
                    "assets/maimai_dialogue/dialogues/demo/"
                            + dialogue + ".json"
            );
            DialogueDefinition server = decode(
                    "data/maimai_dialogue/dialogues/demo/"
                            + dialogue + ".json"
            );

            assertEquals(server, client, dialogue);
        }
    }

    private static DialogueDefinition decode(String path) {
        var stream = BundledDialogueResourcesTest.class
                .getClassLoader()
                .getResourceAsStream(path);
        assertNotNull(stream, path);

        try (var reader = new InputStreamReader(
                stream,
                StandardCharsets.UTF_8
        )) {
            return DialogueDefinition.CODEC.parse(
                    JsonOps.INSTANCE,
                    JsonParser.parseReader(reader)
            ).getOrThrow(AssertionError::new);
        } catch (java.io.IOException exception) {
            throw new AssertionError("Failed to read " + path, exception);
        }
    }
}
