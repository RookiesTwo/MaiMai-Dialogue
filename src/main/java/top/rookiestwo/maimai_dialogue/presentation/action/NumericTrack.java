package top.rookiestwo.maimai_dialogue.presentation.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.List;
import java.util.Objects;

public record NumericTrack(List<NumericKeyframe> keyframes) {
    public static final Codec<NumericTrack> CODEC =
            NumericKeyframe.CODEC.listOf()
                    .xmap(NumericTrack::new, NumericTrack::keyframes)
                    .flatXmap(NumericTrack::validate, DataResult::success);

    public NumericTrack {
        Objects.requireNonNull(keyframes, "keyframes");
        keyframes = List.copyOf(keyframes);
    }

    public float valueAt(float fraction) {
        if (keyframes.isEmpty()) {
            return 0.0F;
        }
        NumericKeyframe previous = new NumericKeyframe(0.0F, 0.0F);
        for (NumericKeyframe next : keyframes) {
            if (fraction <= next.at()) {
                float distance = next.at() - previous.at();
                if (distance <= 0.0F) {
                    return next.value();
                }
                float local = (fraction - previous.at()) / distance;
                return previous.value()
                        + (next.value() - previous.value()) * local;
            }
            previous = next;
        }
        return previous.value();
    }

    public float finalValue() {
        return keyframes.getLast().value();
    }

    private static DataResult<NumericTrack> validate(NumericTrack track) {
        if (track.keyframes.isEmpty()) {
            return DataResult.error(
                    () -> "Numeric action track must not be empty."
            );
        }
        float previous = -1.0F;
        for (NumericKeyframe keyframe : track.keyframes) {
            if (keyframe.at() <= previous) {
                return DataResult.error(
                        () -> "Action keyframe times must be strictly increasing."
                );
            }
            previous = keyframe.at();
        }
        return DataResult.success(track);
    }
}
