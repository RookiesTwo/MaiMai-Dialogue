package top.rookiestwo.maimai_dialogue.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;

class ClientPreferencesTest {
    @Test
    void defaultsMatchBothControlKeysAndConfiguredActions() {
        ClientPreferences preferences = ClientPreferences.defaults();

        assertTrue(preferences.matches(
                ClientControlAction.FAST_FORWARD,
                GLFW_KEY_LEFT_CONTROL
        ));
        assertTrue(preferences.matches(
                ClientControlAction.FAST_FORWARD,
                GLFW_KEY_RIGHT_CONTROL
        ));
        assertTrue(preferences.matches(
                ClientControlAction.ADVANCE,
                GLFW_KEY_SPACE
        ));
        assertFalse(preferences.matches(
                ClientControlAction.SKIP,
                GLFW_KEY_SPACE
        ));
        assertTrue(preferences.conflicts().isEmpty());
    }

    @Test
    void disablesEveryActionSharingAConfiguredKey() {
        DialogueKey shared = new DialogueKey("key.keyboard.h");
        ClientPreferences preferences = ClientPreferences.create(
                4.0,
                30,
                600,
                "",
                1.0,
                DialogueKey.CONTROL,
                shared,
                DialogueKey.UNBOUND,
                shared
        );

        assertEquals(
                java.util.Set.of(
                        ClientControlAction.ADVANCE,
                        ClientControlAction.HISTORY
                ),
                preferences.conflicts()
        );
        assertFalse(preferences.matches(
                ClientControlAction.ADVANCE,
                org.lwjgl.glfw.GLFW.GLFW_KEY_H
        ));
        assertFalse(preferences.matches(
                ClientControlAction.HISTORY,
                org.lwjgl.glfw.GLFW.GLFW_KEY_H
        ));
    }

    @Test
    void acceptsPreciseMultiplierAndRejectsOutOfRangeValues() {
        ClientPreferences precise = ClientPreferences.create(
                7.37,
                30,
                600,
                "",
                1.0,
                DialogueKey.CONTROL,
                ClientPreferences.DEFAULT_ADVANCE_KEY,
                DialogueKey.UNBOUND,
                ClientPreferences.DEFAULT_HISTORY_KEY
        );

        assertEquals(7.37, precise.fastForwardMultiplier());
        assertThrows(IllegalArgumentException.class, () ->
                ClientPreferences.create(
                        32.01,
                        30,
                        600,
                        "",
                        1.0,
                        DialogueKey.CONTROL,
                        ClientPreferences.DEFAULT_ADVANCE_KEY,
                        DialogueKey.UNBOUND,
                        ClientPreferences.DEFAULT_HISTORY_KEY
                )
        );
    }

    @Test
    void rejectsEscapeAndAcceptsUnbound() {
        assertFalse(DialogueKey.isValidName("key.keyboard.escape"));
        assertTrue(DialogueKey.isValidName(DialogueKey.UNBOUND_NAME));
    }
}
