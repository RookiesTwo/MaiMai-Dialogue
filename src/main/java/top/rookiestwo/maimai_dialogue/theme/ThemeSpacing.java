package top.rookiestwo.maimai_dialogue.theme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ThemeSpacing(
        int headerHorizontalDp,
        int headerVerticalDp,
        int contentHorizontalDp,
        int contentVerticalDp,
        int optionsPaddingDp,
        int optionsCollapsedLimit,
        int optionsExpandedLimit
) {
    public static final ThemeSpacing DEFAULT = new ThemeSpacing(
            12,
            7,
            12,
            10,
            6,
            3,
            6
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
                    ThemeCodecs.OPTION_COUNT.optionalFieldOf(
                                    "options_collapsed_limit",
                                    DEFAULT.optionsCollapsedLimit()
                            )
                            .forGetter(
                                    ThemeSpacing::optionsCollapsedLimit
                            ),
                    ThemeCodecs.OPTION_COUNT.optionalFieldOf(
                                    "options_expanded_limit",
                                    DEFAULT.optionsExpandedLimit()
                            )
                            .forGetter(
                                    ThemeSpacing::optionsExpandedLimit
                            )
            ).apply(instance, ThemeSpacing::new));

    public ThemeSpacing {
        optionsExpandedLimit = Math.max(
                optionsCollapsedLimit,
                optionsExpandedLimit
        );
    }
}
