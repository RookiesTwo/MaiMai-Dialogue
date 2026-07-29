package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

public record DialogueBoxLayout(
        float x,
        float y,
        float width,
        float maxHeight,
        VisualAnchor anchor
) {
    private static final Codec<Float> UNIT_CODEC = Codec.FLOAT.validate(
            value -> value >= 0.0F && value <= 1.0F
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "Dialogue box coordinates must be between 0 and 1."
                    )
    );
    private static final Codec<Float> SIZE_CODEC = Codec.FLOAT.validate(
            value -> value > 0.0F && value <= 1.0F
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "Dialogue box size must be greater than 0 and at most 1."
                    )
    );

    public static final DialogueBoxLayout DEFAULT = new DialogueBoxLayout(
            0.5F,
            0.98F,
            0.96F,
            0.5F,
            VisualAnchor.BOTTOM_CENTER
    );

    public static final Codec<DialogueBoxLayout> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            UNIT_CODEC.optionalFieldOf("x", DEFAULT.x())
                                    .forGetter(DialogueBoxLayout::x),
                            UNIT_CODEC.optionalFieldOf("y", DEFAULT.y())
                                    .forGetter(DialogueBoxLayout::y),
                            SIZE_CODEC.optionalFieldOf(
                                            "width",
                                            DEFAULT.width()
                                    )
                                    .forGetter(DialogueBoxLayout::width),
                            SIZE_CODEC.optionalFieldOf(
                                            "max_height",
                                            DEFAULT.maxHeight()
                                    )
                                    .forGetter(DialogueBoxLayout::maxHeight),
                            VisualAnchor.CODEC.optionalFieldOf(
                                            "anchor",
                                            DEFAULT.anchor()
                                    )
                                    .forGetter(DialogueBoxLayout::anchor)
                    ).apply(instance, DialogueBoxLayout::new)
            );

    public DialogueBoxLayout {
        Objects.requireNonNull(anchor, "anchor");
    }
}
