package top.rookiestwo.maimai_dialogue.client.controller;

import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;

// 屏幕实例的身份同时用于拒绝旧页面的销毁通知。
public interface DialogueScreenHandle {
    // render 接收 client thread 状态，具体实现负责投递到自己的 UI 队列。
    void render(DialogueScreenState state);
    void show();
    void close();
}
