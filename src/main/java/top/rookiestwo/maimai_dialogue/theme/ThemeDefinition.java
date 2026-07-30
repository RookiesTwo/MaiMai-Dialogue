package top.rookiestwo.maimai_dialogue.theme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

public record ThemeDefinition(
        DialogueBoxTheme box,
        ThemeText text,
        ThemeOption option,
        ThemeSpacing spacing,
        ThemeControls controls
) {
    public static final ThemeDefinition DEFAULT = new ThemeDefinition(
            DialogueBoxTheme.DEFAULT,
            ThemeText.DEFAULT,
            ThemeOption.DEFAULT,
            ThemeSpacing.DEFAULT,
            ThemeControls.DEFAULT
    );

    public static final Codec<ThemeDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DialogueBoxTheme.CODEC.optionalFieldOf(
                                    "box",
                                    DEFAULT.box()
                            )
                            .forGetter(ThemeDefinition::box),
                    ThemeText.CODEC.optionalFieldOf(
                                    "text",
                                    DEFAULT.text()
                            )
                            .forGetter(ThemeDefinition::text),
                    ThemeOption.CODEC.optionalFieldOf(
                                    "option",
                                    DEFAULT.option()
                            )
                            .forGetter(ThemeDefinition::option),
                    ThemeSpacing.CODEC.optionalFieldOf(
                                    "spacing",
                                    DEFAULT.spacing()
                            )
                            .forGetter(ThemeDefinition::spacing),
                    ThemeControls.CODEC.optionalFieldOf(
                                    "controls",
                                    DEFAULT.controls()
                            )
                            .forGetter(ThemeDefinition::controls)
            ).apply(instance, ThemeDefinition::new));

    public ThemeDefinition {
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(option, "option");
        Objects.requireNonNull(spacing, "spacing");
        Objects.requireNonNull(controls, "controls");
    }
}
