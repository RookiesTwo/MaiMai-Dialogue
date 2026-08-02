package top.rookiestwo.maimai_dialogue.content;

import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.SceneDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.VisualAssetDefinition;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneAction;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

public final class DefinitionTypes {
    public static final DefinitionType<DialogueDefinition> DIALOGUE =
            new DefinitionType<>(
                    "dialogues",
                    "dialogue",
                    DialogueDefinition.CODEC
            );
    public static final DefinitionType<SpeakerDefinition> SPEAKER =
            new DefinitionType<>(
                    "speakers",
                    "speaker",
                    SpeakerDefinition.CODEC
            );
    public static final DefinitionType<ThemeDefinition> THEME =
            new DefinitionType<>(
                    "dialogue_themes",
                    "dialogue theme",
                    ThemeDefinition.CODEC
            );
    public static final DefinitionType<SceneAction> ACTION =
            new DefinitionType<>(
                    "presentation_actions",
                    "presentation action",
                    SceneAction.CODEC
            );
    public static final DefinitionType<VisualAssetDefinition> VISUAL_ASSET =
            new DefinitionType<>(
                    "visual_assets",
                    "visual asset",
                    VisualAssetDefinition.CODEC
            );
    public static final DefinitionType<SceneDefinition> SCENE =
            new DefinitionType<>(
                    "scenes",
                    "scene",
                    SceneDefinition.CODEC
            );

    private DefinitionTypes() {
    }
}
