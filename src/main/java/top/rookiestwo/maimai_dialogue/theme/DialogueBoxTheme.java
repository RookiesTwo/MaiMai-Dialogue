package top.rookiestwo.maimai_dialogue.theme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.rookiestwo.maimai_dialogue.dialogue.SceneColor;

import java.util.Objects;

public record DialogueBoxTheme(
        SceneColor background,
        SceneColor border,
        SceneColor divider,
        int cornerRadiusDp,
        int borderWidthDp
) {
    public static final DialogueBoxTheme DEFAULT = new DialogueBoxTheme(
            new SceneColor(0xCC08080C),
            new SceneColor(0xE6FFFFFF),
            new SceneColor(0x80FFFFFF),
            4,
            0
    );

    public static final Codec<DialogueBoxTheme> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    SceneColor.CODEC.optionalFieldOf(
                                    "background",
                                    DEFAULT.background()
                            )
                            .forGetter(DialogueBoxTheme::background),
                    SceneColor.CODEC.optionalFieldOf(
                                    "border",
                                    DEFAULT.border()
                            )
                            .forGetter(DialogueBoxTheme::border),
                    SceneColor.CODEC.optionalFieldOf(
                                    "divider",
                                    DEFAULT.divider()
                            )
                            .forGetter(DialogueBoxTheme::divider),
                    ThemeCodecs.DP.optionalFieldOf(
                                    "corner_radius",
                                    DEFAULT.cornerRadiusDp()
                            )
                            .forGetter(DialogueBoxTheme::cornerRadiusDp),
                    ThemeCodecs.DP.optionalFieldOf(
                                    "border_width",
                                    DEFAULT.borderWidthDp()
                            )
                            .forGetter(DialogueBoxTheme::borderWidthDp)
            ).apply(instance, DialogueBoxTheme::new));

    public DialogueBoxTheme {
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(border, "border");
        Objects.requireNonNull(divider, "divider");
    }
}
