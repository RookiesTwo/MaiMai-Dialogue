package top.rookiestwo.maimai_dialogue.client.scene;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;
import top.rookiestwo.maimai_dialogue.presentation.action.ActionCall;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneRuntimeTest {
    @Test
    void playbackTokensCanBeNamespacedPerDialogueGeneration() {
        SceneRuntime root = new SceneRuntime(presentation(), 0.0F, 100L);
        SceneRuntime child = new SceneRuntime(presentation(), 0.0F, 200L);

        assertEquals(100L, root.prepare(List.of()).playback().token());
        assertEquals(200L, child.prepare(List.of()).playback().token());
    }

    @Test
    void precomputesFinalStateAndSamplesRelativeTracks() {
        SceneRuntime runtime = new SceneRuntime(presentation());
        ScenePreparation preparation = runtime.prepare(List.of(
                action("""
                        {
                          "target": "guide",
                          "action": {
                            "type": "inline",
                            "action": {
                              "duration_ms": 1000,
                              "x": [{"at": 1.0, "value": 0.2}],
                              "opacity": [{"at": 1.0, "value": -0.4}],
                              "variant": {"at": 0.5, "value": "alert"}
                            }
                          }
                        }
                        """)
        ));

        assertTrue(preparation.errors().isEmpty());
        assertEquals(1000, preparation.playback().blockingDurationMs());
        SceneObjectState halfway = preparation.playback()
                .stateAt(500)
                .find("guide")
                .orElseThrow();
        assertEquals(0.6F, halfway.x(), 0.0001F);
        assertEquals(0.6F, halfway.opacity(), 0.0001F);
        assertEquals("alert", halfway.variant());
        VariantTransition objectTransition = preparation.playback()
                .variantTransitionsAt(750)
                .get("guide");
        assertEquals("idle", objectTransition.fromVariant());
        assertEquals("alert", objectTransition.toVariant());
        assertEquals(0.5F, objectTransition.progress(), 0.0001F);
        float outgoingAlpha = objectTransition.outgoingAlpha(0.8F);
        float incomingAlpha = objectTransition.incomingAlpha(0.8F);
        assertEquals(0.4F, outgoingAlpha, 0.0001F);
        assertEquals(
                0.8F,
                outgoingAlpha + incomingAlpha * (1.0F - outgoingAlpha),
                0.0001F
        );

        SceneObjectState end = runtime.current()
                .find("guide")
                .orElseThrow();
        assertEquals(0.7F, end.x(), 0.0001F);
        assertEquals(0.4F, end.opacity(), 0.0001F);
    }

    @Test
    void rejectsConflictingWritesAndInvalidFinalState() {
        SceneRuntime runtime = new SceneRuntime(presentation());
        ActionCall move = action("""
                {
                  "target": "guide",
                  "action": {
                    "type": "inline",
                    "action": {
                      "x": [{"at": 1.0, "value": 0.1}]
                    }
                  }
                }
                """);
        ActionCall conflictingMove = action("""
                {
                  "target": "guide",
                  "action": {
                    "type": "inline",
                    "action": {
                      "x": [{"at": 1.0, "value": 0.2}]
                    }
                  }
                }
                """);
        ActionCall invalidOpacity = action("""
                {
                  "target": "guide",
                  "action": {
                    "type": "inline",
                    "action": {
                      "opacity": [{"at": 1.0, "value": 0.5}]
                    }
                  }
                }
                """);

        ScenePreparation preparation = runtime.prepare(List.of(
                move,
                conflictingMove,
                invalidOpacity
        ));

        assertFalse(preparation.errors().isEmpty());
        assertEquals(2, preparation.errors().size());
    }

    @Test
    void switchesPredeclaredBackgroundVariant() {
        SceneRuntime runtime = new SceneRuntime(presentation());
        ScenePreparation preparation = runtime.prepare(List.of(
                action("""
                        {
                          "target": "background",
                          "action": {
                            "type": "inline",
                            "action": {
                              "duration_ms": 400,
                              "variant": {
                                "at": 0.5,
                                "value": "night"
                              }
                            }
                          }
                        }
                        """)
        ));

        assertTrue(preparation.errors().isEmpty());
        assertEquals(
                "day",
                preparation.playback().stateAt(199)
                        .background().orElseThrow().variant()
        );
        assertEquals(
                "night",
                preparation.playback().stateAt(200)
                        .background().orElseThrow().variant()
        );
        assertTrue(
                preparation.playback().variantTransitionsAt(199).isEmpty()
        );
        VariantTransition halfway = preparation.playback()
                .variantTransitionsAt(300)
                .get("background");
        assertEquals("day", halfway.fromVariant());
        assertEquals("night", halfway.toVariant());
        assertEquals(0.5F, halfway.progress(), 0.0001F);
        assertTrue(
                preparation.playback().variantTransitionsAt(400).isEmpty()
        );
    }

    @Test
    void animatesDialogueOpacityThroughTheSameScenePlayback() {
        SceneRuntime runtime = new SceneRuntime(presentation(), 0.0F);
        ScenePreparation preparation = runtime.prepare(List.of(
                action("""
                        {
                          "target": "dialogue",
                          "action": {
                            "type": "inline",
                            "action": {
                              "duration_ms": 200,
                              "opacity": [
                                {"at": 1.0, "value": 1.0}
                              ]
                            }
                          }
                        }
                        """)
        ));

        assertTrue(preparation.errors().isEmpty());
        assertEquals(
                0.5F,
                preparation.playback().stateAt(100).dialogueOpacity(),
                0.0001F
        );
        assertEquals(
                0.5F,
                preparation.playback().dialogueTransitionProgressAt(100),
                0.0001F
        );
        assertEquals(1.0F, runtime.current().dialogueOpacity(), 0.0001F);
    }

    @Test
    void defaultFadeUsesTheSameActionPipelineAndRespectsOverrides() {
        ActionCall objectAction = action("""
                {
                  "target": "guide",
                  "action": {
                    "type": "inline",
                    "action": {
                      "x": [{"at": 1.0, "value": 0.1}]
                    }
                  }
                }
                """);
        List<ActionCall> withDefault =
                SceneTransitions.withDefaultFadeIn(List.of(objectAction));
        assertEquals(2, withDefault.size());
        assertEquals("dialogue", withDefault.getFirst().target());

        SceneRuntime runtime = new SceneRuntime(presentation(), 0.0F);
        ScenePreparation preparation = runtime.prepare(withDefault);
        assertTrue(preparation.errors().isEmpty());
        assertEquals(300, preparation.playback().blockingDurationMs());
        assertEquals(1.0F, runtime.current().dialogueOpacity(), 0.0001F);

        ActionCall customDialogueAction = action("""
                {
                  "target": "dialogue",
                  "action": {
                    "type": "inline",
                    "action": {
                      "duration_ms": 100,
                      "opacity": [{"at": 1.0, "value": 0.8}]
                    }
                  }
                }
                """);
        List<ActionCall> custom =
                SceneTransitions.withDefaultFadeIn(
                        List.of(customDialogueAction)
                );
        assertEquals(1, custom.size());
        assertEquals(customDialogueAction, custom.getFirst());
    }

    private static Presentation presentation() {
        return Presentation.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "theme": "maimai_dialogue:default",
                          "background": {
                            "variants": {
                              "day": "example:day.png",
                              "night": "example:night.png"
                            },
                            "initial_variant": "day"
                          },
                          "visual_objects": {
                            "guide": {
                              "variants": {
                                "idle": "example:guide.png",
                                "alert": "example:guide_alert.png"
                              },
                              "initial_variant": "idle",
                              "x": 0.5,
                              "opacity": 0.8
                            }
                          }
                        }
                        """)
        ).getOrThrow(AssertionError::new);
    }

    private static ActionCall action(String json) {
        return ActionCall.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(json)
        ).getOrThrow(AssertionError::new);
    }
}
