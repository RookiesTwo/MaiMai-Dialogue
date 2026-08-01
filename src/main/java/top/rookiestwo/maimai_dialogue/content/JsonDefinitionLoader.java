package top.rookiestwo.maimai_dialogue.content;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JsonDefinitionLoader {
    private JsonDefinitionLoader() {
    }

    // 加载指定目录中的全部 JSON，并把错误聚合到同一个结果中。
    public static <T> DefinitionLoadResult<T> load(
            ResourceManager resourceManager,
            DefinitionType<T> type
    ) {
        Map<ResourceLocation, T> definitions = new LinkedHashMap<>();
        List<DefinitionLoadIssue> issues = new ArrayList<>();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                type.directory(),
                id -> id.getPath().endsWith(".json")
        );

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation resourceId = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                List<String> decodeErrors = new ArrayList<>();
                Optional<T> decoded = type.codec()
                        .parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                        .resultOrPartial(decodeErrors::add);
                if (decoded.isPresent()) {
                    definitions.put(type.toDefinitionId(resourceId), decoded.get());
                } else {
                    issues.add(new DefinitionLoadIssue(
                            resourceId,
                            String.join("; ", decodeErrors)
                    ));
                }
            } catch (IOException | RuntimeException exception) {
                issues.add(new DefinitionLoadIssue(
                        resourceId,
                        exception.getMessage() == null
                                ? exception.getClass().getSimpleName()
                                : exception.getMessage()
                ));
            }
        }
        return new DefinitionLoadResult<>(
                new DefinitionRegistry<>(definitions),
                issues
        );
    }
}
