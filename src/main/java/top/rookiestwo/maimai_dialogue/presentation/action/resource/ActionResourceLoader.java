package top.rookiestwo.maimai_dialogue.presentation.action.resource;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import top.rookiestwo.maimai_dialogue.presentation.action.PresentationAction;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ActionResourceLoader {
    public static final String DIRECTORY = "presentation_actions";

    private static final String DIRECTORY_PREFIX = DIRECTORY + "/";
    private static final String JSON_SUFFIX = ".json";

    private ActionResourceLoader() {
    }

    public static ActionLoadResult load(ResourceManager resourceManager) {
        Map<ResourceLocation, PresentationAction> definitions =
                new LinkedHashMap<>();
        List<ActionLoadError> errors = new ArrayList<>();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                DIRECTORY,
                resourceId -> resourceId.getPath().endsWith(JSON_SUFFIX)
        );

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation resourceId = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader);
                List<String> decodeErrors = new ArrayList<>();
                Optional<PresentationAction> decoded = PresentationAction.CODEC
                        .parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(decodeErrors::add);
                if (decoded.isPresent()) {
                    definitions.put(toActionId(resourceId), decoded.get());
                } else {
                    errors.add(new ActionLoadError(
                            resourceId,
                            String.join("; ", decodeErrors)
                    ));
                }
            } catch (IOException | RuntimeException exception) {
                errors.add(new ActionLoadError(
                        resourceId,
                        exception.getMessage() == null
                                ? exception.getClass().getSimpleName()
                                : exception.getMessage()
                ));
            }
        }
        return new ActionLoadResult(
                new ActionSnapshot(definitions),
                errors
        );
    }

    public static ResourceLocation toActionId(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        if (!path.startsWith(DIRECTORY_PREFIX)
                || !path.endsWith(JSON_SUFFIX)) {
            throw new IllegalArgumentException(
                    "Not a presentation action JSON resource: " + resourceId
            );
        }
        String actionPath = path.substring(
                DIRECTORY_PREFIX.length(),
                path.length() - JSON_SUFFIX.length()
        );
        if (actionPath.isEmpty()) {
            throw new IllegalArgumentException(
                    "Presentation action path must not be empty: " + resourceId
            );
        }
        return ResourceLocation.fromNamespaceAndPath(
                resourceId.getNamespace(),
                actionPath
        );
    }
}
