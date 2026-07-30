package top.rookiestwo.maimai_dialogue.presentation.action.resource;

import java.util.Objects;

public final class ActionSnapshots {
    private static volatile ActionSnapshot client = ActionSnapshot.EMPTY;

    private ActionSnapshots() {
    }

    public static ActionSnapshot client() {
        return client;
    }

    public static void replaceClient(ActionSnapshot snapshot) {
        client = Objects.requireNonNull(snapshot, "snapshot");
    }
}
