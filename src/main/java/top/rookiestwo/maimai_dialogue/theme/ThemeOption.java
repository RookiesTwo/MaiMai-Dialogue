package top.rookiestwo.maimai_dialogue.theme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.rookiestwo.maimai_dialogue.dialogue.SceneColor;

import java.util.Objects;

public record ThemeOption(
        SceneColor background,
        SceneColor hoverBackground,
        SceneColor pressedBackground,
        SceneColor border,
        SceneColor hoverBorder,
        int borderWidthDp,
        int cornerRadiusDp,
        int horizontalPaddingDp,
        int verticalPaddingDp,
        int spacingDp
) {
    public static final ThemeOption DEFAULT = new ThemeOption(
            new SceneColor(0x38000000),
            new SceneColor(0x78000000),
            new SceneColor(0xA0000000),
            new SceneColor(0x70FFFFFF),
            new SceneColor(0xFFFFFFFF),
            0,
            1,
            12,
            8,
            2
    );

    public static final Codec<ThemeOption> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    SceneColor.CODEC.optionalFieldOf(
                                    "background",
                                    DEFAULT.background()
                            )
                            .forGetter(ThemeOption::background),
                    SceneColor.CODEC.optionalFieldOf(
                                    "hover_background",
                                    DEFAULT.hoverBackground()
                            )
                            .forGetter(ThemeOption::hoverBackground),
                    SceneColor.CODEC.optionalFieldOf(
                                    "pressed_background",
                                    DEFAULT.pressedBackground()
                            )
                            .forGetter(ThemeOption::pressedBackground),
                    SceneColor.CODEC.optionalFieldOf(
                                    "border",
                                    DEFAULT.border()
                            )
                            .forGetter(ThemeOption::border),
                    SceneColor.CODEC.optionalFieldOf(
                                    "hover_border",
                                    DEFAULT.hoverBorder()
                            )
                            .forGetter(ThemeOption::hoverBorder),
                    ThemeCodecs.DP.optionalFieldOf(
                                    "border_width",
                                    DEFAULT.borderWidthDp()
                            )
                            .forGetter(ThemeOption::borderWidthDp),
                    ThemeCodecs.DP.optionalFieldOf(
                                    "corner_radius",
                                    DEFAULT.cornerRadiusDp()
                            )
                            .forGetter(ThemeOption::cornerRadiusDp),
                    ThemeCodecs.DP.optionalFieldOf(
                                    "horizontal_padding",
                                    DEFAULT.horizontalPaddingDp()
                            )
                            .forGetter(ThemeOption::horizontalPaddingDp),
                    ThemeCodecs.DP.optionalFieldOf(
                                    "vertical_padding",
                                    DEFAULT.verticalPaddingDp()
                            )
                            .forGetter(ThemeOption::verticalPaddingDp),
                    ThemeCodecs.DP.optionalFieldOf(
                                    "spacing",
                                    DEFAULT.spacingDp()
                            )
                            .forGetter(ThemeOption::spacingDp)
            ).apply(instance, ThemeOption::new));

    public ThemeOption(
            SceneColor background,
            SceneColor hoverBackground,
            SceneColor pressedBackground,
            SceneColor border,
            SceneColor hoverBorder,
            int cornerRadiusDp,
            int horizontalPaddingDp,
            int verticalPaddingDp,
            int spacingDp
    ) {
        this(
                background,
                hoverBackground,
                pressedBackground,
                border,
                hoverBorder,
                1,
                cornerRadiusDp,
                horizontalPaddingDp,
                verticalPaddingDp,
                spacingDp
        );
    }

    public ThemeOption {
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(hoverBackground, "hoverBackground");
        Objects.requireNonNull(pressedBackground, "pressedBackground");
        Objects.requireNonNull(border, "border");
        Objects.requireNonNull(hoverBorder, "hoverBorder");
    }
}
