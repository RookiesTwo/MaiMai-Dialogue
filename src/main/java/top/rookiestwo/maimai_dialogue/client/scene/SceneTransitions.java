package top.rookiestwo.maimai_dialogue.client.scene;

import top.rookiestwo.maimai_dialogue.presentation.action.ActionCall;
import top.rookiestwo.maimai_dialogue.presentation.action.ActionDefinition;
import top.rookiestwo.maimai_dialogue.presentation.action.ActionEasing;
import top.rookiestwo.maimai_dialogue.presentation.action.NumericKeyframe;
import top.rookiestwo.maimai_dialogue.presentation.action.NumericTrack;
import top.rookiestwo.maimai_dialogue.presentation.action.PresentationAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SceneTransitions {
    private static final ActionCall DEFAULT_FADE_IN = new ActionCall(
            "dialogue",
            0,
            new ActionDefinition.Inline(new PresentationAction(
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

    public static List<ActionCall> withDefaultFadeIn(
            List<ActionCall> actions
    ) {
        if (actions.stream().anyMatch(
                call -> call.target().equals("dialogue")
        )) {
            return actions;
        }
        List<ActionCall> withFade =
                new ArrayList<>(actions.size() + 1);
        withFade.add(DEFAULT_FADE_IN);
        withFade.addAll(actions);
        return List.copyOf(withFade);
    }
}
