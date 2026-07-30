package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;
import top.rookiestwo.maimai_dialogue.dialogue.CrtFilter;

/**
 * Lightweight CRT decoration drawn inside the scene layer.
 *
 * <p>This deliberately avoids Arc3D RenderTargets and color filters. Those
 * paths are unstable in Arc3D 2026.2.0, while ordinary ModernUI drawing keeps
 * the scene responsive and still leaves the DialogueBox unaffected.
 */
final class CrtOverlayView extends View {
    private static final long FRAME_DELAY_MS = 100L;

    private final CrtFilter filter;
    private final Paint paint = new Paint();

    CrtOverlayView(Context context, CrtFilter filter) {
        super(context);
        this.filter = filter;
        setClickable(false);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        drawBloom(canvas);
        drawScanlines(canvas, width, height);
        drawShadowMask(canvas, width, height);
        drawVignette(canvas, width, height);
        drawChromaticEdges(canvas, width, height);
        drawNoise(canvas, width, height);
        drawFlicker(canvas);

        if (filter.animated()) {
            postInvalidateDelayed(FRAME_DELAY_MS);
        }
    }

    private void drawBloom(Canvas canvas) {
        float alpha = filter.bloom() * 0.035F;
        if (alpha > 0.0F) {
            canvas.drawColor(1.0F, 1.0F, 1.0F, alpha);
        }
    }

    private void drawScanlines(Canvas canvas, int width, int height) {
        float strength = filter.scanlineStrength();
        if (strength <= 0.0F) {
            return;
        }
        paint.setColor4f(0.0F, 0.0F, 0.0F, strength * 0.34F);
        for (int y = 2; y < height; y += 4) {
            canvas.drawRect(0.0F, y, width, y + 1.0F, paint);
        }
    }

    private void drawShadowMask(Canvas canvas, int width, int height) {
        float strength = filter.maskStrength();
        if (strength <= 0.0F) {
            return;
        }
        float alpha = strength * 0.1F;
        int[] colors = {0xFFFF5555, 0xFF55FF55, 0xFF5555FF};
        for (int x = 0; x < width; x += 9) {
            for (int channel = 0; channel < colors.length; channel++) {
                paint.setColor(colors[channel]);
                paint.setAlphaF(alpha);
                canvas.drawRect(
                        x + channel,
                        0.0F,
                        x + channel + 1.0F,
                        height,
                        paint
                );
            }
        }
    }

    private void drawVignette(Canvas canvas, int width, int height) {
        float strength = filter.vignette();
        if (strength <= 0.0F) {
            return;
        }
        int bands = 5;
        float bandSize = Math.min(width, height) * 0.025F;
        for (int band = 0; band < bands; band++) {
            float inset = band * bandSize;
            float alpha = strength * (bands - band) / bands * 0.12F;
            paint.setColor4f(0.0F, 0.0F, 0.0F, alpha);
            canvas.drawRect(
                    inset,
                    inset,
                    width - inset,
                    inset + bandSize,
                    paint
            );
            canvas.drawRect(
                    inset,
                    height - inset - bandSize,
                    width - inset,
                    height - inset,
                    paint
            );
            canvas.drawRect(
                    inset,
                    inset,
                    inset + bandSize,
                    height - inset,
                    paint
            );
            canvas.drawRect(
                    width - inset - bandSize,
                    inset,
                    width - inset,
                    height - inset,
                    paint
            );
        }
    }

    private void drawChromaticEdges(Canvas canvas, int width, int height) {
        float amount = filter.chromaticAberration();
        if (amount <= 0.0F) {
            return;
        }
        float edgeWidth = 1.0F + amount;
        float alpha = Math.min(0.07F, amount * 0.018F);
        paint.setColor4f(1.0F, 0.1F, 0.1F, alpha);
        canvas.drawRect(0.0F, 0.0F, edgeWidth, height, paint);
        paint.setColor4f(0.1F, 0.35F, 1.0F, alpha);
        canvas.drawRect(width - edgeWidth, 0.0F, width, height, paint);
    }

    private void drawNoise(Canvas canvas, int width, int height) {
        float strength = filter.noise();
        if (strength <= 0.0F) {
            return;
        }
        long random = System.nanoTime() ^ 0x9E3779B97F4A7C15L;
        paint.setColor4f(1.0F, 1.0F, 1.0F, strength * 0.32F);
        for (int index = 0; index < 24; index++) {
            random ^= random << 13;
            random ^= random >>> 7;
            random ^= random << 17;
            int x = Math.floorMod((int) random, width);
            int y = Math.floorMod((int) (random >>> 32), height);
            canvas.drawRect(x, y, x + 1.0F, y + 1.0F, paint);
        }
    }

    private void drawFlicker(Canvas canvas) {
        float strength = filter.flicker();
        if (strength <= 0.0F) {
            return;
        }
        double seconds = System.nanoTime() / 1_000_000_000.0;
        float wave = (float) ((Math.sin(seconds * 31.0) + 1.0) * 0.5);
        canvas.drawColor(
                1.0F,
                1.0F,
                1.0F,
                strength * wave * 0.06F
        );
    }
}
