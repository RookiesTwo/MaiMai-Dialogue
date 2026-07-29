package top.rookiestwo.maimai_dialogue.client;

import icyllis.arc3d.core.SamplingOptions;
import icyllis.arc3d.sketch.Canvas;
import icyllis.arc3d.sketch.Image;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.effects.ColorMatrixColorFilter;
import top.rookiestwo.maimai_dialogue.dialogue.ColorAdjustFilter;

interface SceneCompositeEffect extends AutoCloseable {
    void draw(Canvas canvas, Image scene, int width, int height);

    @Override
    void close();

    final class ColorAdjust implements SceneCompositeEffect {
        private final Paint paint = new Paint();

        ColorAdjust(ColorAdjustFilter filter) {
            paint.setColorFilter(new ColorMatrixColorFilter(
                    ColorAdjustMatrix.create(filter)
            ));
        }

        @Override
        public void draw(
                Canvas canvas,
                Image scene,
                int width,
                int height
        ) {
            canvas.drawImage(
                    scene,
                    0.0F,
                    0.0F,
                    SamplingOptions.LINEAR,
                    paint
            );
        }

        @Override
        public void close() {
            paint.close();
        }
    }
}
