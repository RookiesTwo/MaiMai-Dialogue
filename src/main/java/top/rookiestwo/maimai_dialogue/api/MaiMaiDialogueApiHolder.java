package top.rookiestwo.maimai_dialogue.api;

import top.rookiestwo.maimai_dialogue.progress.ProgressServices;
import top.rookiestwo.maimai_dialogue.server.DefaultDialogueService;

final class MaiMaiDialogueApiHolder {
    static final MaiMaiDialogueApi INSTANCE = new MaiMaiDialogueApi() {
        @Override
        public DialogueService dialogues() {
            return DefaultDialogueService.INSTANCE;
        }

        @Override
        public top.rookiestwo.maimai_dialogue.api.progress.PlayerProgressService
                progress() {
            return ProgressServices.repository();
        }
    };

    private MaiMaiDialogueApiHolder() {
    }
}
