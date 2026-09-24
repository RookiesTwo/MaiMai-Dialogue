package top.rookiestwo.maimai_dialogue_editor.client.preview;

import icyllis.modernui.view.View;

// 预览协调层只操作表面和刷新入口，不依赖具体 View 的布局实现。
public interface PreviewDisplay {
    View root();
    void refresh();
    void setReferenceHeight(int height);
    void finishSceneDrag(boolean commit);
    void refreshActionCanvas();
    void requestSceneFrame(boolean immediate);
    void refreshContentAfterLayout();
}
