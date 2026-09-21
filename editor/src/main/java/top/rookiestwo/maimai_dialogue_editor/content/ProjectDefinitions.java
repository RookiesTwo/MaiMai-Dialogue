package top.rookiestwo.maimai_dialogue_editor.content;

import top.rookiestwo.maimai_dialogue.content.DefinitionType;
import top.rookiestwo.maimai_dialogue.content.DefinitionTypes;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;

/** Export paths and codecs come from the same registry as the runtime loaders. */
public final class ProjectDefinitions {
    private ProjectDefinitions() {}

    public static DefinitionType<?> type(ResourceKind kind) {
        return switch (kind) {
            case DIALOGUE -> DefinitionTypes.DIALOGUE;
            case SPEAKER -> DefinitionTypes.SPEAKER;
            case SCENE -> DefinitionTypes.SCENE;
            case VISUAL_ASSET -> DefinitionTypes.VISUAL_ASSET;
            case ACTION -> DefinitionTypes.ACTION;
            case THEME -> DefinitionTypes.THEME;
            default -> null;
        };
    }
}
