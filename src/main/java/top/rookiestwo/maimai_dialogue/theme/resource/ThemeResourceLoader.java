package top.rookiestwo.maimai_dialogue.theme.resource;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ThemeResourceLoader {
    public static final String DIRECTORY = "dialogue_themes";

    private static final String DIRECTORY_PREFIX = DIRECTORY + "/";
    private static final String JSON_SUFFIX = ".json";

    private ThemeResourceLoader() {
    }

    public static ThemeLoadResult load(ResourceManager resourceManager) {
        Map<ResourceLocation, ThemeDefinition> definitions =
                new LinkedHashMap<>();
        List<ThemeLoadError> errors = new ArrayList<>();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                DIRECTORY,
                resourceId -> resourceId.getPath().endsWith(JSON_SUFFIX)
        );

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation resourceId = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader);
                List<String> decodeErrors = new ArrayList<>();
                Optional<ThemeDefinition> decoded = ThemeDefinition.CODEC
                        .parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(decodeErrors::add);
                if (decoded.isPresent()) {
                    definitions.put(toThemeId(resourceId), decoded.get());
                } else {
                    errors.add(new ThemeLoadError(
                            resourceId,
                            String.join("; ", decodeErrors)
                    ));
                }
            } catch (IOException | RuntimeException exception) {
                errors.add(new ThemeLoadError(
                        resourceId,
                        exception.getMessage() == null
                                ? exception.getClass().getSimpleName()
                                : exception.getMessage()
                ));
            }
        }
        return new ThemeLoadResult(
                new ThemeSnapshot(definitions),
                errors
        );
    }

    public static ResourceLocation toThemeId(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        if (!path.startsWith(DIRECTORY_PREFIX)
                || !path.endsWith(JSON_SUFFIX)) {
            throw new IllegalArgumentException(
                    "Not a dialogue theme JSON resource: " + resourceId
            );
        }
        String themePath = path.substring(
                DIRECTORY_PREFIX.length(),
                path.length() - JSON_SUFFIX.length()
        );
        if (themePath.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dialogue theme path must not be empty: " + resourceId
            );
        }
        return ResourceLocation.fromNamespaceAndPath(
                resourceId.getNamespace(),
                themePath
        );
    }
}
