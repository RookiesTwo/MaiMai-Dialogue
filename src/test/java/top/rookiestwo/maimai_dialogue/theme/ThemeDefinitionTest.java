package top.rookiestwo.maimai_dialogue.theme;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeDefinitionTest {
    @Test
    void decodesNestedThemeAndAppliesDefaults() {
        ThemeDefinition theme = ThemeDefinition.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "box": {
                            "background": "#C0102030",
                            "corner_radius": 0
                          },
                          "option": {
                            "horizontal_padding": 16
                          },
                          "controls": {
                            "icon": "#FF45A0FF",
                            "scrollbar_width": 6
                          }
                        }
                        """)
        ).getOrThrow(AssertionError::new);

        assertEquals(0xC0102030, theme.box().background().argb());
        assertEquals(0, theme.box().cornerRadiusDp());
        assertEquals(16, theme.option().horizontalPaddingDp());
        assertEquals(0xFF45A0FF, theme.controls().icon().argb());
        assertEquals(6, theme.controls().scrollbarWidthDp());
        assertEquals(
                ThemeDefinition.DEFAULT.text(),
                theme.text()
        );
    }

    @Test
    void rejectsOutOfRangeMetrics() {
        assertTrue(ThemeDefinition.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "spacing": {
                            "options_padding": 100
                          }
                        }
                        """)
        ).error().isPresent());
        assertTrue(ThemeDefinition.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "controls": {
                            "scrollbar_width": 0
                          }
                        }
                        """)
        ).error().isPresent());
    }
}
