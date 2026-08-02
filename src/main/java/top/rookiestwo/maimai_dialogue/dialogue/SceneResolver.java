package top.rookiestwo.maimai_dialogue.dialogue;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Merges a reusable Scene into a Dialogue-local Presentation.
 */
public final class SceneResolver {
    private SceneResolver() {
    }

    public static Result resolve(
            Presentation presentation,
            Function<ResourceLocation, Optional<SceneDefinition>> lookup
    ) {
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(lookup, "lookup");

        if (presentation.scene().isEmpty()) {
            return new Result(presentation, List.of());
        }

        ResourceLocation sceneId = presentation.scene().orElseThrow();
        Optional<SceneDefinition> definition = lookup.apply(sceneId);
        if (definition.isEmpty()) {
            return new Result(
                    withoutSceneReference(presentation),
                    List.of("Presentation references missing Scene "
                            + sceneId + ".")
            );
        }

        SceneDefinition scene = definition.orElseThrow();
        Map<String, VisualObject> visualObjects = new LinkedHashMap<>(
                scene.visualObjects()
        );
        visualObjects.putAll(presentation.visualObjects());

        return new Result(
                new Presentation(
                        presentation.theme(),
                        Optional.empty(),
                        presentation.background().or(scene::background),
                        presentation.dialogueBox(),
                        visualObjects,
                        presentation.filter().or(scene::filter)
                ),
                List.of()
        );
    }

    private static Presentation withoutSceneReference(
            Presentation presentation
    ) {
        return new Presentation(
                presentation.theme(),
                Optional.empty(),
                presentation.background(),
                presentation.dialogueBox(),
                presentation.visualObjects(),
                presentation.filter()
        );
    }

    public record Result(
            Presentation presentation,
            List<String> errors
    ) {
        public Result {
            Objects.requireNonNull(presentation, "presentation");
            Objects.requireNonNull(errors, "errors");
            errors = List.copyOf(errors);
        }
    }
}
