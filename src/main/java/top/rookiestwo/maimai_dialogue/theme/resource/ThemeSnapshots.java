package top.rookiestwo.maimai_dialogue.theme.resource;

import java.util.Objects;

public final class ThemeSnapshots {
    private static volatile ThemeSnapshot client = ThemeSnapshot.EMPTY;

    private ThemeSnapshots() {
    }

    public static ThemeSnapshot client() {
        return client;
    }

    public static void replaceClient(ThemeSnapshot snapshot) {
        client = Objects.requireNonNull(snapshot, "snapshot");
    }
}
