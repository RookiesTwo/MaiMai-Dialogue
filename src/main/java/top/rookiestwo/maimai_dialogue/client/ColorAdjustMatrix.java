package top.rookiestwo.maimai_dialogue.client;

import top.rookiestwo.maimai_dialogue.dialogue.ColorAdjustFilter;
import top.rookiestwo.maimai_dialogue.dialogue.SceneColor;

final class ColorAdjustMatrix {
    private static final float RED_LUMINANCE = 0.2126F;
    private static final float GREEN_LUMINANCE = 0.7152F;
    private static final float BLUE_LUMINANCE = 0.0722F;

    private ColorAdjustMatrix() {
    }

    static float[] create(ColorAdjustFilter filter) {
        float saturation = filter.saturation();
        float inverseSaturation = 1.0F - saturation;
        float contrast = filter.contrast();
        float offset = filter.brightness() + 0.5F * (1.0F - contrast);

        float redTint = 1.0F;
        float greenTint = 1.0F;
        float blueTint = 1.0F;
        SceneColor tint = filter.tint().orElse(null);
        if (tint != null) {
            int argb = tint.argb();
            float tintAlpha = ((argb >>> 24) & 0xFF) / 255.0F;
            redTint = tintMultiplier((argb >>> 16) & 0xFF, tintAlpha);
            greenTint = tintMultiplier((argb >>> 8) & 0xFF, tintAlpha);
            blueTint = tintMultiplier(argb & 0xFF, tintAlpha);
        }

        float[] matrix = new float[20];
        matrix[0] = redTint * contrast
                * (RED_LUMINANCE * inverseSaturation + saturation);
        matrix[4] = redTint * contrast
                * GREEN_LUMINANCE * inverseSaturation;
        matrix[8] = redTint * contrast
                * BLUE_LUMINANCE * inverseSaturation;
        matrix[16] = redTint * offset;

        matrix[1] = greenTint * contrast
                * RED_LUMINANCE * inverseSaturation;
        matrix[5] = greenTint * contrast
                * (GREEN_LUMINANCE * inverseSaturation + saturation);
        matrix[9] = greenTint * contrast
                * BLUE_LUMINANCE * inverseSaturation;
        matrix[17] = greenTint * offset;

        matrix[2] = blueTint * contrast
                * RED_LUMINANCE * inverseSaturation;
        matrix[6] = blueTint * contrast
                * GREEN_LUMINANCE * inverseSaturation;
        matrix[10] = blueTint * contrast
                * (BLUE_LUMINANCE * inverseSaturation + saturation);
        matrix[18] = blueTint * offset;

        matrix[15] = 1.0F;
        return matrix;
    }

    private static float tintMultiplier(int component, float alpha) {
        return (1.0F - alpha) + alpha * component / 255.0F;
    }
}
