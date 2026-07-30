package top.rookiestwo.maimai_dialogue.dialogue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;
import top.rookiestwo.maimai_dialogue.presentation.action.PresentationAction;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BundledDialogueResourcesTest {
    private static final List<String> DIALOGUES = List.of(
            "root",
            "public",
            "locked",
            "theme",
            "crt"
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

    @Test
    void bundledRootDemonstratesNearestVisualSampling() {
        DialogueDefinition root = decode(
                "assets/maimai_dialogue/dialogues/demo/root.json"
        );

        assertEquals(
                VisualSampling.NEAREST,
                root.presentation()
                        .visualObjects()
                        .get("demo_marker")
                        .sampling()
        );
    }

    @Test
    void bundledSpeakerDecodes() {
        var stream = BundledDialogueResourcesTest.class
                .getClassLoader()
                .getResourceAsStream(
                        "assets/maimai_dialogue/speakers/demo/guide.json"
                );
        assertNotNull(stream, "demo guide speaker");

        try (var reader = new InputStreamReader(
                stream,
                StandardCharsets.UTF_8
        )) {
            SpeakerDefinition definition = SpeakerDefinition.CODEC.parse(
                    JsonOps.INSTANCE,
                    JsonParser.parseReader(reader)
            ).getOrThrow(AssertionError::new);
            assertEquals("Guide", definition.name());
        } catch (java.io.IOException exception) {
            throw new AssertionError(
                    "Failed to read bundled demo speaker",
                    exception
            );
        }
    }

    @Test
    void bundledThemeAndPresentationActionDecode() {
        ThemeDefinition theme = decodeResource(
                "assets/maimai_dialogue/dialogue_themes/default.json",
                ThemeDefinition.CODEC
        );
        ThemeDefinition parchment = decodeResource(
                "assets/maimai_dialogue/dialogue_themes/"
                        + "demo/parchment.json",
                ThemeDefinition.CODEC
        );
        PresentationAction action = decodeResource(
                "assets/maimai_dialogue/presentation_actions/"
                        + "demo/marker_enter.json",
                PresentationAction.CODEC
        );

        assertEquals(1, theme.box().cornerRadiusDp());
        assertEquals(
                0xFF2B1A10,
                parchment.text().primary().argb()
        );
        assertEquals(4, theme.controls().scrollbarWidthDp());
        assertEquals(5, parchment.controls().scrollbarWidthDp());
        assertEquals(3, theme.spacing().optionsCollapsedLimit());
        assertEquals(6, theme.spacing().optionsExpandedLimit());
        assertEquals(700, action.durationMs());
    }

    private static DialogueDefinition decode(String path) {
        return decodeResource(path, DialogueDefinition.CODEC);
    }

    private static <T> T decodeResource(
            String path,
            com.mojang.serialization.Codec<T> codec
    ) {
        var stream = BundledDialogueResourcesTest.class
                .getClassLoader()
                .getResourceAsStream(path);
        assertNotNull(stream, path);

        try (var reader = new InputStreamReader(
                stream,
                StandardCharsets.UTF_8
        )) {
            return codec.parse(
                    JsonOps.INSTANCE,
                    JsonParser.parseReader(reader)
            ).getOrThrow(AssertionError::new);
        } catch (java.io.IOException exception) {
            throw new AssertionError("Failed to read " + path, exception);
        }
    }
}
