package top.rookiestwo.maimai_dialogue.dialogue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import top.rookiestwo.maimai_dialogue.progress.ProgressNode;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueDefinitionCodecTest {
    @Test
    void decodesConfirmedDialogueShape() {
        DialogueDefinition definition = decode("""
                {
                  "requires": "quest.skier.started && !quest.skier.finished",
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
                      "text": "继承 Speaker。"
                    },
                    {
                      "speaker": {
                        "type": "hide"
                      }
                    }
                  ],
                  "end": {
                    "text": "接下来要做什么？",
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
        assertEquals(3, definition.steps().size());
        assertInstanceOf(SetSpeaker.class,
                definition.steps().getFirst().speaker().orElseThrow());
        assertTrue(definition.steps().get(1).speaker().isEmpty());
        assertInstanceOf(HideSpeaker.class,
                definition.steps().get(2).speaker().orElseThrow());

        OptionsExit exit = assertInstanceOf(OptionsExit.class, definition.end().exit());
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
        assertTrue(definition.steps().isEmpty());
        assertTrue(definition.end().text().isEmpty());
        assertTrue(definition.end().speaker().isEmpty());
        assertInstanceOf(ReturnExit.class, definition.end().exit());
    }

    @Test
    void roundTripsThroughJsonOps() {
        DialogueDefinition original = decode("""
                {
                  "presentation": {
                    "theme": "maimai_dialogue:default"
                  },
                  "steps": [
                    {
                      "text": "Hello"
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
