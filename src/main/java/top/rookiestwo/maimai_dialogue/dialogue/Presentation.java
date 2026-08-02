package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record Presentation(
        ResourceLocation theme,
        Optional<ResourceLocation> scene,
        Optional<SceneBackground> background,
        DialogueBoxLayout dialogueBox,
        Map<String, VisualObject> visualObjects,
        Optional<SceneFilter> filter
) {
    private static final Codec<Presentation> BASE_CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            ResourceLocation.CODEC.fieldOf("theme")
                                    .forGetter(Presentation::theme),
                            ResourceLocation.CODEC.optionalFieldOf("scene")
                                    .forGetter(Presentation::scene),
                            SceneBackground.CODEC.optionalFieldOf("background")
                                    .forGetter(Presentation::background),
                            DialogueBoxLayout.CODEC.optionalFieldOf(
                                            "dialogue_box",
                                            DialogueBoxLayout.DEFAULT
                                    )
                                    .forGetter(Presentation::dialogueBox),
                            Codec.unboundedMap(
                                            Codec.STRING,
                                            VisualObject.CODEC
                                    )
                                    .optionalFieldOf(
                                            "visual_objects",
                                            Map.of()
                                    )
                                    .forGetter(Presentation::visualObjects),
                            SceneFilter.CODEC.optionalFieldOf("filter")
                                    .forGetter(Presentation::filter)
                    ).apply(instance, Presentation::new)
            );

    public static final Codec<Presentation> CODEC = BASE_CODEC.flatXmap(
            Presentation::validate,
            DataResult::success
    );

    public Presentation {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(dialogueBox, "dialogueBox");
        Objects.requireNonNull(visualObjects, "visualObjects");
        Objects.requireNonNull(filter, "filter");
        visualObjects = Map.copyOf(visualObjects);
    }

    /**
     * Backward-compatible constructor for an inline Presentation.
     */
    public Presentation(
            ResourceLocation theme,
            Optional<SceneBackground> background,
            DialogueBoxLayout dialogueBox,
            Map<String, VisualObject> visualObjects,
            Optional<SceneFilter> filter
    ) {
        this(
                theme,
                Optional.empty(),
                background,
                dialogueBox,
                visualObjects,
                filter
        );
    }

    public Presentation(ResourceLocation theme) {
        this(
                theme,
                Optional.empty(),
                Optional.empty(),
                DialogueBoxLayout.DEFAULT,
                Map.of(),
                Optional.empty()
        );
    }

    private static DataResult<Presentation> validate(
            Presentation presentation
    ) {
        for (String objectId : presentation.visualObjects.keySet()) {
            if (objectId.equals("background")
                    || objectId.equals("dialogue")) {
                return DataResult.error(
                        () -> "VisualObject ID '" + objectId
                                + "' is reserved."
                );
            }
            if (!objectId.matches("[a-z0-9_-]+")) {
                return DataResult.error(
                        () -> "Invalid VisualObject ID '" + objectId + "'."
                );
            }
        }
        return DataResult.success(presentation);
    }
}
