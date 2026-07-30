package top.rookiestwo.maimai_dialogue.client.resource;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.dialogue.resource.DialogueReloadListener;
import top.rookiestwo.maimai_dialogue.dialogue.resource.DialogueSnapshots;
import top.rookiestwo.maimai_dialogue.presentation.action.resource.ActionReloadListener;
import top.rookiestwo.maimai_dialogue.presentation.action.resource.ActionSnapshots;
import top.rookiestwo.maimai_dialogue.speaker.resource.SpeakerReloadListener;
import top.rookiestwo.maimai_dialogue.speaker.resource.SpeakerSnapshots;
import top.rookiestwo.maimai_dialogue.theme.resource.ThemeReloadListener;
import top.rookiestwo.maimai_dialogue.theme.resource.ThemeSnapshots;

@EventBusSubscriber(
        modid = MaiMaiDialogue.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
@SuppressWarnings("removal")
public final class ClientDialogueReloadEvents {
    private ClientDialogueReloadEvents() {
    }

    @SubscribeEvent
    public static void registerReloadListeners(
            RegisterClientReloadListenersEvent event
    ) {
        event.registerReloadListener(new DialogueReloadListener(
                DialogueSnapshots::replaceClient
        ));
        event.registerReloadListener(new SpeakerReloadListener(
                SpeakerSnapshots::replaceClient
        ));
        event.registerReloadListener(new ThemeReloadListener(
                ThemeSnapshots::replaceClient
        ));
        event.registerReloadListener(new ActionReloadListener(
                ActionSnapshots::replaceClient
        ));
        event.registerReloadListener(
                new ClientResourceValidationReloadListener()
        );
    }
}
