package top.rookiestwo.maimai_dialogue.theme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.rookiestwo.maimai_dialogue.dialogue.SceneColor;

import java.util.Objects;

public record ThemeControls(
        SceneColor icon,
        SceneColor scrollbarThumb,
        SceneColor scrollbarTrack,
        int scrollbarWidthDp
) {
    public static final ThemeControls DEFAULT = new ThemeControls(
            new SceneColor(0xFFFFFFFF),
            new SceneColor(0xB8FFFFFF),
            new SceneColor(0x38FFFFFF),
            4
    );

    public static final Codec<ThemeControls> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    SceneColor.CODEC.optionalFieldOf(
                                    "icon",
                                    DEFAULT.icon()
                            )
                            .forGetter(ThemeControls::icon),
                    SceneColor.CODEC.optionalFieldOf(
                                    "scrollbar_thumb",
                                    DEFAULT.scrollbarThumb()
                            )
                            .forGetter(ThemeControls::scrollbarThumb),
                    SceneColor.CODEC.optionalFieldOf(
                                    "scrollbar_track",
                                    DEFAULT.scrollbarTrack()
                            )
                            .forGetter(ThemeControls::scrollbarTrack),
                    ThemeCodecs.POSITIVE_DP.optionalFieldOf(
                                    "scrollbar_width",
                                    DEFAULT.scrollbarWidthDp()
                            )
                            .forGetter(ThemeControls::scrollbarWidthDp)
            ).apply(instance, ThemeControls::new));

    public ThemeControls {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(scrollbarThumb, "scrollbarThumb");
        Objects.requireNonNull(scrollbarTrack, "scrollbarTrack");
        if (scrollbarWidthDp < 1) {
            throw new IllegalArgumentException(
                    "scrollbarWidthDp must be positive"
            );
        }
    }
}
