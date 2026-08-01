package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.rookiestwo.maimai_dialogue.progress.ProgressExpression;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DialogueDefinition(
        Optional<ProgressExpression> requires,
        Presentation presentation,
        List<DialogueStep> steps,
        DialogueEnd end
) {
    public static final Codec<DialogueDefinition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            ProgressExpression.CODEC.optionalFieldOf("requires")
                                    .forGetter(DialogueDefinition::requires),
                            Presentation.CODEC.fieldOf("presentation")
                                    .forGetter(DialogueDefinition::presentation),
                            DialogueStep.CODEC.listOf()
                                    .optionalFieldOf("steps", List.of())
                                    .forGetter(DialogueDefinition::steps),
                            DialogueEnd.CODEC.fieldOf("end")
                                    .forGetter(DialogueDefinition::end)
                    ).apply(instance, DialogueDefinition::new)
            );

    public DialogueDefinition {
        Objects.requireNonNull(requires, "requires");
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(end, "end");
        steps = List.copyOf(steps);
    }
}
