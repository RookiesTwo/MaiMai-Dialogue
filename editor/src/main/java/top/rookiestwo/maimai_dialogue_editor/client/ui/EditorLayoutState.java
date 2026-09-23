package top.rookiestwo.maimai_dialogue_editor.client.ui;

import java.util.HashSet;
import java.util.Set;
import top.rookiestwo.maimai_dialogue_editor.project.EditorSessionState;

// 保存用户偏好，临时折叠与窗口钳制不回写这些值。
final class EditorLayoutState {
    double leftFraction = 0.20;
    double rightFraction = 0.24;
    double actionsDp = 160;
    boolean leftCollapsed;
    boolean rightCollapsed;
    final Set<String> collapsedPropertySections = new HashSet<>();
    Runnable changed = () -> {};

    EditorSessionState.Layout snapshot() {
        return new EditorSessionState.Layout(leftFraction, rightFraction, actionsDp,
                leftCollapsed, rightCollapsed, collapsedPropertySections);
    }

    void restore(EditorSessionState.Layout state) {
        leftFraction = state.leftFraction(); rightFraction = state.rightFraction(); actionsDp = state.actionsDp();
        leftCollapsed = state.leftCollapsed(); rightCollapsed = state.rightCollapsed();
        collapsedPropertySections.clear(); collapsedPropertySections.addAll(state.collapsedSections());
    }

    void reset() {
        leftFraction = 0.20;
        rightFraction = 0.24;
        actionsDp = 160;
        leftCollapsed = false;
        rightCollapsed = false;
        collapsedPropertySections.clear();
        changed.run();
    }
}
