package top.rookiestwo.maimai_dialogue_editor.client.ui;

// 仅在当前 Fragment 内保留用户偏好，临时折叠与窗口钳制不回写这些值。
final class EditorLayoutState {
    double leftFraction = 0.20;
    double rightFraction = 0.24;
    double stepsDp = 96;
    double actionsDp = 160;
    boolean leftCollapsed;
    boolean rightCollapsed;

    void reset() {
        leftFraction = 0.20;
        rightFraction = 0.24;
        stepsDp = 96;
        actionsDp = 160;
        leftCollapsed = false;
        rightCollapsed = false;
    }
}
