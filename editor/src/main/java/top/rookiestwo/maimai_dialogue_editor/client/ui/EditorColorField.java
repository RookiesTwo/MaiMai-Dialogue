package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Deferred hex input and an anchored color palette share the same tint value. */
final class EditorColorField extends LinearLayout {
    private final Supplier<String> value;
    private final EditText input;
    private final TextView error;
    private final EditorColorSwatch swatch;
    private boolean invalid;

    EditorColorField(Context context, Supplier<String> value, Consumer<String> setter,
                     BooleanSupplier accepts, Runnable endEdit, ChoicePresenter choices) {
        this(context, value, setter, accepts, endEdit, choices, null);
    }
    EditorColorField(Context context, Supplier<String> value, Consumer<String> setter,
                     BooleanSupplier accepts, Runnable endEdit, ChoicePresenter choices,
                     Supplier<? extends top.rookiestwo.maimai_dialogue_editor.document.EditGesture> gesture) {
        super(context);
        this.value = value;
        setOrientation(VERTICAL);
        var line = new LinearLayout(context);
        input = EditorWidgets.compactInput(context, value.get(), ignored -> {}, () -> {});
        input.setMinWidth(0); input.setMinimumWidth(0);
        input.setTag(EditorWidgets.DEFERRED_INPUT_TAG, Boolean.TRUE);
        error = EditorWidgets.compactParagraph(context, "scene.invalid_color");
        error.setTextColor(EditorWidgets.ERROR); error.setVisibility(GONE);
        input.setOnFocusChangeListener((view, focused) -> {
            if (focused) { error.setVisibility(GONE); }
            else if (accepts.getAsBoolean()) {
                String entered = input.getText().toString();
                invalid = !entered.isEmpty() && EditorColorSwatch.parse(entered) == null;
                error.setVisibility(invalid ? VISIBLE : GONE);
                if (!invalid && !entered.equals(value.get())) setter.accept(entered);
                endEdit.run();
            }
        });
        line.addView(input, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        swatch = new EditorColorSwatch(context);
        swatch.setFocusable(true);
        swatch.setTooltipText(EditorWidgets.tr("color.choose"));
        swatch.setOnClickListener(view -> {
            if (!accepts.getAsBoolean()) return;
            input.clearFocus();
            choices.showColor(swatch, value, selected -> {
                if (!accepts.getAsBoolean()) return;
                invalid = false; error.setVisibility(GONE);
                setter.accept(selected);
                input.setText(value.get());
            }, gesture == null ? null : () -> accepts.getAsBoolean() ? gesture.get() : null);
        });
        line.addView(swatch);
        EditorWidgets.bindMetrics(swatch, () -> swatch.setLayoutParams(
                new LayoutParams(dp(28), dp(EditorWidgets.COMPACT_CONTROL_DP))));
        addView(line, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(error, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        refresh(true);
    }

    void refresh(boolean enabled) {
        input.setEnabled(enabled); swatch.setEnabled(enabled);
        if (!input.isFocused() && !invalid && !input.getText().toString().equals(value.get())) input.setText(value.get());
        swatch.setValue(value.get());
    }
}
