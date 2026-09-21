package top.rookiestwo.maimai_dialogue.content.resolve;

import top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualAssetDefinition;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualObject;

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
            SceneDefinition sceneDefinition,
            Function<ResourceLocation, Optional<VisualAssetDefinition>> lookup
    ) {
        Objects.requireNonNull(sceneDefinition, "sceneDefinition");
        Objects.requireNonNull(lookup, "lookup");

        Map<String, VisualObject> resolvedObjects = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        var background = sceneDefinition.background().flatMap(source -> {
            var asset = lookup.apply(source.asset());
            if (asset.isEmpty()) {
                errors.add("Background references missing VisualAsset " + source.asset() + ".");
                return Optional.empty();
            }
            var resolved = source.resolve(asset.orElseThrow());
            resolved.error().ifPresent(error -> errors.add(error.message()));
            return resolved.result();
        });
        sceneDefinition.visualObjects().forEach((objectId, object) -> {
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

        SceneDefinition resolvedScene = new SceneDefinition(
                sceneDefinition.theme(),
                background,
                sceneDefinition.dialogueBox(),
                resolvedObjects,
                sceneDefinition.filter()
        );
        return new Result(resolvedScene, errors);
    }

    public record Result(
            SceneDefinition scene,
            List<String> errors
    ) {
        public Result {
            Objects.requireNonNull(scene, "scene");
            Objects.requireNonNull(errors, "errors");
            errors = List.copyOf(errors);
        }
    }
}
