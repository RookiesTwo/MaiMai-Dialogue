package top.rookiestwo.maimai_dialogue.client;

import org.junit.jupiter.api.Test;
import top.rookiestwo.maimai_dialogue.dialogue.ColorAdjustFilter;
import top.rookiestwo.maimai_dialogue.dialogue.SceneColor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorAdjustMatrixTest {
    @Test
    void identitySettingsProduceIdentityMatrix() {
        float[] matrix = ColorAdjustMatrix.create(new ColorAdjustFilter(
                0.0F,
                1.0F,
                1.0F,
                Optional.empty()
        ));

        assertArrayEquals(new float[]{
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1,
                0, 0, 0, 0
        }, matrix, 0.0001F);
    }

    @Test
    void brightnessContrastAndTintAffectCompositeMatrix() {
        float[] matrix = ColorAdjustMatrix.create(new ColorAdjustFilter(
                0.1F,
                1.2F,
                1.0F,
                Optional.of(new SceneColor(0x80FF8040))
        ));

        assertEquals(1.2F, matrix[0], 0.0001F);
        assertEquals(1.2F * (127.0F / 255.0F
                        + 128.0F / 255.0F * 128.0F / 255.0F),
                matrix[5], 0.0001F);
        assertEquals(1.2F * (127.0F / 255.0F
                        + 128.0F / 255.0F * 64.0F / 255.0F),
                matrix[10], 0.0001F);
        assertEquals(0.0F, matrix[16], 0.0001F);
        assertEquals(1.0F, matrix[15], 0.0001F);
    }
}
