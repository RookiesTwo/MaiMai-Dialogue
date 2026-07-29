package top.rookiestwo.maimai_dialogue.dialogue.resource;

import java.util.Objects;

public final class DialogueSnapshots {
    private static volatile DialogueSnapshot server = DialogueSnapshot.EMPTY;
    private static volatile DialogueSnapshot client = DialogueSnapshot.EMPTY;

    private DialogueSnapshots() {
    }

    public static DialogueSnapshot server() {
        return server;
    }

    public static DialogueSnapshot client() {
        return client;
    }

    public static void replaceServer(DialogueSnapshot snapshot) {
        server = Objects.requireNonNull(snapshot, "snapshot");
    }

    public static void replaceClient(DialogueSnapshot snapshot) {
        client = Objects.requireNonNull(snapshot, "snapshot");
    }
}
