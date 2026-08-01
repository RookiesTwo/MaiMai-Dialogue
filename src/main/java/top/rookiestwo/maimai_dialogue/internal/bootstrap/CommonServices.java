package top.rookiestwo.maimai_dialogue.internal.bootstrap;

import top.rookiestwo.maimai_dialogue.content.ContentRepository;
import top.rookiestwo.maimai_dialogue.content.DefinitionRegistry;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.progress.DefaultPlayerProgressService;
import top.rookiestwo.maimai_dialogue.server.DefaultDialogueService;
import top.rookiestwo.maimai_dialogue.server.DialogueAccessService;

public final class CommonServices {
    private static final CommonServices INSTANCE = new CommonServices();

    private final ContentRepository<DefinitionRegistry<DialogueDefinition>>
            serverDialogues = new ContentRepository<>(
                    DefinitionRegistry.empty()
            );
    private final DefaultPlayerProgressService progress =
            new DefaultPlayerProgressService();
    private final DialogueAccessService dialogueAccess =
            new DialogueAccessService(serverDialogues::current, progress);
    private final DefaultDialogueService dialogues =
            new DefaultDialogueService(dialogueAccess);

    private CommonServices() {
    }

    public static CommonServices get() {
        return INSTANCE;
    }

    public ContentRepository<DefinitionRegistry<DialogueDefinition>>
            serverDialogues() {
        return serverDialogues;
    }

    public DefaultPlayerProgressService progress() {
        return progress;
    }

    public DialogueAccessService dialogueAccess() {
        return dialogueAccess;
    }

    public DefaultDialogueService dialogues() {
        return dialogues;
    }
}
