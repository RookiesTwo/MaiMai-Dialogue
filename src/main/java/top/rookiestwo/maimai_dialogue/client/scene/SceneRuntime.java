package top.rookiestwo.maimai_dialogue.client.scene;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneActionCall;
import top.rookiestwo.maimai_dialogue.presentation.action.ActionSpec;
import top.rookiestwo.maimai_dialogue.presentation.action.ActionProperty;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneAction;
import top.rookiestwo.maimai_dialogue.presentation.action.NumericTrack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class SceneRuntime {
    private final Function<ResourceLocation, Optional<SceneAction>>
            actionLookup;
    private SceneState current;
    private long nextPlaybackToken;

    public SceneRuntime(Presentation presentation) {
        this(presentation, 1.0F, 1L);
    }

    public SceneRuntime(
            Presentation presentation,
            float initialDialogueOpacity
    ) {
        this(presentation, initialDialogueOpacity, 1L);
    }

    public SceneRuntime(
            Presentation presentation,
            float initialDialogueOpacity,
            long firstPlaybackToken
    ) {
        this(
                presentation,
                initialDialogueOpacity,
                ignored -> Optional.empty(),
                firstPlaybackToken
        );
    }

    public SceneRuntime(
            Presentation presentation,
            float initialDialogueOpacity,
            Function<ResourceLocation, Optional<SceneAction>>
                    actionLookup
    ) {
        this(presentation, initialDialogueOpacity, actionLookup, 1L);
    }

    public SceneRuntime(
            Presentation presentation,
            float initialDialogueOpacity,
            Function<ResourceLocation, Optional<SceneAction>>
                    actionLookup,
            long firstPlaybackToken
    ) {
        this.actionLookup = Objects.requireNonNull(
                actionLookup,
                "actionLookup"
        );
        nextPlaybackToken = firstPlaybackToken;
        current = SceneState.initial(presentation, initialDialogueOpacity);
    }

    public SceneState current() {
        return current;
    }

    // 解析并校验一组 SceneActionCall，生成可播放的状态变化。
    public ScenePreparation prepare(List<SceneActionCall> actionCalls) {
        SceneState start = current;
        SceneState end;
        List<ResolvedActionCall> resolved = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, EnumSet<ActionProperty>> writes = new HashMap<>();
        int totalDuration = 0;
        int blockingDuration = 0;

        for (SceneActionCall call : actionCalls) {
            SceneAction action = resolve(call.action());
            if (action == null) {
                if (call.action() instanceof ActionSpec.Reference reference) {
                    errors.add("Client is missing SceneAction "
                            + reference.id());
                }
                continue;
            }

            EnumSet<ActionProperty> targetWrites = writes.computeIfAbsent(
                    call.target(),
                    ignored -> EnumSet.noneOf(ActionProperty.class)
            );
            EnumSet<ActionProperty> actionWrites =
                    EnumSet.noneOf(ActionProperty.class);
            for (ActionProperty property : ActionProperty.values()) {
                if (action.writes(property)) {
                    actionWrites.add(property);
                }
            }
            EnumSet<ActionProperty> conflicts =
                    EnumSet.copyOf(actionWrites);
            conflicts.retainAll(targetWrites);
            if (!conflicts.isEmpty()) {
                for (ActionProperty conflict : conflicts) {
                    errors.add("Multiple SceneActions write "
                            + call.target() + "."
                            + conflict.name().toLowerCase());
                }
                continue;
            }

            boolean validTarget;
            if (call.target().equals("dialogue")) {
                validTarget = validateDialogue(
                        start.dialogueBox(),
                        action,
                        actionWrites,
                        errors
                );
            } else if (call.target().equals("background")) {
                validTarget = validateBackground(
                        start.background().orElse(null),
                        action,
                        actionWrites,
                        errors
                );
            } else {
                SceneObjectState initial = start.find(call.target())
                        .orElse(null);
                if (initial == null) {
                    errors.add(
                            "SceneAction targets undeclared "
                                    + "VisualObject " + call.target()
                    );
                    validTarget = false;
                } else {
                    validTarget = validateFinalState(
                            initial,
                            action,
                            errors,
                            call.target()
                    );
                }
            }
            if (!validTarget) {
                continue;
            }
            targetWrites.addAll(actionWrites);

            ResolvedActionCall resolvedCall = new ResolvedActionCall(
                    call.target(),
                    call.delayMs(),
                    action
            );
            resolved.add(resolvedCall);
            totalDuration = Math.max(
                    totalDuration,
                    resolvedCall.endTimeMs()
            );
            if (action.blocking()) {
                blockingDuration = Math.max(
                        blockingDuration,
                        resolvedCall.endTimeMs()
                );
            }
        }

        ScenePlayback provisional = new ScenePlayback(
                nextPlaybackToken++,
                start,
                start,
                resolved,
                totalDuration,
                blockingDuration
        );
        end = provisional.stateAt(totalDuration);
        ScenePlayback playback = new ScenePlayback(
                provisional.token(),
                start,
                end,
                resolved,
                totalDuration,
                blockingDuration
        );
        current = end;
        return new ScenePreparation(playback, errors);
    }

    private SceneAction resolve(ActionSpec definition) {
        if (definition instanceof ActionSpec.Inline inline) {
            return inline.action();
        }
        if (definition instanceof ActionSpec.Reference reference) {
            return actionLookup.apply(reference.id()).orElse(null);
        }
        return null;
    }

    private static boolean validateFinalState(
            SceneObjectState initial,
            SceneAction action,
            List<String> errors,
            String target
    ) {
        float finalScale = initial.scale() + action.scale()
                .map(track -> track.finalValue())
                .orElse(0.0F);
        if (finalScale <= 0.0F) {
            errors.add("SceneAction leaves " + target
                    + " with non-positive scale.");
            return false;
        }
        float finalOpacity = initial.opacity() + action.opacity()
                .map(track -> track.finalValue())
                .orElse(0.0F);
        if (finalOpacity < 0.0F || finalOpacity > 1.0F) {
            errors.add("SceneAction leaves " + target
                    + " opacity outside 0..1.");
            return false;
        }
        String finalVariant = action.variant()
                .map(change -> change.variant())
                .orElse(initial.variant());
        if (!initial.variants().containsKey(finalVariant)) {
            errors.add("SceneAction selects missing variant "
                    + finalVariant + " on " + target + ".");
            return false;
        }
        return true;
    }

    private static boolean validateBackground(
            SceneBackgroundState initial,
            SceneAction action,
            EnumSet<ActionProperty> writes,
            List<String> errors
    ) {
        if (initial == null) {
            errors.add(
                    "SceneAction targets an undeclared background."
            );
            return false;
        }
        EnumSet<ActionProperty> unsupported = EnumSet.copyOf(writes);
        unsupported.remove(ActionProperty.VARIANT);
        if (!unsupported.isEmpty()) {
            errors.add(
                    "Background SceneAction only supports variant."
            );
            return false;
        }
        String finalVariant = action.variant()
                .map(change -> change.variant())
                .orElse(initial.variant());
        if (!initial.variants().containsKey(finalVariant)) {
            errors.add(
                    "SceneAction selects missing Background variant "
                            + finalVariant + "."
            );
            return false;
        }
        return true;
    }

    private static boolean validateDialogue(
            DialogueBoxState initial,
            SceneAction action,
            EnumSet<ActionProperty> writes,
            List<String> errors
    ) {
        EnumSet<ActionProperty> unsupported = EnumSet.copyOf(writes);
        unsupported.remove(ActionProperty.X);
        unsupported.remove(ActionProperty.Y);
        unsupported.remove(ActionProperty.SCALE);
        unsupported.remove(ActionProperty.OPACITY);
        if (!unsupported.isEmpty()) {
            errors.add(
                    "Dialogue SceneAction only supports x, y, scale, and opacity."
            );
            return false;
        }
        float finalScale = initial.scale() + action.scale()
                .map(NumericTrack::finalValue)
                .orElse(0.0F);
        if (finalScale <= 0.0F) {
            errors.add(
                    "SceneAction leaves Dialogue with non-positive scale."
            );
            return false;
        }
        float finalOpacity = initial.opacity() + action.opacity()
                .map(NumericTrack::finalValue)
                .orElse(0.0F);
        if (finalOpacity < 0.0F || finalOpacity > 1.0F) {
            errors.add(
                    "SceneAction leaves Dialogue opacity outside 0..1."
            );
            return false;
        }
        return true;
    }
}
