package top.rookiestwo.maimai_dialogue_editor.client.ui.controls;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;
import top.rookiestwo.maimai_dialogue.presentation.filter.SceneColor;

/** Square color sample with a checkerboard for alpha and a slash for an unset tint. */
final class EditorColorSwatch extends View {
    private final Paint paint = new Paint();
    private String value = "";
    private Integer color;

    EditorColorSwatch(Context context) { super(context); setWillNotDraw(false); }

    void setValue(String value) {
        if (!this.value.equals(value)) { this.value = value; color = parse(value); invalidate(); }
    }

    static Integer parse(String value) { return SceneColor.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
            new com.google.gson.JsonPrimitive(value)).result().map(SceneColor::argb).orElse(null); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = dp(2), right = Math.max(inset, getWidth() - inset), bottom = Math.max(inset, getHeight() - inset);
        int cell = Math.max(1, dp(5));
        for (int y = (int) inset; y < bottom; y += cell) for (int x = (int) inset; x < right; x += cell) {
            paint.setColor(((x / cell + y / cell) & 1) == 0 ? EditorWidgets.PANEL : EditorWidgets.BORDER);
            canvas.drawRect(x, y, Math.min(x + cell, right), Math.min(y + cell, bottom), paint);
        }
        if (color != null) { paint.setColor(color); canvas.drawRect(inset, inset, right, bottom, paint); }
        paint.setColor(isHovered() || isFocused() ? EditorWidgets.ACCENT : EditorWidgets.MUTED);
        paint.setStrokeWidth(Math.max(1, dp(1)));
        canvas.drawLine(inset, inset, right, inset, paint); canvas.drawLine(right, inset, right, bottom, paint);
        canvas.drawLine(right, bottom, inset, bottom, paint); canvas.drawLine(inset, bottom, inset, inset, paint);
        if (color == null) canvas.drawLine(inset, bottom, right, inset, paint);
    }
}
