package top.rookiestwo.maimai_dialogue_editor.client.preview;

import top.rookiestwo.maimai_dialogue.client.controller.DialogueUiActions;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueImageSource;
import top.rookiestwo.maimai_dialogue.client.ui.screen.DialogueFragment;

// 共用的嵌入式 Fragment 挂载入口，生命周期检查集中在 Host。
interface PreviewMount {
    boolean active();
    boolean ready();
    PreviewDisplay display();
    DialogueFragment fragment();
    void clear();
    void show(DialogueUiActions actions, DialogueImageSource images, String tag);
}
