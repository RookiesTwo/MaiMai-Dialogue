package top.rookiestwo.maimai_dialogue.content.resolve;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.presentation.Presentation;
import top.rookiestwo.maimai_dialogue.presentation.PresentationDefinition;
import top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualAssetDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class DialoguePresentationResolver {
    private DialoguePresentationResolver() {
    }

    // 保持引用、Theme、Scene、VisualAsset 的查询顺序及各层降级规则。
    public static Result resolve(
            Presentation source,
            Function<ResourceLocation, Optional<PresentationDefinition>> presentations,
            Function<ResourceLocation, Optional<ThemeDefinition>> themes,
            Function<ResourceLocation, Optional<SceneDefinition>> scenes,
            Function<ResourceLocation, Optional<VisualAssetDefinition>> visualAssets
    ) {
        var reference = PresentationResolver.resolve(source, presentations);
        var theme = themes.apply(reference.presentation().theme());
        var scene = SceneResolver.resolve(reference.presentation(), scenes);
        var visual = VisualAssetResolver.resolve(scene.presentation(), visualAssets);
        return new Result(
                reference.presentation(), visual.presentation(),
                theme.orElse(ThemeDefinition.DEFAULT), theme.isEmpty(),
                reference.errors(), scene.errors(), visual.errors()
        );
    }

    // 保留分阶段诊断，使校验器和播放端沿用各自原有的错误文案与顺序。
    public record Result(
            Presentation source,
            Presentation presentation,
            ThemeDefinition theme,
            boolean missingTheme,
            List<String> referenceErrors,
            List<String> sceneErrors,
            List<String> visualErrors
    ) {
        public Result {
            referenceErrors = List.copyOf(referenceErrors);
            sceneErrors = List.copyOf(sceneErrors);
            visualErrors = List.copyOf(visualErrors);
        }
    }
}
