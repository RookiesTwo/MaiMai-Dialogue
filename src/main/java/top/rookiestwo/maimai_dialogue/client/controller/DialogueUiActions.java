package top.rookiestwo.maimai_dialogue.client.controller;

import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;
import top.rookiestwo.maimai_dialogue.dialogue.branch.DialogueOption;

// UI 将操作投递到 client thread 后调用此接口。
public interface DialogueUiActions {
    DialogueScreenState viewState();
    void advance();
    void skipToEnd();
    void selectOption(DialogueOption option);
    void completePlayback(long generation, long playbackToken);
    void completeTextPlayback(long generation, long playbackToken);
    void closeFromUi();
    void onScreenDestroyed(DialogueScreenHandle screen);
}
