package top.rookiestwo.maimai_dialogue.client.ui.screen;

import top.rookiestwo.maimai_dialogue.client.controller.DialogueUiActions;
import top.rookiestwo.maimai_dialogue.client.session.PlaybackPhase;

import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;

import javax.annotation.Nullable;

final class FastForwardPlayback {
    private final java.util.function.Supplier<DialogueScreenState> state;
    private final DialogueUiActions actions;
    private long autoAdvancePlaybackToken = Long.MIN_VALUE;
    private boolean fastForwarding;

    FastForwardPlayback(java.util.function.Supplier<DialogueScreenState> state, DialogueUiActions actions) {
        this.state = state;
        this.actions = actions;
    }

    void setEnabled(boolean enabled) {
        fastForwarding = enabled;
        if (!enabled) {
            autoAdvancePlaybackToken = Long.MIN_VALUE;
        }
    }

    void schedule(
            @Nullable DialogueScreenState state
    ) {
        long playbackToken = state == null
                ? Long.MIN_VALUE
                : state.scenePlayback()
                .map(ScenePlayback::token)
                .orElse(Long.MIN_VALUE);
        if (state == null
                || !shouldAutoAdvance(
                fastForwarding,
                state.playbackPhase(),
                state.canSkipToEnd()
        )
                || playbackToken == Long.MIN_VALUE
                || autoAdvancePlaybackToken == playbackToken) {
            return;
        }
        autoAdvancePlaybackToken = playbackToken;
        DialogueUiDispatch.toClient(() -> {
            DialogueScreenState current = this.state.get();
            if (current == null
                    || current.scenePlayback()
                    .map(ScenePlayback::token)
                    .orElse(Long.MIN_VALUE) != playbackToken
                    || !shouldAutoAdvance(
                    fastForwarding,
                    current.playbackPhase(),
                    current.canSkipToEnd()
            )) {
                return;
            }
            actions.advance();
        });
    }

    static boolean shouldAutoAdvance(
            boolean fastForwarding,
            PlaybackPhase phase,
            boolean canSkipToEnd
    ) {
        return fastForwarding
                && phase == PlaybackPhase.READY
                && canSkipToEnd;
    }
}
