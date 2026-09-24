package top.rookiestwo.maimai_dialogue_editor.client.ui.controls;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// 保存输入期间的正文和校验结果；只在有效目标失焦时提交，不依赖 View。
public final class EditorTextBinding {
    private final Supplier<String> value;
    private final BooleanSupplier accepts;
    private final Function<String, String> commit;
    private final Runnable endEdit;
    private Consumer<String> changed = ignored -> {};
    private Runnable blurred = () -> {};
    private String text;
    private String error = "";
    private boolean focused;
    private boolean commitUnchanged;
    private boolean keepErrorOnFocus;

    public EditorTextBinding(Supplier<String> value, BooleanSupplier accepts,
                      Function<String, String> commit, Runnable endEdit) {
        this.value = value;
        this.accepts = accepts;
        this.commit = commit;
        this.endEdit = endEdit;
        text = value.get();
    }

    public static EditorTextBinding plain(Supplier<String> value, BooleanSupplier accepts,
                                   Consumer<String> commit, Runnable endEdit) {
        return new EditorTextBinding(value, accepts, text -> { commit.accept(text); return ""; }, endEdit);
    }

    public EditorTextBinding commitUnchanged() { commitUnchanged = true; return this; }
    public EditorTextBinding keepErrorOnFocus() { keepErrorOnFocus = true; return this; }
    public EditorTextBinding onChange(Consumer<String> listener) { changed = listener; return this; }
    public EditorTextBinding onBlur(Runnable listener) { blurred = listener; return this; }
    String text() { return text; }
    String error() { return error; }

    void input(String next) {
        text = next;
        if (accepts.getAsBoolean()) changed.accept(next);
    }

    void focus(boolean focused) {
        this.focused = focused;
        if (focused) {
            if (!keepErrorOnFocus) error = "";
            return;
        }
        if (accepts.getAsBoolean()) {
            error = "";
            if (commitUnchanged || !text.equals(value.get())) error = commit.apply(text);
            endEdit.run();
        }
        // 旧目标失效时仍释放该输入自己的暂存，不允许提交到新选择。
        blurred.run();
    }

    void refresh() {
        if (!focused && error.isEmpty()) text = value.get();
    }

    // 复用同一输入框切换编辑目标时，显式清除旧目标的正文和错误。
    void reset() { error = ""; text = value.get(); }
}
