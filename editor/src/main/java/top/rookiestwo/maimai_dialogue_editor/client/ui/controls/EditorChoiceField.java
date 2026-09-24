package top.rookiestwo.maimai_dialogue_editor.client.ui.controls;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.Button;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// 统一下拉字段的交互与刷新，提交边界和未知值显示由所属面板提供。
public final class EditorChoiceField {
    private final Button button;
    private final Supplier<String> value;
    private final Supplier<List<ChoicePresenter.Item>> items;
    private final BooleanSupplier enabled;
    private final Function<String, String> unknownLabel;

    public EditorChoiceField(Context context, ChoicePresenter choices, Supplier<String> value,
                      Supplier<List<ChoicePresenter.Item>> items, BooleanSupplier accepts,
                      BooleanSupplier enabled, Consumer<String> commit, Function<String, String> unknownLabel) {
        this.value = value;
        this.items = items;
        this.enabled = enabled;
        this.unknownLabel = unknownLabel;
        button = EditorWidgets.fieldButton(context, "", () -> {});
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        EditorWidgets.bindMetrics(button, () -> {
            int padding = button.dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP);
            button.setPadding(padding, 0, padding, 0);
        });
        button.setOnClickListener(view -> {
            if (!accepts.getAsBoolean()) return;
            choices.show(button, items.get(), value.get(), selected -> {
                // 弹出期间可能切换文档或重建面板，结果必须再次验证所属绑定。
                if (accepts.getAsBoolean()) commit.accept(selected);
            });
        });
    }

    public Button button() { return button; }

    public void refresh() {
        String selected = value.get();
        String text = items.get().stream().filter(item -> item.value().equals(selected))
                .map(ChoicePresenter.Item::label).findFirst().orElseGet(() -> unknownLabel.apply(selected));
        button.setText(text + " ▾");
        button.setTooltipText(text);
        EditorWidgets.enabled(button, enabled.getAsBoolean());
    }
}
