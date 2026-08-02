package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.ModernUI;
import icyllis.modernui.graphics.text.FontFamily;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.client.config.ClientPreferences;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("UnstableApiUsage")
public record DialogueTypography(Typeface typeface, float scale) {
    private static final Set<String> WARNED_MISSING_FONTS =
            ConcurrentHashMap.newKeySet();

    public static DialogueTypography resolve(ClientPreferences preferences) {
        return new DialogueTypography(
                resolveTypeface(preferences.fontFamily()),
                (float) preferences.fontScale()
        );
    }

    public static List<String> availableFontFamilies() {
        return FontFamily.getSystemFontMap()
                .keySet()
                .stream()
                .sorted(Comparator.comparing(
                        value -> value.toLowerCase(Locale.ROOT)
                ))
                .toList();
    }

    public float scaled(float textSizeSp) {
        return textSizeSp * scale;
    }

    public void apply(TextView view, float textSizeSp) {
        view.setTypeface(typeface);
        view.setTextSize(scaled(textSizeSp));
    }

    public static Typeface resolveTypeface(String configuredName) {
        Typeface fallback = ModernUI.getSelectedTypeface();
        if (configuredName.isBlank()) {
            return fallback;
        }
        FontFamily selected = findFamily(configuredName);
        if (selected == null) {
            if (WARNED_MISSING_FONTS.add(configuredName)) {
                MaiMaiDialogue.LOGGER.warn(
                        "Dialogue font '{}' is unavailable; following Modern UI instead.",
                        configuredName
                );
            }
            return fallback;
        }
        LinkedHashSet<FontFamily> families = new LinkedHashSet<>();
        families.add(selected);
        families.addAll(fallback.getFamilies());
        return Typeface.createTypeface(families.toArray(FontFamily[]::new));
    }

    private static FontFamily findFamily(String configuredName) {
        FontFamily direct = FontFamily.getSystemFontWithAlias(configuredName);
        if (direct != null) {
            return direct;
        }
        for (FontFamily family : FontFamily.getSystemFontMap().values()) {
            if (family.getFamilyName().equalsIgnoreCase(configuredName)) {
                return family;
            }
        }
        return null;
    }
}
