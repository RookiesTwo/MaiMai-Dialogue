package top.rookiestwo.maimai_dialogue_editor.client.ui.controls;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.TextView;

// 将共享文本绑定接到输入框；程序刷新不触发编辑或输入暂存。
public final class EditorTextField {
    private final EditorTextBinding binding;
    private final EditText input;
    private TextView error;
    private boolean synchronizing;

    public EditorTextField(Context context, EditorTextBinding binding) {
        this.binding = binding;
        input = EditorWidgets.compactInput(context, binding.text(), text -> {
            if (!synchronizing) binding.input(text);
        }, () -> {});
        input.setTag(EditorWidgets.DEFERRED_INPUT_TAG, Boolean.TRUE);
        input.setOnFocusChangeListener((view, focused) -> {
            binding.focus(focused);
            refreshError();
        });
    }

    public EditText input() { return input; }

    public TextView error() {
        if (error == null) {
            error = EditorWidgets.compactParagraph(input.getContext(), "");
            error.setTextColor(EditorWidgets.ERROR);
            refreshError();
        }
        return error;
    }

    public void refresh() {
        binding.refresh();
        synchronize();
    }

    public void refresh(boolean enabled) {
        refresh();
        input.setEnabled(enabled);
    }

    public void reset() {
        binding.reset();
        synchronize();
    }

    private void synchronize() {
        if (!input.getText().toString().equals(binding.text())) {
            synchronizing = true;
            try { input.setText(binding.text()); }
            finally { synchronizing = false; }
        }
        refreshError();
    }

    private void refreshError() {
        if (error == null) return;
        String message = binding.error();
        error.setText(message.isEmpty() ? "" : EditorWidgets.tr(message));
        error.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
