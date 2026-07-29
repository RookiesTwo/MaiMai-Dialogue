package top.rookiestwo.maimai_dialogue.speaker.resource;

import java.util.Objects;

public final class SpeakerSnapshots {
    private static volatile SpeakerSnapshot client = SpeakerSnapshot.EMPTY;

    private SpeakerSnapshots() {
    }

    public static SpeakerSnapshot client() {
        return client;
    }

    public static void replaceClient(SpeakerSnapshot snapshot) {
        client = Objects.requireNonNull(snapshot, "snapshot");
    }
}
