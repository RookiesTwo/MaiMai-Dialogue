package top.rookiestwo.maimai_dialogue.content;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record DefinitionType<T>(
        String directory,
        String displayName,
        Codec<T> codec
) {
    private static final String JSON_SUFFIX = ".json";

    public DefinitionType {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(codec, "codec");
    }

    // 把资源包中的 JSON 路径转换为业务 definition ID。
    public ResourceLocation toDefinitionId(ResourceLocation resourceId) {
        String prefix = directory + "/";
        String path = resourceId.getPath();
        if (!path.startsWith(prefix) || !path.endsWith(JSON_SUFFIX)) {
            throw new IllegalArgumentException(
                    "Not a " + displayName + " JSON resource: " + resourceId
            );
        }
        String definitionPath = path.substring(
                prefix.length(),
                path.length() - JSON_SUFFIX.length()
        );
        if (definitionPath.isEmpty()) {
            throw new IllegalArgumentException(
                    displayName + " resource path must not be empty: "
                            + resourceId
            );
        }
        return ResourceLocation.fromNamespaceAndPath(
                resourceId.getNamespace(),
                definitionPath
        );
    }
}
