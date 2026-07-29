package top.rookiestwo.maimai_dialogue.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Objects;
import java.util.regex.Pattern;

public record ProgressNode(String value) {
    private static final Pattern VALID_VALUE =
            Pattern.compile("[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*");

    public static final Codec<ProgressNode> CODEC = Codec.STRING.comapFlatMap(
            ProgressNode::parse,
            ProgressNode::value
    );

    public ProgressNode {
        Objects.requireNonNull(value, "value");
        if (!VALID_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid progress node: " + value);
        }
    }

    public static DataResult<ProgressNode> parse(String value) {
        if (value == null || !VALID_VALUE.matcher(value).matches()) {
            return DataResult.error(() -> "Invalid progress node '" + value
                    + "'. Expected lowercase dot-separated segments matching "
                    + "[a-z0-9_-]+.");
        }
        return DataResult.success(new ProgressNode(value));
    }

    @Override
    public String toString() {
        return value;
    }
}
