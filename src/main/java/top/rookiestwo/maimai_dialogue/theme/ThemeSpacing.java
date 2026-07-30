package top.rookiestwo.maimai_dialogue.theme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ThemeSpacing(
        int headerHorizontalDp,
        int headerVerticalDp,
        int contentHorizontalDp,
        int contentVerticalDp,
        int optionsPaddingDp,
        int optionsCollapsedMaxHeightDp,
        int optionsExpandedMaxHeightDp
) {
    public static final ThemeSpacing DEFAULT = new ThemeSpacing(
            12,
            7,
            12,
            10,
            6,
            160,
            320
    );

    public static final Codec<ThemeSpacing> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ThemeCodecs.DP.optionalFieldOf(
                                    "header_horizontal",
                                    DEFAULT.headerHorizontalDp()
                            )
                            .forGetter(ThemeSpacing::headerHorizontalDp),
                    ThemeCodecs.DP.optionalFieldOf(
                                    "header_vertical",
                                    DEFAULT.headerVerticalDp()
                            )
                            .forGetter(ThemeSpacing::headerVerticalDp),
                    ThemeCodecs.DP.optionalFieldOf(
                                    "content_horizontal",
                                    DEFAULT.contentHorizontalDp()
                            )
                            .forGetter(ThemeSpacing::contentHorizontalDp),
                    ThemeCodecs.DP.optionalFieldOf(
                                    "content_vertical",
                                    DEFAULT.contentVerticalDp()
                            )
                            .forGetter(ThemeSpacing::contentVerticalDp),
                    ThemeCodecs.DP.optionalFieldOf(
                                    "options_padding",
                                    DEFAULT.optionsPaddingDp()
                            )
                            .forGetter(ThemeSpacing::optionsPaddingDp),
                    ThemeCodecs.PANEL_DP.optionalFieldOf(
                                    "options_collapsed_max_height",
                                    DEFAULT.optionsCollapsedMaxHeightDp()
                            )
                            .forGetter(
                                    ThemeSpacing::optionsCollapsedMaxHeightDp
                            ),
                    ThemeCodecs.PANEL_DP.optionalFieldOf(
                                    "options_expanded_max_height",
                                    DEFAULT.optionsExpandedMaxHeightDp()
                            )
                            .forGetter(
                                    ThemeSpacing::optionsExpandedMaxHeightDp
                            )
            ).apply(instance, ThemeSpacing::new));
}
