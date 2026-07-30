package top.rookiestwo.maimai_dialogue.client.resource;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.resource.DialogueSnapshot;
import top.rookiestwo.maimai_dialogue.presentation.action.PresentationAction;
import top.rookiestwo.maimai_dialogue.presentation.action.resource.ActionSnapshot;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;
import top.rookiestwo.maimai_dialogue.speaker.resource.SpeakerSnapshot;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import top.rookiestwo.maimai_dialogue.theme.resource.ThemeSnapshot;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientResourceValidatorTest {
    private static final ResourceLocation DIALOGUE_ID =
            ResourceLocation.parse("test:root");

    @Test
    void acceptsCompleteReferences() {
        DialogueDefinition dialogue = decode("""
                {
                  "presentation": {"theme": "test:theme"},
                  "end": {
                    "speaker": {"type": "set", "id": "test:speaker"},
                    "text": "Ready.",
                    "actions": [{
                      "target": "dialogue",
                      "action": {
                        "type": "inline",
                        "action": {
                          "opacity": [{"at": 1.0, "value": 1.0}]
                        }
                      }
                    }],
                    "exit": {"type": "return"}
                  }
                }
                """);

        List<String> errors = ClientResourceValidator.validate(
                new DialogueSnapshot(Map.of(DIALOGUE_ID, dialogue)),
                new SpeakerSnapshot(Map.of(
                        ResourceLocation.parse("test:speaker"),
                        new SpeakerDefinition("Speaker")
                )),
                new ThemeSnapshot(Map.of(
                        ResourceLocation.parse("test:theme"),
                        ThemeDefinition.DEFAULT
                )),
                ActionSnapshot.EMPTY,
                ignored -> true
        );

        assertTrue(errors.isEmpty());
    }

    @Test
    void aggregatesCrossResourceErrors() {
        DialogueDefinition dialogue = decode("""
                {
                  "presentation": {
                    "theme": "test:missing_theme",
                    "background": {
                      "variants": {"default": "test:bg.png"}
                    },
                    "visual_objects": {
                      "actor": {
                        "variants": {"default": "test:actor.png"},
                        "initial_variant": "default"
                      }
                    }
                  },
                  "steps": [{
                    "speaker": {
                      "type": "set",
                      "id": "test:missing_speaker"
                    },
                    "actions": [{
                      "target": "actor",
                      "action": {
                        "type": "reference",
                        "id": "test:missing_action"
                      }
                    }]
                  }],
                  "end": {
                    "actions": [
                      {
                        "target": "ghost",
                        "action": {
                          "type": "inline",
                          "action": {
                            "x": [{"at": 1.0, "value": 0.2}]
                          }
                        }
                      },
                      {
                        "target": "actor",
                        "action": {
                          "type": "inline",
                          "action": {
                            "variant": {
                              "at": 1.0,
                              "value": "missing_variant"
                            }
                          }
                        }
                      }
                    ],
                    "exit": {
                      "type": "options",
                      "options": [{
                        "text": "Missing",
                        "target": {
                          "type": "dialogue",
                          "dialogue": "test:missing_dialogue"
                        }
                      }]
                    }
                  }
                }
                """);

        List<String> errors = ClientResourceValidator.validate(
                new DialogueSnapshot(Map.of(DIALOGUE_ID, dialogue)),
                SpeakerSnapshot.EMPTY,
                ThemeSnapshot.EMPTY,
                ActionSnapshot.EMPTY,
                ignored -> false
        );

        assertEquals(8, errors.size());
        assertContains(errors, "missing Theme");
        assertContains(errors, "missing image test:bg.png");
        assertContains(errors, "missing image test:actor.png");
        assertContains(errors, "missing Speaker");
        assertContains(errors, "missing PresentationAction");
        assertContains(errors, "undeclared VisualObject ghost");
        assertContains(errors, "selects missing variant missing_variant");
        assertContains(errors, "targets missing Dialogue");
    }

    @Test
    void bundledResourcesPassGlobalReferenceValidation() {
        Map<ResourceLocation, DialogueDefinition> dialogues =
                new LinkedHashMap<>();
        for (String path : List.of(
                "root",
                "public",
                "locked",
                "theme",
                "crt"
        )) {
            dialogues.put(
                    ResourceLocation.parse("maimai_dialogue:demo/" + path),
                    decodeResource(
                            "assets/maimai_dialogue/dialogues/demo/"
                                    + path + ".json",
                            DialogueDefinition.CODEC
                    )
            );
        }
        SpeakerSnapshot speakers = new SpeakerSnapshot(Map.of(
                ResourceLocation.parse("maimai_dialogue:demo/guide"),
                decodeResource(
                        "assets/maimai_dialogue/speakers/demo/guide.json",
                        SpeakerDefinition.CODEC
                )
        ));
        ThemeSnapshot themes = new ThemeSnapshot(Map.of(
                ResourceLocation.parse("maimai_dialogue:default"),
                decodeResource(
                        "assets/maimai_dialogue/dialogue_themes/default.json",
                        ThemeDefinition.CODEC
                ),
                ResourceLocation.parse("maimai_dialogue:demo/parchment"),
                decodeResource(
                        "assets/maimai_dialogue/dialogue_themes/"
                                + "demo/parchment.json",
                        ThemeDefinition.CODEC
                )
        ));
        ActionSnapshot actions = new ActionSnapshot(Map.of(
                ResourceLocation.parse(
                        "maimai_dialogue:demo/marker_enter"
                ),
                decodeResource(
                        "assets/maimai_dialogue/presentation_actions/"
                                + "demo/marker_enter.json",
                        PresentationAction.CODEC
                )
        ));

        List<String> errors = ClientResourceValidator.validate(
                new DialogueSnapshot(dialogues),
                speakers,
                themes,
                actions,
                ignored -> true
        );

        assertTrue(
                errors.isEmpty(),
                () -> "Bundled resource reference errors: " + errors
        );
    }

    private static void assertContains(
            List<String> errors,
            String fragment
    ) {
        assertTrue(
                errors.stream().anyMatch(error -> error.contains(fragment)),
                () -> "Missing error containing '" + fragment + "': "
                        + errors
        );
    }

    private static DialogueDefinition decode(String json) {
        return DialogueDefinition.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(json)
        ).getOrThrow(AssertionError::new);
    }

    private static <T> T decodeResource(
            String path,
            com.mojang.serialization.Codec<T> codec
    ) {
        var stream = ClientResourceValidatorTest.class
                .getClassLoader()
                .getResourceAsStream(path);
        if (stream == null) {
            throw new AssertionError("Missing resource " + path);
        }
        try (var reader = new InputStreamReader(
                stream,
                StandardCharsets.UTF_8
        )) {
            return codec.parse(
                    JsonOps.INSTANCE,
                    JsonParser.parseReader(reader)
            ).getOrThrow(AssertionError::new);
        } catch (java.io.IOException exception) {
            throw new AssertionError(
                    "Failed to read resource " + path,
                    exception
            );
        }
    }
}
