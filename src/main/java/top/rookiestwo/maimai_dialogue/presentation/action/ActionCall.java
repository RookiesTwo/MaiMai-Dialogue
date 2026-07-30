package top.rookiestwo.maimai_dialogue.presentation.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

public record ActionCall(
        String target,
        int delayMs,
        ActionDefinition action
) {
    private static final Codec<String> TARGET_CODEC = Codec.STRING.validate(
            value -> value.matches("[a-z0-9_-]+")
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "Invalid PresentationAction target ID '"
                                    + value + "'."
                    )
    );
    private static final Codec<Integer> DELAY_CODEC = Codec.INT.validate(
            value -> value >= 0 && value <= 60_000
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "Action delay_ms must be between 0 and 60000."
                    )
    );

    public static final Codec<ActionCall> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    TARGET_CODEC.fieldOf("target")
                            .forGetter(ActionCall::target),
                    DELAY_CODEC.optionalFieldOf("delay_ms", 0)
                            .forGetter(ActionCall::delayMs),
                    ActionDefinition.CODEC.fieldOf("action")
                            .forGetter(ActionCall::action)
            ).apply(instance, ActionCall::new));

    public ActionCall {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(action, "action");
    }
}
