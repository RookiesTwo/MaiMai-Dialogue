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
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

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
        return withDefaultFadeIn(actions, ignored -> Optional.empty());
    }

    public static List<SceneActionCall> withDefaultFadeIn(List<SceneActionCall> actions,
            Function<ResourceLocation, Optional<SceneAction>> lookup) {
        if (actions.stream().anyMatch(
                call -> controlsDialogue(call, lookup)
        )) {
            return actions;
        }
        List<SceneActionCall> withFade =
                new ArrayList<>(actions.size() + 1);
        withFade.add(DEFAULT_FADE_IN);
        withFade.addAll(actions);
        return List.copyOf(withFade);
    }

    private static boolean controlsDialogue(SceneActionCall call,
            Function<ResourceLocation, Optional<SceneAction>> lookup) {
        if (!call.target().equals("dialogue")) return false;
        if (call.action() instanceof ActionSpec.Inline inline) return !inline.action().audioOnly();
        if (call.action() instanceof ActionSpec.Reference reference) {
            return lookup.apply(reference.id()).map(action -> !action.audioOnly()).orElse(true);
        }
        return true;
    }
}
