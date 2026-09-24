package top.rookiestwo.maimai_dialogue_editor.document;

// 文本编辑与保存缓冲共用的位置描述，不依赖具体 Workspace。
public record ContentCursor(int step, int option, int variant) {
    public ContentCursor(int step, int option) { this(step, option, 0); }
}
