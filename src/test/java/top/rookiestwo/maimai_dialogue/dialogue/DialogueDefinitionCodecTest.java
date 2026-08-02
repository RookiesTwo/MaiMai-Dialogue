package top.rookiestwo.maimai_dialogue.dialogue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import top.rookiestwo.maimai_dialogue.progress.ProgressNode;

import java.util.Set;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueDefinitionCodecTest {
    @Test
    void decodesConfirmedDialogueShape() {
        DialogueDefinition definition = decode("""
                {
                  "requires": "quest.skier.started && !quest.skier.finished",
                  "skip_summary": "## 摘要\n\n直接进入最终选择。",
                  "presentation": {
                    "theme": "example:default",
                    "visual_objects": {}
                  },
                  "steps": [
                    {
                      "speaker": {
                        "type": "set",
                        "id": "example:skier"
                      },
                      "text": "你好。"
                    },
                    {
                      "text": "继承 Speaker。",
                      "typewriter_interval_ms": 12
                    },
                    {
                      "speaker": {
                        "type": "hide"
                      }
                    }
                  ],
                  "end": {
                    "text": "接下来要做什么？",
                    "typewriter_interval_ms": 0,
                    "exit": {
                      "type": "options",
                      "options": [
                        {
                          "text": "继续交谈",
                          "icon": "dialogue",
                          "target": {
                            "type": "dialogue",
                            "dialogue": "example:skier/more"
                          }
                        },
                        {
                          "text": "返回",
                          "target": {
                            "type": "return"
                          }
                        }
                      ]
                    }
                  }
                }
                """);

        assertTrue(definition.requires().orElseThrow().evaluate(Set.of(
                new ProgressNode("quest.skier.started")
        )));
        assertEquals(
                "## 摘要\n\n直接进入最终选择。",
                definition.skipSummary().orElseThrow()
        );
        assertEquals(3, definition.steps().size());
        assertInstanceOf(SetSpeaker.class,
                definition.steps().getFirst().speaker().orElseThrow());
        assertTrue(definition.steps().get(1).speaker().isEmpty());
        assertInstanceOf(HideSpeaker.class,
                definition.steps().get(2).speaker().orElseThrow());
        assertEquals(
                DialogueStep.DEFAULT_TYPEWRITER_INTERVAL_MS,
                definition.steps().getFirst().typewriterIntervalMs()
        );
        assertTrue(
                definition.steps().getFirst().usesDefaultTypewriterInterval()
        );
        assertTrue(
                !definition.steps().get(1).usesDefaultTypewriterInterval()
        );
        assertEquals(12, definition.steps().get(1).typewriterIntervalMs());
        assertEquals(0, definition.end().typewriterIntervalMs());

        ChoiceExit exit = assertInstanceOf(ChoiceExit.class, definition.end().exit());
        assertEquals(2, exit.options().size());
        assertEquals(OptionIcon.DIALOGUE, exit.options().getFirst().icon());
        assertEquals(OptionIcon.NONE, exit.options().get(1).icon());
        assertInstanceOf(DialogueTarget.class, exit.options().getFirst().target());
        assertInstanceOf(ReturnTarget.class, exit.options().get(1).target());
    }

    @Test
    void appliesDefaultsForPublicDialogue() {
        DialogueDefinition definition = decode("""
                {
                  "presentation": {
                    "theme": "maimai_dialogue:default"
                  },
                  "end": {
                    "exit": {
                      "type": "return"
                    }
                  }
                }
                """);

        assertTrue(definition.requires().isEmpty());
        assertTrue(definition.skipSummary().isEmpty());
        assertTrue(definition.steps().isEmpty());
        assertTrue(definition.end().text().isEmpty());
        assertTrue(definition.end().speaker().isEmpty());
        assertEquals(
                DialogueStep.DEFAULT_TYPEWRITER_INTERVAL_MS,
                definition.end().typewriterIntervalMs()
        );
        assertTrue(definition.end().usesDefaultTypewriterInterval());
        assertInstanceOf(ReturnExit.class, definition.end().exit());
    }

    @Test
    void roundTripsThroughJsonOps() {
        DialogueDefinition original = decode("""
                {
                  "presentation": {
                    "theme": "maimai_dialogue:default"
                  },
                  "skip_summary": "**Skip** to the end.",
                  "steps": [
                    {
                      "text": ["Hello", "Welcome"],
                      "typewriter_interval_ms": 45
                    }
                  ],
                  "end": {
                    "exit": {
                      "type": "return"
                    }
                  }
                }
                """);

        var encoded = DialogueDefinition.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(AssertionError::new);
        DialogueDefinition decoded = DialogueDefinition.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(AssertionError::new);

        assertEquals(original, decoded);
        assertEquals(
                "**Skip** to the end.",
                decoded.skipSummary().orElseThrow()
        );
        assertEquals(45, decoded.steps().getFirst().typewriterIntervalMs());
        assertTrue(decoded.end().usesDefaultTypewriterInterval());
        assertTrue(!decoded.steps().getFirst().usesDefaultTypewriterInterval());
    }

    @Test
    void preservesExplicitThirtyAgainstClientDefault() {
        DialogueDefinition definition = decode("""
                {
                  "presentation": {
                    "theme": "maimai_dialogue:default"
                  },
                  "steps": [
                    {"text": "Default"},
                    {"text": "Explicit", "typewriter_interval_ms": 30}
                  ],
                  "end": {
                    "exit": {"type": "return"}
                  }
                }
                """);

        DialogueStep inherited = definition.steps().getFirst();
        DialogueStep explicit = definition.steps().get(1);
        assertEquals(75, inherited.resolveTypewriterIntervalMs(75));
        assertEquals(30, explicit.resolveTypewriterIntervalMs(75));
        assertTrue(inherited.usesDefaultTypewriterInterval());
        assertTrue(!explicit.usesDefaultTypewriterInterval());
    }

    @Test
    void decodesFixedAndRandomDialogueText() {
        DialogueDefinition definition = decode("""
                {
                  "presentation": {
                    "theme": "maimai_dialogue:default"
                  },
                  "steps": [
                    {
                      "text": "Fixed"
                    },
                    {
                      "text": ["First", "Second", "First"]
                    }
                  ],
                  "end": {
                    "text": ["Done", "Finished"],
                    "exit": {
                      "type": "return"
                    }
                  }
                }
                """);

        assertEquals(
                Set.of("Fixed"),
                Set.copyOf(definition.steps().getFirst().text()
                        .orElseThrow().variants())
        );
        assertEquals(
                java.util.List.of("First", "Second", "First"),
                definition.steps().get(1).text().orElseThrow().variants()
        );
        assertEquals(
                java.util.List.of("Done", "Finished"),
                definition.end().text().orElseThrow().variants()
        );
    }

    @Test
    void rejectsInvalidDialogueTextArrays() {
        for (String text : java.util.List.of(
                "[]",
                "[\"Valid\", 1]",
                "null"
        )) {
            assertTrue(DialogueDefinition.CODEC.parse(
                    JsonOps.INSTANCE,
                    JsonParser.parseString("""
                            {
                              "presentation": {
                                "theme": "maimai_dialogue:default"
                              },
                              "steps": [{"text": %s}],
                              "end": {
                                "exit": {"type": "return"}
                              }
                            }
                            """.formatted(text))
            ).error().isPresent());
        }
    }

    @Test
    void rejectsInvalidTypewriterIntervals() {
        for (int interval : java.util.List.of(-1, 1_001)) {
            assertTrue(DialogueDefinition.CODEC.parse(
                    JsonOps.INSTANCE,
                    JsonParser.parseString("""
                            {
                              "presentation": {
                                "theme": "maimai_dialogue:default"
                              },
                              "steps": [{
                                "text": "Invalid",
                                "typewriter_interval_ms": %d
                              }],
                              "end": {
                                "exit": {"type": "return"}
                              }
                            }
                            """.formatted(interval))
            ).error().isPresent());
        }
    }

    @Test
    void rejectsInvalidSkipSummaries() {
        for (String summary : List.of(
                "\"\"",
                "\"   \"",
                "null",
                "1",
                "[\"Summary\"]"
        )) {
            assertTrue(DialogueDefinition.CODEC.parse(
                    JsonOps.INSTANCE,
                    JsonParser.parseString("""
                            {
                              "skip_summary": %s,
                              "presentation": {
                                "theme": "maimai_dialogue:default"
                              },
                              "end": {
                                "exit": {"type": "return"}
                              }
                            }
                            """.formatted(summary))
            ).error().isPresent());
        }
    }

    @Test
    void legacyConstructorDefaultsSkipSummary() {
        DialogueDefinition definition = new DialogueDefinition(
                Optional.empty(),
                new Presentation(
                        net.minecraft.resources.ResourceLocation
                                .fromNamespaceAndPath(
                                        "maimai_dialogue",
                                        "default"
                                )
                ),
                List.of(),
                new DialogueEnd(
                        Optional.empty(),
                        Optional.empty(),
                        ReturnExit.INSTANCE
                )
        );

        assertTrue(definition.skipSummary().isEmpty());
    }

    @Test
    void rejectsBlankOptionsAndUnknownTypes() {
        assertTrue(DialogueDefinition.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "presentation": {"theme": "maimai_dialogue:default"},
                          "end": {
                            "exit": {
                              "type": "options",
                              "options": []
                            }
                          }
                        }
                        """)
        ).error().isPresent());

        assertTrue(DialogueDefinition.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "presentation": {"theme": "maimai_dialogue:default"},
                          "end": {
                            "exit": {
                              "type": "close"
                            }
                          }
                        }
                        """)
        ).error().isPresent());
    }

    private static DialogueDefinition decode(String json) {
        return DialogueDefinition.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(json)
        ).getOrThrow(AssertionError::new);
    }
}
