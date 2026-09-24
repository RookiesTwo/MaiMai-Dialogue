package top.rookiestwo.maimai_dialogue_editor.client.ui.controls;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.*;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import top.rookiestwo.maimai_dialogue_editor.document.EditGesture;

/** Live HSV/alpha picker; reads are side-effect free and each gesture has an undo boundary. */
public final class EditorColorPalette extends LinearLayout {
    private final Supplier<String> value;
    private final Consumer<String> changed;
    private final Runnable endEdit;
    private final Supplier<? extends EditGesture> beginEdit;
    private EditGesture gesture;
    private final float[] hsv = {0, 0, 1};
    private int alpha = 255;
    private String current;
    private boolean updating;
    private final List<Runnable> readouts = new ArrayList<>();
    private final EditorColorSwatch sample;
    private final TextView hex;
    private final SaturationValue palette;
    private final EditorSeekBar hue;
    private final EditorSeekBar opacity;

    public EditorColorPalette(Context context, Supplier<String> value, Consumer<String> changed, Runnable endEdit, Runnable close,
                       Supplier<? extends EditGesture> beginEdit) {
        super(context); this.value = value; this.changed = changed; this.endEdit = endEdit;
        this.beginEdit = beginEdit;
        setOrientation(VERTICAL);
        EditorWidgets.bindMetrics(this, () -> setPadding(dp(6), dp(6), dp(6), dp(6)));
        var heading = new LinearLayout(context);
        sample = new EditorColorSwatch(context); heading.addView(sample);
        EditorWidgets.bindMetrics(sample, () -> sample.setLayoutParams(new LayoutParams(dp(30), dp(24))));
        hex = EditorWidgets.label(context, "color.none", 13, EditorWidgets.TEXT);
        heading.addView(hex, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        addView(heading);
        var presets = new LinearLayout(context);
        for (int color : new int[]{0xFFFFFF, 0x000000, 0xFF4040, 0xFFD740, 0x40C878, 0x40D7DF, 0x4088FF, 0xC060E0}) {
            var swatch = new EditorColorSwatch(context);
            String text = String.format(Locale.ROOT, "#%06X", color);
            swatch.setValue(text); swatch.setTooltipText(text);
            swatch.setFocusable(true);
            swatch.setOnClickListener(view -> {
                if (!isAttachedToWindow()) return;
                endEdit.run(); Color.RGBToHSV(color, hsv); publish(); endEdit.run();
            });
            presets.addView(swatch);
            EditorWidgets.bindMetrics(swatch, () -> swatch.setLayoutParams(new LayoutParams(0, dp(24), 1)));
        }
        addView(presets);
        palette = new SaturationValue(context);
        addView(palette);
        EditorWidgets.bindMetrics(palette, () -> palette.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(150))));
        hue = slider("color.hue", 3600, 0, progress -> hsv[0] = progress / 10f,
                progress -> new java.math.BigDecimal(progress).movePointLeft(1).stripTrailingZeros().toPlainString() + "°");
        opacity = slider("color.alpha", 255, 255, progress -> alpha = progress,
                progress -> Math.round(progress * 100f / 255) + "%");
        var footer = new LinearLayout(context);
        var clear = EditorWidgets.button(context, beginEdit == null ? "color.clear" : "reset_value", () -> {
            if (!isAttachedToWindow()) return;
            endEdit.run(); current = ""; hsv[0] = hsv[1] = 0; hsv[2] = 1; alpha = 255;
            updateControls(); changed.accept(""); endEdit.run();
        });
        var done = EditorWidgets.button(context, "color.done", close);
        for (Button button : new Button[]{clear, done}) {
            footer.addView(button);
            EditorWidgets.bindMetrics(button, () -> button.setLayoutParams(new LayoutParams(0, dp(24), 1)));
        }
        addView(footer);
        refresh();
    }

    private EditorSeekBar slider(String label, int max, int fallback, java.util.function.IntConsumer update,
                                java.util.function.IntFunction<String> format) {
        var row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
        var title = EditorWidgets.label(getContext(), label, 12, EditorWidgets.MUTED);
        row.addView(title);
        EditorWidgets.bindMetrics(title, () -> title.setLayoutParams(new LayoutParams(dp(60), dp(24))));
        var control = new EditorSeekBar(getContext()); control.setMax(max); control.setKeyProgressIncrement(1);
        control.setTooltipText(EditorWidgets.tr(label));
        row.addView(control);
        EditorWidgets.bindMetrics(control, () -> control.setLayoutParams(new LayoutParams(0, dp(24), 1)));
        Button reset = EditorWidgets.icon(getContext(), "↺", "reset_value", () -> {
            control.cancelGesture(); endEdit.run(); update.accept(fallback); publish(); endEdit.run();
        });
        row.addView(reset);
        EditorWidgets.bindMetrics(reset, () -> reset.setLayoutParams(new LayoutParams(dp(24), dp(24))));
        TextView readout = EditorWidgets.label(getContext(), "", 12, EditorWidgets.TEXT);
        readout.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(readout);
        EditorWidgets.bindMetrics(readout, () -> readout.setLayoutParams(new LayoutParams(dp(44), dp(24))));
        readouts.add(() -> {
            EditorWidgets.enabled(reset, control.isEnabled() && control.getProgress() != fallback);
            readout.setText(format.apply(control.getProgress()));
        });
        control.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean tracking;
            @Override public void onStartTrackingTouch(SeekBar bar) { startGesture(); tracking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) { tracking = false; finishGesture(); }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || updating) return;
                if (!tracking) endEdit.run();
                update.accept(progress); publish();
                if (!tracking) endEdit.run();
            }
        });
        addView(row);
        return control;
    }

    public void refresh() {
        if (gesture != null) return;
        String next = value.get();
        if (next.equals(current)) return;
        current = next;
        Integer color = EditorColorSwatch.parse(next);
        if (color == null) color = 0xFFFFFFFF;
        Color.RGBToHSV(color, hsv); alpha = color >>> 24;
        updateControls();
    }

    private void publish() {
        if (!isAttachedToWindow()) return;
        int color = Color.HSVToColor(hsv) | alpha << 24;
        current = alpha == 255 ? String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF)
                : String.format(Locale.ROOT, "#%08X", color);
        updateControls();
        if (gesture != null) gesture.update(current); else changed.accept(current);
    }

    private void startGesture() {
        finishGesture(); endEdit.run();
        gesture = beginEdit == null ? null : beginEdit.get();
    }
    public void finishGesture() {
        var finished = gesture; gesture = null;
        if (finished != null) finished.finish(true);
        endEdit.run(); refresh();
    }

    private void updateControls() {
        updating = true;
        try { hue.setProgress(Math.round(hsv[0] * 10)); opacity.setProgress(alpha); }
        finally { updating = false; }
        readouts.forEach(Runnable::run);
        sample.setValue(current);
        hex.setText(current.isEmpty() ? EditorWidgets.tr(beginEdit == null ? "color.none" : "theme.default_color") : current);
        palette.invalidate();
    }

    private final class SaturationValue extends View {
        private final Paint paint = new Paint();
        private boolean dragging;
        private LinearGradient saturation, brightness;
        private int shaderWidth, shaderHeight, shaderColor;

        SaturationValue(Context context) {
            super(context); setWillNotDraw(false); setClickable(true);
            setFocusable(true); setFocusableInTouchMode(true);
            setTooltipText(EditorWidgets.tr("color.saturation_value"));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth(), height = getHeight();
            if (width <= 0 || height <= 0) return;
            int color = 0xFF000000 | Color.HSVToColor(hsv[0], 1, 1);
            if (saturation == null || shaderWidth != width || shaderHeight != height || shaderColor != color) {
                shaderWidth = width; shaderHeight = height; shaderColor = color;
                saturation = new LinearGradient(0, 0, width, 0, 0xFFFFFFFF, color, Shader.TileMode.CLAMP, null);
                brightness = new LinearGradient(0, 0, 0, height, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP, null);
            }
            paint.setColor(0xFFFFFFFF); paint.setShader(saturation); canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(brightness); canvas.drawRect(0, 0, width, height, paint); paint.setShader(null);
            float x = hsv[1] * width, y = (1 - hsv[2]) * height, radius = dp(4);
            paint.setColor(0xFF000000); paint.setStrokeWidth(dp(3)); cross(canvas, x, y, radius);
            paint.setColor(0xFFFFFFFF); paint.setStrokeWidth(dp(1)); cross(canvas, x, y, radius);
        }

        private void cross(Canvas canvas, float x, float y, float radius) {
            canvas.drawLine(x - radius, y, x + radius, y, paint);
            canvas.drawLine(x, y - radius, x, y + radius, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN -> {
                    if (!event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) return false;
                    requestFocus(); dragging = true; startGesture();
                    getParent().requestDisallowInterceptTouchEvent(true); move(event); return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    if (!dragging) return false;
                    if (!event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) finish(); else move(event);
                    return true;
                }
                case MotionEvent.ACTION_UP -> { if (!dragging) return false; move(event); finish(); return true; }
                case MotionEvent.ACTION_CANCEL -> { finish(); return true; }
                default -> { return false; }
            }
        }

        private void move(MotionEvent event) {
            hsv[1] = Math.clamp(event.getX() / Math.max(1, getWidth()), 0f, 1f);
            hsv[2] = 1 - Math.clamp(event.getY() / Math.max(1, getHeight()), 0f, 1f);
            publish();
        }

        private void finish() {
            if (!dragging) return;
            dragging = false;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            finishGesture();
        }

        @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (!focused) finish(); }
        @Override protected void onDetachedFromWindow() { finish(); super.onDetachedFromWindow(); }
    }
}
