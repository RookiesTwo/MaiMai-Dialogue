package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves reusable VisualAsset references into runtime-ready VisualObjects.
 */
public final class VisualAssetResolver {
    private VisualAssetResolver() {
    }

    public static Result resolve(
            Presentation presentation,
            Function<ResourceLocation, Optional<VisualAssetDefinition>> lookup
    ) {
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(lookup, "lookup");

        Map<String, VisualObject> resolvedObjects = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        presentation.visualObjects().forEach((objectId, object) -> {
            if (!object.referencesAsset()) {
                resolvedObjects.put(objectId, object);
                return;
            }
            ResourceLocation assetId = object.asset().orElseThrow();
            Optional<VisualAssetDefinition> definition = lookup.apply(assetId);
            if (definition.isEmpty()) {
                errors.add("VisualObject " + objectId
                        + " references missing VisualAsset " + assetId + ".");
                return;
            }
            DataResult<VisualObject> resolved = object.resolve(
                    definition.orElseThrow()
            );
            resolved.result().ifPresent(value ->
                    resolvedObjects.put(objectId, value)
            );
            resolved.error().ifPresent(error ->
                    errors.add("VisualObject " + objectId + ": "
                            + error.message())
            );
        });

        Presentation resolvedPresentation = new Presentation(
                presentation.theme(),
                presentation.background(),
                presentation.dialogueBox(),
                resolvedObjects,
                presentation.filter()
        );
        return new Result(resolvedPresentation, errors);
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
