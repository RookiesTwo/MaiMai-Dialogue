package top.rookiestwo.maimai_dialogue_editor.document;

// 临时编辑只影响预览，结束时再选择提交或放弃。
public interface EditGesture {
    boolean update(String value);
    void finish(boolean commit);
}
