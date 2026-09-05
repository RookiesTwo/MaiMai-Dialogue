package top.rookiestwo.maimai_dialogue.client.config.ui;

import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.SeekBar;
import net.minecraft.client.resources.language.I18n;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import static top.rookiestwo.maimai_dialogue.client.config.ui.ConfigWidgets.*;

final class NumericOptionEditor {
    private final List<Runnable> refreshers = new ArrayList<>();
    private final Runnable changed;

    NumericOptionEditor(Runnable changed) {
        this.changed = changed;
    }

    void refreshAll() {
        refreshers.forEach(Runnable::run);
    }

    void clear() {
        refreshers.clear();
    }

    void addIntegerOption(
            LinearLayout card,
            String option,
            int min,
            int max,
            int step,
            int defaultValue,
            DoubleSupplier getter,
            DoubleConsumer setter
    ) {
        addNumericOption(
                card,
                option,
                min,
                max,
                step,
                defaultValue,
                getter,
                setter,
                true
        );
    }

    void addDecimalOption(
            LinearLayout card,
            String option,
            double min,
            double max,
            double sliderStep,
            double defaultValue,
            DoubleSupplier getter,
            DoubleConsumer setter
    ) {
        addNumericOption(
                card,
                option,
                min,
                max,
                sliderStep,
                defaultValue,
                getter,
                setter,
                false
        );
    }

    private void addNumericOption(
            LinearLayout card,
            String option,
            double min,
            double max,
            double sliderStep,
            double defaultValue,
            DoubleSupplier getter,
            DoubleConsumer setter,
            boolean integer
    ) {
        Context context = card.getContext();
        LinearLayout row = createOptionRow(context, option);
        SeekBar slider = new SeekBar(context);
        int sliderMax = (int) Math.round((max - min) / sliderStep);
        slider.setMax(sliderMax);
        row.addView(slider, new LinearLayout.LayoutParams(
                row.dp(180),
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText input = new EditText(context);
        input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        input.setMinWidth(input.dp(76));
        row.addView(input, new LinearLayout.LayoutParams(
                row.dp(88),
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button reset = createOutlinedButton(context);
        reset.setText(I18n.get("gui.maimai_dialogue.config.reset"));
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        resetParams.setMargins(row.dp(12), 0, 0, 0);
        row.addView(reset, resetParams);
        card.addView(row, matchWidthWrapHeight());

        boolean[] updating = {false};
        Runnable refresh = () -> {
            double value = getter.getAsDouble();
            updating[0] = true;
            slider.setProgress((int) Math.round((value - min) / sliderStep));
            input.setText(formatNumber(value, integer));
            updating[0] = false;
        };
        Consumer<Double> commit = value -> {
            double clamped = Math.clamp(value, min, max);
            if (integer) {
                clamped = Math.rint(clamped);
            }
            setter.accept(clamped);
            ClientConfig.changed();
            refresh.run();
            changed.run();
        };
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(
                    SeekBar seekBar,
                    int progress,
                    boolean fromUser
            ) {
                if (fromUser && !updating[0]) {
                    commit.accept(min + progress * sliderStep);
                }
            }
        });
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus || updating[0]) {
                return;
            }
            try {
                commit.accept(Double.parseDouble(input.getText().toString()));
            } catch (NumberFormatException exception) {
                refresh.run();
            }
        });
        reset.setOnClickListener(view -> commit.accept(defaultValue));
        refreshers.add(refresh);
    }

    private static String formatNumber(double value, boolean integer) {
        if (integer) {
            return Integer.toString((int) Math.round(value));
        }
        return BigDecimal.valueOf(value)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
