package top.rookiestwo.maimai_dialogue.content.resolve;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualAssetDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Shared resolution for gameplay and authoring, with diagnostics and safe runtime fallbacks. */
public final class SceneResolver {
    private SceneResolver() {}

    public static Result resolve(ResourceLocation id,
                                 Function<ResourceLocation, Optional<SceneDefinition>> scenes,
                                 Function<ResourceLocation, Optional<ThemeDefinition>> themes,
                                 Function<ResourceLocation, Optional<VisualAssetDefinition>> visualAssets) {
        var source = scenes.apply(id);
        var resolved = resolve(source.orElse(SceneDefinition.DEFAULT), themes, visualAssets);
        return new Result(resolved.source(), resolved.scene(), resolved.theme(), resolved.missingTheme(),
                source.isEmpty() ? List.of("Dialogue references missing Scene " + id + ".") : List.of(), resolved.visualErrors());
    }

    public static Result resolve(SceneDefinition source,
                                 Function<ResourceLocation, Optional<ThemeDefinition>> themes,
                                 Function<ResourceLocation, Optional<VisualAssetDefinition>> visualAssets) {
        var theme = themes.apply(source.theme());
        var visual = VisualAssetResolver.resolve(source, visualAssets);
        return new Result(source, visual.scene(), theme.orElse(ThemeDefinition.DEFAULT), theme.isEmpty(), List.of(), visual.errors());
    }

    public record Result(SceneDefinition source, SceneDefinition scene, ThemeDefinition theme,
                         boolean missingTheme, List<String> sceneErrors, List<String> visualErrors) {
        public Result { sceneErrors = List.copyOf(sceneErrors); visualErrors = List.copyOf(visualErrors); }
    }
}
