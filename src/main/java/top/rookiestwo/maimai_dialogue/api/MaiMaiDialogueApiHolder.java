package top.rookiestwo.maimai_dialogue.api;

import top.rookiestwo.maimai_dialogue.internal.bootstrap.CommonServices;

final class MaiMaiDialogueApiHolder {
    static final MaiMaiDialogueApi INSTANCE = new MaiMaiDialogueApi() {
        @Override
        public DialogueService dialogues() {
            return CommonServices.get().dialogues();
        }

        @Override
        public top.rookiestwo.maimai_dialogue.api.progress.PlayerProgressService
                progress() {
            return CommonServices.get().progress();
        }
    };

    private MaiMaiDialogueApiHolder() {
    }
}
