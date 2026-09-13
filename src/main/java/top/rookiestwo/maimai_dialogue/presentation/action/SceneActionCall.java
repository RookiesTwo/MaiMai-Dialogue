package top.rookiestwo.maimai_dialogue.presentation.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.Optional;

public record SceneActionCall(
        String target,
        int delayMs,
        ActionSpec action
) {
    private static final Codec<String> TARGET_CODEC = Codec.STRING.validate(
            value -> value.matches("[a-z0-9_-]+")
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "Invalid SceneAction target ID '"
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

    public static final Codec<SceneActionCall> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    TARGET_CODEC.optionalFieldOf("target")
                            .forGetter(call -> call.target().isEmpty() ? Optional.empty() : Optional.of(call.target())),
                    DELAY_CODEC.optionalFieldOf("delay_ms", 0)
                            .forGetter(SceneActionCall::delayMs),
                    ActionSpec.CODEC.fieldOf("action")
                            .forGetter(SceneActionCall::action)
            ).apply(instance, (target, delay, action) -> new SceneActionCall(target.orElse(""), delay, action)));

    public SceneActionCall {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(action, "action");
    }
}
