package top.rookiestwo.maimai_dialogue.presentation.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public record SceneColor(int argb) {
    public static final Codec<SceneColor> CODEC = Codec.STRING.comapFlatMap(
            SceneColor::parse,
            SceneColor::serialized
    );

    public String serialized() {
        return String.format("#%08X", argb);
    }

    private static DataResult<SceneColor> parse(String value) {
        if (!value.matches("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) {
            return DataResult.error(
                    () -> "Color must use #RRGGBB or #AARRGGBB."
            );
        }
        try {
            long decoded = Long.parseLong(value.substring(1), 16);
            if (value.length() == 7) {
                decoded |= 0xFF000000L;
            }
            return DataResult.success(new SceneColor((int) decoded));
        } catch (NumberFormatException exception) {
            return DataResult.error(() -> "Invalid color '" + value + "'.");
        }
    }
}
