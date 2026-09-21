package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.SeekBar;
import icyllis.modernui.widget.TextView;
import top.rookiestwo.maimai_dialogue_editor.document.SceneWorkspace.NumberField;
import top.rookiestwo.maimai_dialogue_editor.document.SceneWorkspace.NumberDrag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/** Rounded readout, full-precision editing, and an optional quick-adjust slider. */
final class EditorNumberField extends LinearLayout {
    private final Supplier<String> value;
    private final Function<String, String> setter;
    private final BooleanSupplier accepts;
    private final Runnable endEdit;
    private final boolean integer;
    private final EditText input;
    private final TextView error;
    private final EditorSeekBar slider;
    private final Button reset;
    private final BigDecimal defaultValue;
    private final int sliderMinimum;
    private boolean updating;
    private boolean edited;
    private boolean invalid;
    private boolean tracking;
    private NumberDrag gesture;

    EditorNumberField(Context context, NumberField field, Supplier<String> value,
                      Function<String, String> setter, BooleanSupplier accepts, Runnable endEdit,
                      Supplier<NumberDrag> beginDrag) {
        super(context);
        this.value = value;
        this.setter = setter;
        this.accepts = accepts;
        this.endEdit = endEdit;
        integer = field.integer();
        defaultValue = new BigDecimal(Float.toString(field.fallback()));
        setOrientation(VERTICAL);
        var line = new LinearLayout(context);
        line.setGravity(Gravity.CENTER_VERTICAL);
        addView(line, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        input = EditorWidgets.compactInput(context, display(value.get()), this::textChanged, () -> {});
        input.setMinWidth(0);
        input.setMinimumWidth(0);
        input.setTag(EditorWidgets.DEFERRED_INPUT_TAG, Boolean.TRUE);
        input.setOnFocusChangeListener((view, focused) -> focusChanged(focused));
        error = EditorWidgets.compactParagraph(context, "");
        error.setTextColor(EditorWidgets.ERROR);
        error.setVisibility(GONE);

        boolean scale = field.name().equals("scale");
        boolean bounded = !integer && field.minimum() >= -100 && field.maximum() <= 100;
        // Scale has no upper data limit. This interval is only a convenient slider range.
        sliderMinimum = scale ? 1 : bounded
                ? Math.max(field.minimum() > 0 ? 1 : Integer.MIN_VALUE, Math.round(field.minimum() * 1000)) : 0;
        slider = scale || bounded ? new EditorSeekBar(context) : null;
        if (slider != null) {
            slider.setMax((scale ? 4000 : Math.round(field.maximum() * 1000)) - sliderMinimum);
            slider.setKeyProgressIncrement(field.maximum() - field.minimum() > 10 ? 1000 : 10);
            slider.setTooltipText(EditorWidgets.tr("scene." + field.name()));
            line.addView(slider, new LayoutParams(0, dp(EditorWidgets.COMPACT_CONTROL_DP), 1));
            EditorWidgets.bindMetrics(slider, () -> slider.setLayoutParams(
                    new LayoutParams(0, dp(EditorWidgets.COMPACT_CONTROL_DP), 1)));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onStartTrackingTouch(SeekBar seekBar) {
                    input.clearFocus();
                    endEdit.run();
                    tracking = true;
                    gesture = accepts.getAsBoolean() ? beginDrag.get() : null;
                }

                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser || updating || !accepts.getAsBoolean()) return;
                    if (!tracking) endEdit.run();
                    invalid = false;
                    edited = false;
                    error.setVisibility(GONE);
                    String next = BigDecimal.valueOf((long) sliderMinimum + progress, 3).toPlainString();
                    if (tracking) {
                        if (gesture == null || !gesture.update(next)) return;
                    } else setter.apply(next);
                    writeText(display(next));
                    syncReset(true, next);
                    if (!tracking) endEdit.run();
                }

                @Override public void onStopTrackingTouch(SeekBar seekBar) {
                    tracking = false;
                    NumberDrag finished = gesture; gesture = null;
                    if (finished != null) finished.finish(true);
                    endEdit.run();
                    writeText(display(value.get()));
                    syncSlider();
                    syncReset(input.isEnabled());
                }
            });
        }
        reset = slider == null ? null : EditorWidgets.icon(context, "↺", "reset_value", this::resetValue);
        if (reset != null) {
            line.addView(reset);
            EditorWidgets.bindMetrics(reset, () -> reset.setLayoutParams(
                    new LayoutParams(dp(EditorWidgets.COMPACT_CONTROL_DP), dp(EditorWidgets.COMPACT_CONTROL_DP))));
            syncReset(true);
        }
        line.addView(input, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(error, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        syncSlider();
    }

    private void textChanged(String text) {
        if (updating || !accepts.getAsBoolean()) return;
        edited = true;
        if (!text.isBlank()) setter.apply(text);
    }

    private void focusChanged(boolean focused) {
        if (updating) return;
        if (focused) {
            edited = invalid;
            if (!invalid) writeText(value.get());
            invalid = false;
            error.setVisibility(GONE);
        } else if (accepts.getAsBoolean()) {
            String entered = input.getText().toString();
            String issue = edited && !entered.equals(value.get()) ? setter.apply(entered) : "";
            invalid = !issue.isEmpty();
            error.setText(invalid ? EditorWidgets.tr(issue) : "");
            error.setVisibility(invalid ? VISIBLE : GONE);
            edited = false;
            endEdit.run();
            if (!invalid) writeText(display(value.get()));
            syncSlider();
            syncReset(input.isEnabled());
        }
    }

    void refresh(boolean enabled) {
        if (!enabled && slider != null) slider.cancelGesture();
        input.setEnabled(enabled);
        if (slider != null) slider.setEnabled(enabled);
        if (!tracking && !input.isFocused() && !invalid) writeText(display(value.get()));
        syncSlider();
        if (!tracking) syncReset(enabled);
    }

    private void resetValue() {
        if (!accepts.getAsBoolean()) return;
        input.clearFocus();
        slider.cancelGesture();
        if (!accepts.getAsBoolean()) return;
        endEdit.run();
        invalid = false;
        edited = false;
        error.setVisibility(GONE);
        // Removing an optional number restores its runtime default and its omission semantics.
        setter.apply("");
        endEdit.run();
        writeText(display(value.get()));
        syncSlider();
        syncReset(input.isEnabled());
    }

    private void syncReset(boolean enabled) {
        syncReset(enabled, value.get());
    }
    private void syncReset(boolean enabled, String current) {
        if (reset == null) return;
        boolean changed;
        try { changed = new BigDecimal(current.strip()).compareTo(defaultValue) != 0; }
        catch (NumberFormatException ignored) { changed = true; }
        // INVISIBLE reserves the slot between slider and input before editing and after resetting.
        reset.setVisibility(changed || invalid ? VISIBLE : INVISIBLE);
        EditorWidgets.enabled(reset, enabled && (changed || invalid));
    }

    private void writeText(String text) {
        if (input.getText().toString().equals(text)) return;
        boolean previous = updating;
        updating = true;
        try { input.setText(text); }
        finally { updating = previous; }
    }

    private String display(String raw) {
        if (integer) return raw;
        try { return new BigDecimal(raw.strip()).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
        catch (NumberFormatException | ArithmeticException ignored) { return raw; }
    }

    private void syncSlider() {
        if (slider == null || tracking) return;
        try {
            double ticks = Double.parseDouble(value.get()) * 1000 - sliderMinimum;
            if (!Double.isFinite(ticks)) return;
            // Programmatic synchronization never clamps or writes the draft's actual value.
            slider.setProgress((int) Math.round(Math.clamp(ticks, 0, slider.getMax())));
        } catch (NumberFormatException ignored) { }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        LayoutParams params = (LayoutParams) input.getLayoutParams();
        // Share the original input width with Reset, preserving the slider's available track length.
        int inputBudget = Math.min(dp(76), Math.max(0, MeasureSpec.getSize(widthSpec) / 2));
        int resetWidth = reset == null ? 0 : Math.min(dp(EditorWidgets.COMPACT_CONTROL_DP), inputBudget);
        if (reset != null) reset.getLayoutParams().width = resetWidth;
        params.width = slider == null ? LayoutParams.MATCH_PARENT : inputBudget - resetWidth;
        super.onMeasure(widthSpec, heightSpec);
    }
}
