package top.rookiestwo.maimai_dialogue.client.session;

import top.rookiestwo.maimai_dialogue.dialogue.DialogueStep;

import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.dialogue.branch.DialogueOption;
import top.rookiestwo.maimai_dialogue.presentation.Presentation;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DialogueScreenState(
        long generation,
        Optional<Presentation> presentation,
        Optional<ThemeDefinition> theme,
        Optional<ScenePlayback> scenePlayback,
        PlaybackPhase playbackPhase,
        boolean playbackSkipped,
        Optional<String> skipSummary,
        boolean canSkipToEnd,
        boolean mustComplete,
        int typewriterIntervalMs,
        Optional<String> speaker,
        Optional<String> text,
        Optional<SessionMessage> error,
        List<DialogueHistoryEntry> history,
        List<DialogueOption> options,
        boolean loadingOptions,
        boolean requestingTarget
) {
    public static DialogueScreenState empty(long generation) {
        return new DialogueScreenState(
                generation, Optional.empty(), Optional.empty(), Optional.empty(),
                PlaybackPhase.READY, false, Optional.empty(), false, false,
                DialogueStep.DEFAULT_TYPEWRITER_INTERVAL_MS,
                Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(), List.of(), false, false
        );
    }

    public DialogueScreenState {
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(scenePlayback, "scenePlayback");
        Objects.requireNonNull(playbackPhase, "playbackPhase");
        Objects.requireNonNull(skipSummary, "skipSummary");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(error, "error");
        history = List.copyOf(history);
        options = List.copyOf(options);
    }
}
