package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.R;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.Drawable;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceTree;

/** Decorative compound drawable: selection, hover and clicks stay on the existing row label. */
final class ResourceTreeIcon extends Drawable {
    static final int SIZE_DP = 14;
    private final ResourceTree.Node node;
    private final Paint paint = new Paint();
    private int color = EditorWidgets.MUTED;

    ResourceTreeIcon(ResourceTree.Node node) { this.node = node; }

    @Override public boolean isStateful() { return true; }

    @Override protected boolean onStateChange(int[] state) {
        boolean enabled = false;
        for (int attribute : state) if (attribute == R.attr.state_enabled) enabled = true;
        int next = enabled ? EditorWidgets.MUTED : EditorWidgets.BORDER;
        if (color == next) return false;
        color = next;
        invalidateSelf();
        return true;
    }

    @Override public void draw(Canvas canvas) {
        var bounds = getBounds();
        if (bounds.isEmpty()) return;
        paint.setColor(color);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.25f);
        paint.setStrokeCap(Paint.CAP_ROUND);
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / 16f, bounds.height() / 16f);
        if (node.type() == ResourceTree.Type.PROJECT) {
            canvas.drawRect(1, 2, 15, 14, paint);
            line(canvas, 1, 6, 15, 6); line(canvas, 6, 6, 6, 14);
        } else if (node.type() == ResourceTree.Type.FOLDER) {
            line(canvas, 1, 4, 6, 4); line(canvas, 6, 4, 8, 6); line(canvas, 8, 6, 15, 6);
            line(canvas, 15, 6, 15, 14); line(canvas, 15, 14, 1, 14); line(canvas, 1, 14, 1, 4);
        } else if (node.kind() != null) {
            switch (node.kind()) {
                case DIALOGUE -> {
                    line(canvas, 1, 2, 15, 2); line(canvas, 15, 2, 15, 11);
                    line(canvas, 15, 11, 7, 11); line(canvas, 7, 11, 3, 14);
                    line(canvas, 3, 14, 3, 11); line(canvas, 3, 11, 1, 11); line(canvas, 1, 11, 1, 2);
                    line(canvas, 4, 5, 12, 5); line(canvas, 4, 8, 10, 8);
                }
                case SPEAKER -> {
                    canvas.drawCircle(8, 4.5f, 3, paint);
                    line(canvas, 2, 14, 3, 11); line(canvas, 3, 11, 6, 9);
                    line(canvas, 6, 9, 10, 9); line(canvas, 10, 9, 13, 11);
                    line(canvas, 13, 11, 14, 14); line(canvas, 14, 14, 2, 14);
                }
                case SCENE -> {
                    canvas.drawRect(1, 2, 15, 11, paint);
                    line(canvas, 8, 11, 8, 14); line(canvas, 4, 14, 12, 14);
                    line(canvas, 3, 9, 6, 6); line(canvas, 6, 6, 10, 9); line(canvas, 10, 9, 13, 5);
                }
                case VISUAL_ASSET -> {
                    line(canvas, 1, 11, 1, 1); line(canvas, 1, 1, 11, 1);
                    canvas.drawRect(4, 4, 15, 15, paint);
                    line(canvas, 6, 12, 9, 8); line(canvas, 9, 8, 13, 12);
                }
                case ACTION -> {
                    line(canvas, 1, 4, 15, 4); line(canvas, 1, 12, 15, 12);
                    line(canvas, 4, 2, 6, 4); line(canvas, 6, 4, 4, 6);
                    line(canvas, 4, 6, 2, 4); line(canvas, 2, 4, 4, 2);
                    line(canvas, 11, 10, 13, 12); line(canvas, 13, 12, 11, 14);
                    line(canvas, 11, 14, 9, 12); line(canvas, 9, 12, 11, 10);
                }
                case THEME -> {
                    line(canvas, 1, 3, 15, 3); line(canvas, 1, 8, 15, 8); line(canvas, 1, 13, 15, 13);
                    line(canvas, 5, 1, 5, 5); line(canvas, 11, 6, 11, 10); line(canvas, 7, 11, 7, 15);
                }
                case IMAGE -> {
                    canvas.drawRect(1, 2, 15, 14, paint);
                    canvas.drawCircle(11, 5.5f, 1.5f, paint);
                    line(canvas, 3, 12, 6, 7); line(canvas, 6, 7, 9, 11); line(canvas, 9, 11, 13, 9);
                }
                case SOUND -> {
                    line(canvas, 6, 12, 6, 3); line(canvas, 6, 3, 14, 1); line(canvas, 14, 1, 14, 10);
                    canvas.drawCircle(4, 12, 2, paint); canvas.drawCircle(12, 10, 2, paint);
                }
            }
        }
        canvas.restore();
    }

    private void line(Canvas canvas, float x0, float y0, float x1, float y1) {
        canvas.drawLine(x0, y0, x1, y1, paint);
    }
}
