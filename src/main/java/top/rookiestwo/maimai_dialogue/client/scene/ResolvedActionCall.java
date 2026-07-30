package top.rookiestwo.maimai_dialogue.client.scene;

import top.rookiestwo.maimai_dialogue.presentation.action.PresentationAction;

import java.util.Objects;

public record ResolvedActionCall(
        String target,
        int delayMs,
        PresentationAction action
) {
    public ResolvedActionCall {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(action, "action");
    }

    public int endTimeMs() {
        return delayMs + action.durationMs();
    }
}
