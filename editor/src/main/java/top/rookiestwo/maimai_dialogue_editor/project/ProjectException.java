package top.rookiestwo.maimai_dialogue_editor.project;

import java.io.IOException;

/** A localizable storage/format failure; no UI dependency in project persistence. */
public final class ProjectException extends IOException {
    private final String reason;

    public ProjectException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
