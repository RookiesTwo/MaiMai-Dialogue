package top.rookiestwo.maimai_dialogue.dialogue.resource;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DialogueResourceLoader {
    public static final String DIRECTORY = "dialogues";

    private static final String DIRECTORY_PREFIX = DIRECTORY + "/";
    private static final String JSON_SUFFIX = ".json";

    private DialogueResourceLoader() {
    }

    public static DialogueLoadResult load(ResourceManager resourceManager) {
        Map<ResourceLocation, DialogueDefinition> definitions =
                new LinkedHashMap<>();
        List<DialogueLoadError> errors = new ArrayList<>();

        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                DIRECTORY,
                resourceId -> resourceId.getPath().endsWith(JSON_SUFFIX)
        );

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation resourceId = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader);
                List<String> decodeErrors = new ArrayList<>();
                Optional<DialogueDefinition> decoded = DialogueDefinition.CODEC
                        .parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(decodeErrors::add);

                if (decoded.isPresent()) {
                    definitions.put(toDialogueId(resourceId), decoded.get());
                } else {
                    errors.add(new DialogueLoadError(
                            resourceId,
                            String.join("; ", decodeErrors)
                    ));
                }
            } catch (IOException | RuntimeException exception) {
                errors.add(new DialogueLoadError(
                        resourceId,
                        exception.getMessage() == null
                                ? exception.getClass().getSimpleName()
                                : exception.getMessage()
                ));
            }
        }

        return new DialogueLoadResult(
                new DialogueSnapshot(definitions),
                errors
        );
    }

    public static ResourceLocation toDialogueId(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        if (!path.startsWith(DIRECTORY_PREFIX)
                || !path.endsWith(JSON_SUFFIX)) {
            throw new IllegalArgumentException(
                    "Not a dialogue JSON resource: " + resourceId
            );
        }

        String dialoguePath = path.substring(
                DIRECTORY_PREFIX.length(),
                path.length() - JSON_SUFFIX.length()
        );
        if (dialoguePath.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dialogue resource path must not be empty: " + resourceId
            );
        }
        return ResourceLocation.fromNamespaceAndPath(
                resourceId.getNamespace(),
                dialoguePath
        );
    }
}
