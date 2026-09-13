package top.rookiestwo.maimai_dialogue.client.bootstrap;

import top.rookiestwo.maimai_dialogue.client.ui.screen.DialogueFragment;

import top.rookiestwo.maimai_dialogue.client.controller.ClientDialogueController;

import top.rookiestwo.maimai_dialogue.client.resource.ClientContentSnapshot;
import top.rookiestwo.maimai_dialogue.content.ContentRepository;
import top.rookiestwo.maimai_dialogue.client.audio.DialogueAudioManager;
import top.rookiestwo.maimai_dialogue.client.audio.MinecraftAudioBackend;
import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;

public final class ClientServices {
    private static final ClientServices INSTANCE = new ClientServices();

    private final ContentRepository<ClientContentSnapshot> content =
            new ContentRepository<>(ClientContentSnapshot.EMPTY);
    private final DialogueAudioManager audio = new DialogueAudioManager(new MinecraftAudioBackend(),
            ClientConfig::audio, () -> System.nanoTime() / 1_000_000L, message -> MaiMaiDialogue.LOGGER.warn(message));
    private final ClientDialogueController dialogues =
            new ClientDialogueController(content::current, DialogueFragment::new, audio);

    private ClientServices() {
    }

    public static ClientServices get() {
        return INSTANCE;
    }

    public ContentRepository<ClientContentSnapshot> content() {
        return content;
    }

    public ClientDialogueController dialogues() {
        return dialogues;
    }

    public DialogueAudioManager audio() { return audio; }
}
