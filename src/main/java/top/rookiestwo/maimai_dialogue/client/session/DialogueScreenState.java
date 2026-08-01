package top.rookiestwo.maimai_dialogue.client.session;

import top.rookiestwo.maimai_dialogue.client.PlaybackPhase;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueOption;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;
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
        Optional<String> speaker,
        Optional<String> text,
        Optional<String> error,
        List<DialogueHistoryEntry> history,
        List<DialogueOption> options,
        boolean loadingOptions,
        boolean requestingTarget
) {
    public DialogueScreenState {
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(scenePlayback, "scenePlayback");
        Objects.requireNonNull(playbackPhase, "playbackPhase");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(error, "error");
        history = List.copyOf(history);
        options = List.copyOf(options);
    }
}
