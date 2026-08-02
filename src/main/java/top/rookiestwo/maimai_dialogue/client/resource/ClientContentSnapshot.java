package top.rookiestwo.maimai_dialogue.client.resource;

import top.rookiestwo.maimai_dialogue.content.DefinitionRegistry;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.PresentationDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.SceneDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.VisualAssetDefinition;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneAction;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.util.Objects;

public record ClientContentSnapshot(
        DefinitionRegistry<DialogueDefinition> dialogues,
        DefinitionRegistry<SpeakerDefinition> speakers,
        DefinitionRegistry<ThemeDefinition> themes,
        DefinitionRegistry<PresentationDefinition> presentations,
        DefinitionRegistry<SceneDefinition> scenes,
        DefinitionRegistry<VisualAssetDefinition> visualAssets,
        DefinitionRegistry<SceneAction> actions
) {
    public static final ClientContentSnapshot EMPTY = new ClientContentSnapshot(
            DefinitionRegistry.empty(),
            DefinitionRegistry.empty(),
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
            DefinitionRegistry<SceneDefinition> scenes,
            DefinitionRegistry<VisualAssetDefinition> visualAssets,
            DefinitionRegistry<SceneAction> actions
    ) {
        this(
                dialogues,
                speakers,
                themes,
                DefinitionRegistry.empty(),
                scenes,
                visualAssets,
                actions
        );
    }

    public ClientContentSnapshot(
            DefinitionRegistry<DialogueDefinition> dialogues,
            DefinitionRegistry<SpeakerDefinition> speakers,
            DefinitionRegistry<ThemeDefinition> themes,
            DefinitionRegistry<VisualAssetDefinition> visualAssets,
            DefinitionRegistry<SceneAction> actions
    ) {
        this(
                dialogues,
                speakers,
                themes,
                DefinitionRegistry.empty(),
                DefinitionRegistry.empty(),
                visualAssets,
                actions
        );
    }

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
                DefinitionRegistry.empty(),
                DefinitionRegistry.empty(),
                actions
        );
    }

    public ClientContentSnapshot {
        Objects.requireNonNull(dialogues, "dialogues");
        Objects.requireNonNull(speakers, "speakers");
        Objects.requireNonNull(themes, "themes");
        Objects.requireNonNull(presentations, "presentations");
        Objects.requireNonNull(scenes, "scenes");
        Objects.requireNonNull(visualAssets, "visualAssets");
        Objects.requireNonNull(actions, "actions");
    }
}
