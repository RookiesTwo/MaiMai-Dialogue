package top.rookiestwo.maimai_dialogue.theme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.rookiestwo.maimai_dialogue.presentation.filter.SceneColor;

import java.util.Objects;

public record ThemeText(
        SceneColor primary,
        SceneColor error,
        int speakerSizeSp,
        int dialogueSizeSp,
        int optionSizeSp,
        int auxiliarySizeSp
) {
    public static final ThemeText DEFAULT = new ThemeText(
            new SceneColor(0xFFFFFFFF),
            new SceneColor(0xFFFF8080),
            15,
            16,
            15,
            13
    );

    public static final Codec<ThemeText> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    SceneColor.CODEC.optionalFieldOf(
                                    "primary",
                                    DEFAULT.primary()
                            )
                            .forGetter(ThemeText::primary),
                    SceneColor.CODEC.optionalFieldOf(
                                    "error",
                                    DEFAULT.error()
                            )
                            .forGetter(ThemeText::error),
                    ThemeCodecs.TEXT_SIZE.optionalFieldOf(
                                    "speaker_size",
                                    DEFAULT.speakerSizeSp()
                            )
                            .forGetter(ThemeText::speakerSizeSp),
                    ThemeCodecs.TEXT_SIZE.optionalFieldOf(
                                    "dialogue_size",
                                    DEFAULT.dialogueSizeSp()
                            )
                            .forGetter(ThemeText::dialogueSizeSp),
                    ThemeCodecs.TEXT_SIZE.optionalFieldOf(
                                    "option_size",
                                    DEFAULT.optionSizeSp()
                            )
                            .forGetter(ThemeText::optionSizeSp),
                    ThemeCodecs.TEXT_SIZE.optionalFieldOf(
                                    "auxiliary_size",
                                    DEFAULT.auxiliarySizeSp()
                            )
                            .forGetter(ThemeText::auxiliarySizeSp)
            ).apply(instance, ThemeText::new));

    public ThemeText {
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(error, "error");
    }
}
