package top.rookiestwo.maimai_dialogue.network;

import java.util.Arrays;

public enum OptionCommandStatus {
    EXECUTED(0),
    SOURCE_DIALOGUE_NOT_FOUND(1),
    SOURCE_REQUIREMENTS_NOT_MET(2),
    TARGET_DIALOGUE_NOT_FOUND(3),
    TARGET_REQUIREMENTS_NOT_MET(4),
    PROGRESS_UNAVAILABLE(5),
    INVALID_OPTION(6),
    COMMAND_FAILED(7),
    INTERNAL_ERROR(8);

    private final int networkId;

    OptionCommandStatus(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public static OptionCommandStatus fromNetworkId(int networkId) {
        return Arrays.stream(values())
                .filter(status -> status.networkId == networkId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown option command status ID: " + networkId
                ));
    }
}
