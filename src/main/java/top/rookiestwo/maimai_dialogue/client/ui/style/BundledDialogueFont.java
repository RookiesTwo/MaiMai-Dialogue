package top.rookiestwo.maimai_dialogue.client.ui.style;

import icyllis.modernui.graphics.text.FontFamily;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.client.config.ClientPreferences;

import java.awt.FontFormatException;
import java.io.IOException;

/** The mod's own font, loaded once without registering or replacing system/ModernUI fonts. */
@SuppressWarnings("UnstableApiUsage")
final class BundledDialogueFont {
    static final String RESOURCE = "/assets/maimai_dialogue/fonts/chill_round_f_regular.ttf";
    private static final String LOCALIZED_FAMILY = "寒蝉全圆体";

    private BundledDialogueFont() {}

    static boolean matches(String name) {
        return ClientPreferences.DEFAULT_FONT_FAMILY.equalsIgnoreCase(name) || LOCALIZED_FAMILY.equals(name);
    }

    static FontFamily family() { return Holder.FAMILY; }

    private static final class Holder {
        private static final FontFamily FAMILY = load();
    }

    private static FontFamily load() {
        try (var input = BundledDialogueFont.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Missing bundled font: " + RESOURCE);
            return FontFamily.createFamily(input, false);
        } catch (IOException | FontFormatException failure) {
            MaiMaiDialogue.LOGGER.error("Failed to load the bundled Dialogue font; using font fallback.", failure);
            return null;
        }
    }
}
