package top.rookiestwo.maimai_dialogue.presentation;

import top.rookiestwo.maimai_dialogue.presentation.filter.SceneFilter;
import top.rookiestwo.maimai_dialogue.presentation.scene.SceneBackground;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualObject;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record Presentation(
        Optional<ResourceLocation> reference,
        ResourceLocation theme,
        Optional<ResourceLocation> scene,
        Optional<SceneBackground> background,
        DialogueBoxLayout dialogueBox,
        Map<String, VisualObject> visualObjects,
        Optional<SceneFilter> filter
) {
    public static final ResourceLocation DEFAULT_THEME_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "maimai_dialogue",
                    "default"
            );

    private static final Codec<Serialized> SERIALIZED_CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING.optionalFieldOf("type")
                                    .forGetter(Serialized::type),
                            ResourceLocation.CODEC.optionalFieldOf("id")
                                    .forGetter(Serialized::id),
                            ResourceLocation.CODEC.optionalFieldOf("theme")
                                    .forGetter(Serialized::theme),
                            ResourceLocation.CODEC.optionalFieldOf("scene")
                                    .forGetter(Serialized::scene),
                            SceneBackground.CODEC.optionalFieldOf("background")
                                    .forGetter(Serialized::background),
                            DialogueBoxLayout.CODEC.optionalFieldOf(
                                            "dialogue_box"
                                    )
                                    .forGetter(Serialized::dialogueBox),
                            Codec.unboundedMap(
                                            Codec.STRING,
                                            VisualObject.CODEC
                                    )
                                    .optionalFieldOf(
                                            "visual_objects",
                                            Map.of()
                                    )
                                    .forGetter(Serialized::visualObjects),
                            SceneFilter.CODEC.optionalFieldOf("filter")
                                    .forGetter(Serialized::filter)
                    ).apply(instance, Serialized::new)
            );

    public static final Codec<Presentation> CODEC = SERIALIZED_CODEC.flatXmap(
            Serialized::decode,
            Serialized::encode
    );

    public static final Codec<Presentation> INLINE_CODEC = CODEC.flatXmap(
            presentation -> presentation.referencesDefinition()
                    ? DataResult.error(
                            () -> "PresentationDefinition cannot reference another PresentationDefinition."
                    )
                    : DataResult.success(presentation),
            presentation -> presentation.referencesDefinition()
                    ? DataResult.error(
                            () -> "PresentationDefinition cannot reference another PresentationDefinition."
                    )
                    : DataResult.success(presentation)
    );

    public Presentation {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(dialogueBox, "dialogueBox");
        Objects.requireNonNull(visualObjects, "visualObjects");
        Objects.requireNonNull(filter, "filter");
        visualObjects = Map.copyOf(visualObjects);
        if (reference.isPresent()
                && (!theme.equals(DEFAULT_THEME_ID)
                || scene.isPresent()
                || background.isPresent()
                || !dialogueBox.equals(DialogueBoxLayout.DEFAULT)
                || !visualObjects.isEmpty()
                || filter.isPresent())) {
            throw new IllegalArgumentException(
                    "Referenced Presentation cannot define inline fields."
            );
        }
    }

    /**
     * Backward-compatible constructor for a fully inline Presentation.
     */
    public Presentation(
            ResourceLocation theme,
            Optional<ResourceLocation> scene,
            Optional<SceneBackground> background,
            DialogueBoxLayout dialogueBox,
            Map<String, VisualObject> visualObjects,
            Optional<SceneFilter> filter
    ) {
        this(
                Optional.empty(),
                theme,
                scene,
                background,
                dialogueBox,
                visualObjects,
                filter
        );
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
                Optional.empty(),
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
                Optional.empty(),
                theme,
                Optional.empty(),
                Optional.empty(),
                DialogueBoxLayout.DEFAULT,
                Map.of(),
                Optional.empty()
        );
    }

    public static Presentation reference(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return new Presentation(
                Optional.of(id),
                DEFAULT_THEME_ID,
                Optional.empty(),
                Optional.empty(),
                DialogueBoxLayout.DEFAULT,
                Map.of(),
                Optional.empty()
        );
    }

    public boolean referencesDefinition() {
        return reference.isPresent();
    }

    private static DataResult<Presentation> validate(
            Presentation presentation
    ) {
        if (presentation.referencesDefinition()) {
            return DataResult.success(presentation);
        }
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

    private record Serialized(
            Optional<String> type,
            Optional<ResourceLocation> id,
            Optional<ResourceLocation> theme,
            Optional<ResourceLocation> scene,
            Optional<SceneBackground> background,
            Optional<DialogueBoxLayout> dialogueBox,
            Map<String, VisualObject> visualObjects,
            Optional<SceneFilter> filter
    ) {
        private DataResult<Presentation> decode() {
            if (type.isPresent()) {
                if (!type.orElseThrow().equals("reference")) {
                    return DataResult.error(
                            () -> "Unknown Presentation type '"
                                    + type.orElseThrow() + "'."
                    );
                }
                if (id.isEmpty()) {
                    return DataResult.error(
                            () -> "Referenced Presentation requires id."
                    );
                }
                if (theme.isPresent()
                        || scene.isPresent()
                        || background.isPresent()
                        || dialogueBox.isPresent()
                        || !visualObjects.isEmpty()
                        || filter.isPresent()) {
                    return DataResult.error(
                            () -> "Referenced Presentation cannot define inline fields."
                    );
                }
                return DataResult.success(Presentation.reference(
                        id.orElseThrow()
                ));
            }
            if (id.isPresent()) {
                return DataResult.error(
                        () -> "Presentation id requires type 'reference'."
                );
            }
            if (theme.isEmpty()) {
                return DataResult.error(
                        () -> "Inline Presentation requires theme."
                );
            }
            return validate(new Presentation(
                    Optional.empty(),
                    theme.orElseThrow(),
                    scene,
                    background,
                    dialogueBox.orElse(DialogueBoxLayout.DEFAULT),
                    visualObjects,
                    filter
            ));
        }

        private static DataResult<Serialized> encode(
                Presentation presentation
        ) {
            DataResult<Presentation> validation = validate(presentation);
            if (validation.error().isPresent()) {
                return DataResult.error(
                        () -> validation.error().orElseThrow().message()
                );
            }
            if (presentation.referencesDefinition()) {
                return DataResult.success(new Serialized(
                        Optional.of("reference"),
                        presentation.reference(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Map.of(),
                        Optional.empty()
                ));
            }
            return DataResult.success(new Serialized(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(presentation.theme()),
                    presentation.scene(),
                    presentation.background(),
                    presentation.dialogueBox().equals(DialogueBoxLayout.DEFAULT)
                            ? Optional.empty()
                            : Optional.of(presentation.dialogueBox()),
                    presentation.visualObjects(),
                    presentation.filter()
            ));
        }
    }
}
