package top.rookiestwo.maimai_dialogue.client.session;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public record DialogueHistoryEntry(
        Type type,
        Optional<String> speaker,
        String content
) {
    public DialogueHistoryEntry {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(content, "content");
    }

    public static DialogueHistoryEntry dialogue(
            @Nullable String speaker,
            String markdown
    ) {
        return new DialogueHistoryEntry(
                Type.DIALOGUE,
                Optional.ofNullable(speaker),
                markdown
        );
    }

    public static DialogueHistoryEntry option(String text) {
        return new DialogueHistoryEntry(Type.OPTION, Optional.empty(), text);
    }

    public enum Type {
        DIALOGUE,
        OPTION
    }
}
