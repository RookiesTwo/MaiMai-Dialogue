package top.rookiestwo.maimai_dialogue_editor.client.ui.workbench;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.PropertySectionState;

import java.util.HashSet;
import java.util.Set;
import top.rookiestwo.maimai_dialogue_editor.project.EditorSessionState;

// 保存用户偏好，临时折叠与窗口钳制不回写这些值。
final class EditorLayoutState implements PropertySectionState {
    double leftFraction = 0.20;
    double rightFraction = 0.24;
    double actionsDp = 160;
    boolean leftCollapsed;
    boolean rightCollapsed;
    private final Set<String> collapsedPropertySections = new HashSet<>();
    Runnable changed = () -> {};

    @Override public boolean collapsed(String key) { return collapsedPropertySections.contains(key); }
    @Override public void collapsed(String key, boolean value) {
        if (value) collapsedPropertySections.add(key); else collapsedPropertySections.remove(key);
    }
    @Override public void expandPrefix(String prefix) { collapsedPropertySections.removeIf(key -> key.startsWith(prefix)); }
    @Override public void changed() { changed.run(); }

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
