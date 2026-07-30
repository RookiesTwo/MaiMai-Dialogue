package top.rookiestwo.maimai_dialogue.dialogue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresentationCodecTest {
    @Test
    void decodesCompleteStaticPresentation() {
        Presentation presentation = decode("""
                {
                  "theme": "maimai_dialogue:default",
                  "background": {
                    "variants": {
                      "day": "maimai_dialogue:demo/background.png",
                      "night": "maimai_dialogue:demo/background_night.png"
                    },
                    "initial_variant": "day",
                    "fit": "cover",
                    "opacity": 0.9
                  },
                  "dialogue_box": {
                    "x": 0.5,
                    "y": 0.98,
                    "width": 0.9,
                    "max_height": 0.45,
                    "anchor": "bottom_center"
                  },
                  "visual_objects": {
                    "guide": {
                      "variants": {
                        "idle": "maimai_dialogue:demo/guide.png",
                        "alert": "maimai_dialogue:demo/guide_alert.png"
                      },
                      "initial_variant": "idle",
                      "x": 0.25,
                      "y": 0.92,
                      "anchor": "bottom_center",
                      "scale": 1.2,
                      "sampling": "nearest",
                      "opacity": 0.85,
                      "visible": true,
                      "z_index": 10
                    }
                  },
                  "filter": {
                    "type": "color_adjust",
                    "brightness": -0.05,
                    "contrast": 1.1,
                    "saturation": 0.7,
                    "tint": "#FFFFD8B0"
                  }
                }
                """);

        assertEquals(1, presentation.visualObjects().size());
        assertEquals(
                "maimai_dialogue:demo/background.png",
                presentation.background()
                        .orElseThrow()
                        .initialImage()
                        .toString()
        );
        assertEquals(
                "maimai_dialogue:demo/guide.png",
                presentation.visualObjects()
                        .get("guide")
                        .initialImage()
                        .toString()
        );
        assertEquals(
                VisualSampling.NEAREST,
                presentation.visualObjects().get("guide").sampling()
        );
        assertInstanceOf(
                ColorAdjustFilter.class,
                presentation.filter().orElseThrow()
        );
    }

    @Test
    void decodesCrtFilterDefaults() {
        Presentation presentation = decode("""
                {
                  "theme": "maimai_dialogue:default",
                  "filter": {
                    "type": "crt"
                  }
                }
                """);

        CrtFilter filter = assertInstanceOf(
                CrtFilter.class,
                presentation.filter().orElseThrow()
        );
        assertEquals(0.22F, filter.scanlineStrength());
        assertEquals(1.0F, filter.chromaticAberration());
        assertTrue(filter.animated());
        assertFalse(new CrtFilter(
                0.1F,
                0.2F,
                0.1F,
                1.0F,
                0.2F,
                0.0F,
                0.0F,
                0.2F
        ).animated());
    }

    @Test
    void defaultsVisualObjectSamplingToLinear() {
        Presentation presentation = decode("""
                {
                  "theme": "maimai_dialogue:default",
                  "visual_objects": {
                    "guide": {
                      "variants": {
                        "idle": "maimai_dialogue:demo/guide.png"
                      },
                      "initial_variant": "idle"
                    }
                  }
                }
                """);

        assertEquals(
                VisualSampling.LINEAR,
                presentation.visualObjects().get("guide").sampling()
        );
    }

    @Test
    void rejectsMissingInitialVariantAndInvalidObjectId() {
        var missingVariant = Presentation.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "theme": "maimai_dialogue:default",
                          "visual_objects": {
                            "guide": {
                              "variants": {
                                "idle": "maimai_dialogue:demo/guide.png"
                              },
                              "initial_variant": "missing"
                            }
                          }
                        }
                        """)
        );
        var invalidObjectId = Presentation.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "theme": "maimai_dialogue:default",
                          "visual_objects": {
                            "Guide Portrait": {
                              "variants": {
                                "idle": "maimai_dialogue:demo/guide.png"
                              },
                              "initial_variant": "idle"
                            }
                          }
                        }
                        """)
        );

        assertTrue(missingVariant.error().isPresent());
        assertTrue(invalidObjectId.error().isPresent());
    }

    @Test
    void rejectsUnknownVisualObjectSampling() {
        var result = Presentation.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "theme": "maimai_dialogue:default",
                          "visual_objects": {
                            "guide": {
                              "variants": {
                                "idle": "maimai_dialogue:demo/guide.png"
                              },
                              "initial_variant": "idle",
                              "sampling": "pixelated"
                            }
                          }
                        }
                        """)
        );

        assertTrue(result.error().isPresent());
    }

    private static Presentation decode(String json) {
        return Presentation.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(json)
        ).getOrThrow(AssertionError::new);
    }
}
