package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.widget.Button;

/** Geometric icons centered by their visible bounds instead of font metrics. */
enum EditorButtonIcon {
    ADD(-1, 0, 1, 0, 0, -1, 0, 1),
    CLOSE(-1, -1, 1, 1, -1, 1, 1, -1),
    CHEVRON_LEFT(0.5f, -1, -0.5f, 0, -0.5f, 0, 0.5f, 1),
    CHEVRON_RIGHT(-0.5f, -1, 0.5f, 0, 0.5f, 0, -0.5f, 1),
    REFERENCE(-1, 1, 1, -1, -0.3f, -1, 1, -1, 1, -1, 1, 0.3f),
    COPY(-1, -1, 0.3f, -1, 0.3f, -1, 0.3f, 0.3f, 0.3f, 0.3f, -1, 0.3f, -1, 0.3f, -1, -1,
            -0.3f, -0.3f, 1, -0.3f, 1, -0.3f, 1, 1, 1, 1, -0.3f, 1, -0.3f, 1, -0.3f, -0.3f),
    REMOVE(-1, 0, 1, 0),
    MOVE_UP(0, 1, 0, -1, -0.75f, -0.25f, 0, -1, 0, -1, 0.75f, -0.25f),
    MOVE_DOWN(0, -1, 0, 1, -0.75f, 0.25f, 0, 1, 0, 1, 0.75f, 0.25f),
    TRIANGLE_UP {
        @Override void drawShape(Canvas canvas, Paint paint) {
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, UP_VERTICES.length, UP_VERTICES, 0,
                    null, 0, null, 0, null, 0, 0, null, paint);
        }
    },
    TRIANGLE_DOWN {
        @Override void drawShape(Canvas canvas, Paint paint) {
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, DOWN_VERTICES.length, DOWN_VERTICES, 0,
                    null, 0, null, 0, null, 0, 0, null, paint);
        }
    },
    PLAY {
        @Override void drawShape(Canvas canvas, Paint paint) {
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, PLAY_VERTICES.length, PLAY_VERTICES, 0,
                    null, 0, null, 0, null, 0, 0, null, paint);
        }
    },
    STOP {
        @Override void drawShape(Canvas canvas, Paint paint) {
            canvas.drawRect(-1, -1, 1, 1, paint);
        }
    };

    private static final float[] PLAY_VERTICES = {-1, -1, 1, 0, -1, 1};
    private static final float[] UP_VERTICES = {0, -1, 1, 1, -1, 1};
    private static final float[] DOWN_VERTICES = {-1, -1, 1, -1, 0, 1};
    private final float[] lines;

    EditorButtonIcon(float... lines) {
        this.lines = lines;
    }

    void draw(Canvas canvas, Button button, Paint paint) {
        float size = Math.min(button.dp(12), Math.min(button.getWidth(), button.getHeight()) * 0.5f);
        if (size <= 0) return;
        float half = size * 0.5f;
        // View draws content in scroll coordinates; keep the icon fixed to the visible bounds.
        float centerX = button.getScrollX() + button.getWidth() * 0.5f;
        float centerY = button.getScrollY() + button.getHeight() * 0.5f;
        paint.setColor(button.getCurrentTextColor());
        paint.setStyle(Paint.FILL);
        paint.setStrokeWidth(0.25f);
        paint.setStrokeCap(Paint.CAP_ROUND);
        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.scale(half, half);
        drawShape(canvas, paint);
        canvas.restore();
    }

    void drawShape(Canvas canvas, Paint paint) {
        for (int i = 0; i < lines.length; i += 4) {
            canvas.drawLine(lines[i], lines[i + 1], lines[i + 2], lines[i + 3], paint);
        }
    }
}
