package top.rookiestwo.maimai_dialogue.presentation.action;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresentationActionCodecTest {
    @Test
    void decodesInlineActionCallWithTracks() {
        ActionCall call = ActionCall.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "target": "guide",
                          "delay_ms": 100,
                          "action": {
                            "type": "inline",
                            "action": {
                              "duration_ms": 500,
                              "easing": "ease_out",
                              "x": [
                                {"at": 0.5, "value": 0.1},
                                {"at": 1.0, "value": 0.2}
                              ],
                              "variant": {
                                "at": 0.75,
                                "value": "alert"
                              }
                            }
                          }
                        }
                        """)
        ).getOrThrow(AssertionError::new);

        var inline = assertInstanceOf(
                ActionDefinition.Inline.class,
                call.action()
        );
        assertEquals(600, call.delayMs() + inline.action().durationMs());
        assertEquals(
                0.2F,
                inline.action().x().orElseThrow().finalValue(),
                0.0001F
        );
        assertEquals(
                "alert",
                inline.action().variant().orElseThrow().variant()
        );
    }

    @Test
    void decodesReferenceAndRejectsUnorderedKeyframes() {
        ActionCall reference = ActionCall.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "target": "guide",
                          "action": {
                            "type": "reference",
                            "id": "example:guide/enter"
                          }
                        }
                        """)
        ).getOrThrow(AssertionError::new);
        assertInstanceOf(ActionDefinition.Reference.class, reference.action());

        assertTrue(PresentationAction.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "x": [
                            {"at": 0.8, "value": 0.1},
                            {"at": 0.4, "value": 0.2}
                          ]
                        }
                        """)
        ).error().isPresent());
    }
}
