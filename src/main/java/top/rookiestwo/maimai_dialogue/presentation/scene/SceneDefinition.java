package top.rookiestwo.maimai_dialogue.presentation.scene;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.content.DefinitionCodecs;
import top.rookiestwo.maimai_dialogue.presentation.DialogueBoxLayout;
import top.rookiestwo.maimai_dialogue.presentation.filter.SceneFilter;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualObject;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Complete initial scene shared by Dialogues. Playback always owns its mutable state.
 */
public record SceneDefinition(
        ResourceLocation theme,
        Optional<SceneBackground> background,
        DialogueBoxLayout dialogueBox,
        Map<String, VisualObject> visualObjects,
        Optional<SceneFilter> filter
) {
    public static final ResourceLocation DEFAULT_THEME_ID = ResourceLocation.fromNamespaceAndPath("maimai_dialogue", "default");
    public static final ResourceLocation DEFAULT_SCENE_ID = ResourceLocation.fromNamespaceAndPath("maimai_dialogue", "default");
    public static final SceneDefinition DEFAULT = new SceneDefinition(DEFAULT_THEME_ID, Optional.empty(),
            DialogueBoxLayout.DEFAULT, Map.of(), Optional.empty());

    private static final Codec<SceneDefinition> BASE_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("theme", DEFAULT_THEME_ID)
                            .forGetter(SceneDefinition::theme),
                    SceneBackground.CODEC.optionalFieldOf("background")
                            .forGetter(SceneDefinition::background),
                    DialogueBoxLayout.CODEC.optionalFieldOf("dialogue_box", DialogueBoxLayout.DEFAULT)
                            .forGetter(SceneDefinition::dialogueBox),
                    Codec.unboundedMap(Codec.STRING, VisualObject.CODEC)
                            .optionalFieldOf("visual_objects", Map.of())
                            .forGetter(SceneDefinition::visualObjects),
                    SceneFilter.CODEC.optionalFieldOf("filter")
                            .forGetter(SceneDefinition::filter)
            ).apply(instance, SceneDefinition::new));

    public static final Codec<SceneDefinition> CODEC = DefinitionCodecs.rejectFields(BASE_CODEC.flatXmap(
            SceneDefinition::validate,
            SceneDefinition::validate
    ), "scene", "type", "id");

    public SceneDefinition {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(dialogueBox, "dialogueBox");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(visualObjects, "visualObjects");
        Objects.requireNonNull(filter, "filter");
        visualObjects = Map.copyOf(visualObjects);
    }

    public SceneDefinition(Optional<SceneBackground> background, Map<String, VisualObject> visualObjects,
                           Optional<SceneFilter> filter) {
        this(DEFAULT_THEME_ID, background, DialogueBoxLayout.DEFAULT, visualObjects, filter);
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
