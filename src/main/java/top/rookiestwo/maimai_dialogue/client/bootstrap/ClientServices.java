package top.rookiestwo.maimai_dialogue.client.bootstrap;

import top.rookiestwo.maimai_dialogue.client.ui.screen.DialogueFragment;

import top.rookiestwo.maimai_dialogue.client.controller.ClientDialogueController;

import top.rookiestwo.maimai_dialogue.client.resource.ClientContentSnapshot;
import top.rookiestwo.maimai_dialogue.content.ContentRepository;

public final class ClientServices {
    private static final ClientServices INSTANCE = new ClientServices();

    private final ContentRepository<ClientContentSnapshot> content =
            new ContentRepository<>(ClientContentSnapshot.EMPTY);
    private final ClientDialogueController dialogues =
            new ClientDialogueController(content::current, DialogueFragment::new);

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
}
