package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.widget.FrameLayout;

/** Transparency background is drawn before all scene, dialogue and corner-control children. */
final class EditorPreviewViewport extends FrameLayout {
    private static final int CELL_DP = 12;
    private final Paint paint = new Paint();

    EditorPreviewViewport(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(true);
        paint.setAntiAlias(false);
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int cell = Math.max(1, dp(CELL_DP));
        paint.setColor(0xFFFFFFFF);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setColor(0xFFE4E7EB);
        for (int row = 0, y = 0; y < height; row++, y += cell) {
            for (int x = (row & 1) * cell; x < width; x += cell * 2) {
                canvas.drawRect(x, y, Math.min(x + cell, width), Math.min(y + cell, height), paint);
            }
        }
    }
}
