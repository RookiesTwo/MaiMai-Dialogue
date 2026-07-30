package top.rookiestwo.maimai_dialogue.client;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public record HistoryEntry(
        Type type,
        Optional<String> speaker,
        String content
) {
    public HistoryEntry {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(content, "content");
    }

    public static HistoryEntry dialogue(
            @Nullable String speaker,
            String markdown
    ) {
        return new HistoryEntry(
                Type.DIALOGUE,
                Optional.ofNullable(speaker),
                markdown
        );
    }

    public static HistoryEntry option(String text) {
        return new HistoryEntry(Type.OPTION, Optional.empty(), text);
    }

    public enum Type {
        DIALOGUE,
        OPTION
    }
}
