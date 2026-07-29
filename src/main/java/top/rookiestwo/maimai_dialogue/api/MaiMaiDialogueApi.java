package top.rookiestwo.maimai_dialogue.api;

import top.rookiestwo.maimai_dialogue.api.progress.PlayerProgressService;

public interface MaiMaiDialogueApi {
    static MaiMaiDialogueApi get() {
        return MaiMaiDialogueApiHolder.INSTANCE;
    }

    DialogueService dialogues();

    PlayerProgressService progress();
}
