package top.rookiestwo.maimai_dialogue.speaker.resource;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SpeakerResourceLoader {
    public static final String DIRECTORY = "speakers";

    private static final String DIRECTORY_PREFIX = DIRECTORY + "/";
    private static final String JSON_SUFFIX = ".json";

    private SpeakerResourceLoader() {
    }

    public static SpeakerLoadResult load(ResourceManager resourceManager) {
        Map<ResourceLocation, SpeakerDefinition> definitions =
                new LinkedHashMap<>();
        List<SpeakerLoadError> errors = new ArrayList<>();

        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                DIRECTORY,
                resourceId -> resourceId.getPath().endsWith(JSON_SUFFIX)
        );

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation resourceId = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader);
                List<String> decodeErrors = new ArrayList<>();
                Optional<SpeakerDefinition> decoded = SpeakerDefinition.CODEC
                        .parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(decodeErrors::add);

                if (decoded.isPresent()) {
                    definitions.put(toSpeakerId(resourceId), decoded.get());
                } else {
                    errors.add(new SpeakerLoadError(
                            resourceId,
                            String.join("; ", decodeErrors)
                    ));
                }
            } catch (IOException | RuntimeException exception) {
                errors.add(new SpeakerLoadError(
                        resourceId,
                        exception.getMessage() == null
                                ? exception.getClass().getSimpleName()
                                : exception.getMessage()
                ));
            }
        }

        return new SpeakerLoadResult(
                new SpeakerSnapshot(definitions),
                errors
        );
    }

    public static ResourceLocation toSpeakerId(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        if (!path.startsWith(DIRECTORY_PREFIX)
                || !path.endsWith(JSON_SUFFIX)) {
            throw new IllegalArgumentException(
                    "Not a speaker JSON resource: " + resourceId
            );
        }

        String speakerPath = path.substring(
                DIRECTORY_PREFIX.length(),
                path.length() - JSON_SUFFIX.length()
        );
        if (speakerPath.isEmpty()) {
            throw new IllegalArgumentException(
                    "Speaker resource path must not be empty: " + resourceId
            );
        }
        return ResourceLocation.fromNamespaceAndPath(
                resourceId.getNamespace(),
                speakerPath
        );
    }
}
