package top.rookiestwo.maimai_dialogue.client.scene;

import top.rookiestwo.maimai_dialogue.presentation.action.SceneActionCall;
import top.rookiestwo.maimai_dialogue.presentation.action.ActionSpec;
import top.rookiestwo.maimai_dialogue.presentation.action.ActionEasing;
import top.rookiestwo.maimai_dialogue.presentation.action.NumericKeyframe;
import top.rookiestwo.maimai_dialogue.presentation.action.NumericTrack;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SceneTransitions {
    private static final SceneActionCall DEFAULT_FADE_IN = new SceneActionCall(
            "dialogue",
            0,
            new ActionSpec.Inline(new SceneAction(
                    250,
                    ActionEasing.EASE_OUT,
                    true,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(new NumericTrack(List.of(
                            new NumericKeyframe(1.0F, 1.0F)
                    ))),
                    Optional.empty(),
                    Optional.empty()
            ))
    );

    private SceneTransitions() {
    }

    public static List<SceneActionCall> withDefaultFadeIn(
            List<SceneActionCall> actions
    ) {
        if (actions.stream().anyMatch(
                call -> call.target().equals("dialogue")
        )) {
            return actions;
        }
        List<SceneActionCall> withFade =
                new ArrayList<>(actions.size() + 1);
        withFade.add(DEFAULT_FADE_IN);
        withFade.addAll(actions);
        return List.copyOf(withFade);
    }
}
