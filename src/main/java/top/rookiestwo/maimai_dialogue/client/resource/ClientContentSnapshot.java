package top.rookiestwo.maimai_dialogue.client.resource;

import top.rookiestwo.maimai_dialogue.content.DefinitionRegistry;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.VisualAssetDefinition;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneAction;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.util.Objects;

public record ClientContentSnapshot(
        DefinitionRegistry<DialogueDefinition> dialogues,
        DefinitionRegistry<SpeakerDefinition> speakers,
        DefinitionRegistry<ThemeDefinition> themes,
        DefinitionRegistry<VisualAssetDefinition> visualAssets,
        DefinitionRegistry<SceneAction> actions
) {
    public static final ClientContentSnapshot EMPTY = new ClientContentSnapshot(
            DefinitionRegistry.empty(),
            DefinitionRegistry.empty(),
            DefinitionRegistry.empty(),
            DefinitionRegistry.empty(),
            DefinitionRegistry.empty()
    );

    public ClientContentSnapshot(
            DefinitionRegistry<DialogueDefinition> dialogues,
            DefinitionRegistry<SpeakerDefinition> speakers,
            DefinitionRegistry<ThemeDefinition> themes,
            DefinitionRegistry<SceneAction> actions
    ) {
        this(
                dialogues,
                speakers,
                themes,
                DefinitionRegistry.empty(),
                actions
        );
    }

    public ClientContentSnapshot {
        Objects.requireNonNull(dialogues, "dialogues");
        Objects.requireNonNull(speakers, "speakers");
        Objects.requireNonNull(themes, "themes");
        Objects.requireNonNull(visualAssets, "visualAssets");
        Objects.requireNonNull(actions, "actions");
    }
}
