package top.rookiestwo.maimai_dialogue.client.session;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.SceneDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.VisualAssetDefinition;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneAction;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.util.Optional;

public interface DialogueContentLookup {
    Optional<DialogueDefinition> dialogue(ResourceLocation id);

    Optional<SpeakerDefinition> speaker(ResourceLocation id);

    Optional<ThemeDefinition> theme(ResourceLocation id);

    default Optional<SceneDefinition> scene(ResourceLocation id) {
        return Optional.empty();
    }

    default Optional<VisualAssetDefinition> visualAsset(ResourceLocation id) {
        return Optional.empty();
    }

    Optional<SceneAction> action(ResourceLocation id);
}
