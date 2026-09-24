package top.rookiestwo.maimai_dialogue_editor.client.ui.preview;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.widget.FrameLayout;

/** Transparency background is drawn before all scene, dialogue and corner-control children. */
final class EditorPreviewViewport extends FrameLayout {
    private static final int CELL_DP = 12;
    private final Paint paint = new Paint();
    private boolean imageMode;
    private int imageWidth, imageHeight;

    EditorPreviewViewport(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(true);
        paint.setAntiAlias(false);
    }

    void setImageBounds(boolean enabled, int width, int height) {
        if (imageMode == enabled && imageWidth == width && imageHeight == height) return;
        imageMode = enabled; imageWidth = width; imageHeight = height; invalidate();
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float left = 0, top = 0, right = width, bottom = height;
        if (imageMode) {
            if (imageWidth <= 0 || imageHeight <= 0) return;
            float scale = Math.min(width / (float)imageWidth, height / (float)imageHeight);
            left = (width - imageWidth * scale) / 2;
            top = (height - imageHeight * scale) / 2;
            right = width - left; bottom = height - top;
        }
        int cell = Math.max(1, dp(CELL_DP));
        paint.setColor(0xFFFFFFFF);
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setColor(0xFFE4E7EB);
        int row = 0;
        for (float y = top; y < bottom; row++, y += cell) {
            for (float x = left + (row & 1) * cell; x < right; x += cell * 2) {
                canvas.drawRect(x, y, Math.min(x + cell, right), Math.min(y + cell, bottom), paint);
            }
        }
    }
}
