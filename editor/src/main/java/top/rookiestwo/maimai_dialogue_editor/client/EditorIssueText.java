package top.rookiestwo.maimai_dialogue_editor.client;

import net.minecraft.client.resources.language.I18n;
import top.rookiestwo.maimai_dialogue_editor.export.ValidationIssue;

// 属性面板和导出菜单共用的校验问题文案。
public final class EditorIssueText {
    private EditorIssueText() {}

    public static String describe(ValidationIssue issue) {
        return I18n.get("gui.maimai_dialogue_editor.export.issue." + issue.reason())
                + (issue.detail().isEmpty() ? "" : "\n" + issue.detail());
    }
}
