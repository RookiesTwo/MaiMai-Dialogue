package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reusable visual stage shared by Dialogue presentations.
 */
public record SceneDefinition(
        Optional<SceneBackground> background,
        Map<String, VisualObject> visualObjects,
        Optional<SceneFilter> filter
) {
    private static final Codec<SceneDefinition> BASE_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    SceneBackground.CODEC.optionalFieldOf("background")
                            .forGetter(SceneDefinition::background),
                    Codec.unboundedMap(Codec.STRING, VisualObject.CODEC)
                            .optionalFieldOf("visual_objects", Map.of())
                            .forGetter(SceneDefinition::visualObjects),
                    SceneFilter.CODEC.optionalFieldOf("filter")
                            .forGetter(SceneDefinition::filter)
            ).apply(instance, SceneDefinition::new));

    public static final Codec<SceneDefinition> CODEC = BASE_CODEC.flatXmap(
            SceneDefinition::validate,
            SceneDefinition::validate
    );

    public SceneDefinition {
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(visualObjects, "visualObjects");
        Objects.requireNonNull(filter, "filter");
        visualObjects = Map.copyOf(visualObjects);
    }

    private static DataResult<SceneDefinition> validate(
            SceneDefinition definition
    ) {
        for (String objectId : definition.visualObjects().keySet()) {
            if (objectId.equals("background")
                    || objectId.equals("dialogue")) {
                return DataResult.error(
                        () -> "Scene VisualObject ID '" + objectId
                                + "' is reserved."
                );
            }
            if (!objectId.matches("[a-z0-9_-]+")) {
                return DataResult.error(
                        () -> "Invalid Scene VisualObject ID '"
                                + objectId + "'."
                );
            }
        }
        return DataResult.success(definition);
    }
}
