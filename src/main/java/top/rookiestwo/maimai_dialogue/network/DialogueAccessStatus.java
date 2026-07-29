package top.rookiestwo.maimai_dialogue.network;

import java.util.Arrays;

public enum DialogueAccessStatus {
    ALLOWED(0),
    DIALOGUE_NOT_FOUND(1),
    REQUIREMENTS_NOT_MET(2),
    PROGRESS_UNAVAILABLE(3),
    INTERNAL_ERROR(4);

    private final int networkId;

    DialogueAccessStatus(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public static DialogueAccessStatus fromNetworkId(int networkId) {
        return Arrays.stream(values())
                .filter(status -> status.networkId == networkId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown dialogue access status ID: " + networkId
                ));
    }
}
