package top.rookiestwo.maimai_dialogue.client.session;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import top.rookiestwo.maimai_dialogue.client.PlaybackPhase;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePreparation;
import top.rookiestwo.maimai_dialogue.client.scene.SceneRuntime;
import top.rookiestwo.maimai_dialogue.client.scene.SceneTransitions;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueStep;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueOption;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueTarget;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueEnd;
import top.rookiestwo.maimai_dialogue.dialogue.HideSpeaker;
import top.rookiestwo.maimai_dialogue.dialogue.OptionTarget;
import top.rookiestwo.maimai_dialogue.dialogue.ChoiceExit;
import top.rookiestwo.maimai_dialogue.dialogue.ReturnExit;
import top.rookiestwo.maimai_dialogue.dialogue.ReturnTarget;
import top.rookiestwo.maimai_dialogue.dialogue.SetSpeaker;
import top.rookiestwo.maimai_dialogue.dialogue.SpeakerOperation;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneActionCall;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class DialogueSession {
    private final DialogueContentLookup content;
    private final ResourceLocation rootDialogueId;
    private final List<DialogueHistoryEntry> history = new ArrayList<>();
    private final List<DialogueSessionEffect> initialEffects = new ArrayList<>();
    private long nextRequestId = 1L;
    private long generation;
    private ActiveDialogue active;
    @Nullable
    private PendingAccessQuery pendingAccessQuery;
    @Nullable
    private PendingTargetRequest pendingTargetRequest;

    public DialogueSession(
            DialogueContentLookup content,
            ResourceLocation rootDialogueId,
            DialogueDefinition definition,
            long firstGeneration
    ) {
        this.content = Objects.requireNonNull(content, "content");
        this.rootDialogueId = Objects.requireNonNull(
                rootDialogueId,
                "rootDialogueId"
        );
        generation = firstGeneration - 1;
        active = createActive(rootDialogueId, definition, initialEffects);
    }

    // 初始化根 Dialogue，并准备首个步骤及选项查询。
    public DialogueSessionUpdate start() {
        List<DialogueSessionEffect> effects = new ArrayList<>(initialEffects);
        initialEffects.clear();
        enterCurrentStep(active, effects);
        prefetchOptions(active, effects);
        return update(effects, true);
    }

    // 根据服务端访问结果筛选当前可见选项。
    public DialogueSessionUpdate handleAccessResult(
            long requestId,
            Map<ResourceLocation, DialogueAccessDecision> statuses
    ) {
        PendingAccessQuery pending = pendingAccessQuery;
        if (pending == null
                || pending.requestId != requestId
                || pending.generation != active.generation) {
            return update(List.of(), false);
        }
        pendingAccessQuery = null;
        List<DialogueOption> visible = new ArrayList<>();
        List<DialogueSessionEffect> effects = new ArrayList<>();
        String accessError = null;
        for (DialogueOption option : pending.options) {
            OptionTarget target = option.target();
            if (target instanceof ReturnTarget) {
                visible.add(option);
            } else if (target instanceof DialogueTarget dialogueTarget) {
                DialogueAccessDecision status = statuses.get(
                        dialogueTarget.dialogue()
                );
                if (status == DialogueAccessDecision.ALLOWED) {
                    visible.add(option);
                } else if (status == DialogueAccessDecision.DIALOGUE_NOT_FOUND) {
                    effects.add(reportMissingServer(dialogueTarget.dialogue()));
                } else if (status == DialogueAccessDecision.PROGRESS_UNAVAILABLE) {
                    accessError = "Progress data is unavailable. Try again later.";
                } else if (status == DialogueAccessDecision.INTERNAL_ERROR) {
                    accessError = "The server could not check dialogue access.";
                }
            }
        }
        active.visibleOptions = List.copyOf(visible);
        active.errorMessage = accessError;
        return update(effects, true);
    }

    // 处理目标 Dialogue 的二次权限校验结果并完成导航。
    public DialogueSessionUpdate handleTargetResult(
            long requestId,
            ResourceLocation dialogueId,
            DialogueAccessDecision status
    ) {
        PendingTargetRequest pending = pendingTargetRequest;
        if (pending == null
                || pending.requestId != requestId
                || pending.generation != active.generation
                || !pending.target.equals(dialogueId)) {
            return update(List.of(), false);
        }
        pendingTargetRequest = null;
        List<DialogueSessionEffect> effects = new ArrayList<>();
        if (status != DialogueAccessDecision.ALLOWED) {
            if (status == DialogueAccessDecision.DIALOGUE_NOT_FOUND) {
                effects.add(reportMissingServer(dialogueId));
                active.visibleOptions = active.visibleOptions.stream()
                        .filter(option -> !(option.target()
                                instanceof DialogueTarget target)
                                || !target.dialogue().equals(dialogueId))
                        .toList();
            }
            active.errorMessage = requestFailureMessage(status);
            return update(effects, true);
        }
        DialogueDefinition definition = content.dialogue(dialogueId).orElse(null);
        if (definition == null) {
            effects.add(reportMissingClient("dialogue", dialogueId));
            return update(effects, true);
        }
        recordOption(pending.option);
        activate(dialogueId, definition, effects);
        return update(effects, true);
    }

    // 推进正文、跳过播放，或在结束节点执行 Return 行为。
    public DialogueSessionUpdate advance() {
        if (pendingTargetRequest != null) {
            return update(List.of(), false);
        }
        if (active.playbackPhase == PlaybackPhase.PLAYING) {
            active.playbackPhase = PlaybackPhase.READY;
            active.sceneComplete = true;
            active.textComplete = true;
            active.playbackSkipped = true;
            return update(List.of(), true);
        }
        if (active.stepIndex < active.definition.steps().size()) {
            active.stepIndex++;
            List<DialogueSessionEffect> effects = new ArrayList<>();
            enterCurrentStep(active, effects);
            return update(effects, true);
        }
        if (active.definition.end().exit() instanceof ReturnExit) {
            return performReturn();
        }
        if (active.definition.end().exit() instanceof ChoiceExit
                && pendingAccessQuery == null
                && active.visibleOptions.isEmpty()) {
            return performReturn();
        }
        return update(List.of(), false);
    }

    // 标记场景动画完成，并在正文也完成时解除播放锁定。
    public DialogueSessionUpdate completeScene(
            long completedGeneration,
            long playbackToken
    ) {
        if (active.generation != completedGeneration
                || active.playback.token() != playbackToken
                || active.sceneComplete) {
            return update(List.of(), false);
        }
        active.sceneComplete = true;
        updatePlaybackPhase(active);
        return update(List.of(), true);
    }

    // 标记打字机播放完成，并在场景也完成时解除播放锁定。
    public DialogueSessionUpdate completeText(
            long completedGeneration,
            long playbackToken
    ) {
        if (active.generation != completedGeneration
                || active.playback.token() != playbackToken
                || active.textComplete) {
            return update(List.of(), false);
        }
        active.textComplete = true;
        updatePlaybackPhase(active);
        return update(List.of(), true);
    }

    // 校验并执行玩家选择的当前可见选项。
    public DialogueSessionUpdate selectOption(DialogueOption option) {
        if (pendingTargetRequest != null
                || active.stepIndex < active.definition.steps().size()
                || !active.visibleOptions.contains(option)) {
            return update(List.of(), false);
        }
        if (option.target() instanceof ReturnTarget) {
            recordOption(option);
            return performReturn();
        }
        if (option.target() instanceof DialogueTarget target) {
            long requestId = nextRequestId();
            pendingTargetRequest = new PendingTargetRequest(
                    requestId,
                    active.generation,
                    target.dialogue(),
                    option
            );
            active.errorMessage = null;
            return update(
                    List.of(new DialogueSessionEffect.RequestTarget(
                            requestId,
                            target.dialogue()
                    )),
                    true
            );
        }
        return update(List.of(), false);
    }

    public DialogueScreenState screenState() {
        Optional<String> text = active.currentStepText();
        boolean atEnd = active.stepIndex >= active.definition.steps().size();
        boolean ready = active.playbackPhase == PlaybackPhase.READY;
        return new DialogueScreenState(
                active.generation,
                Optional.of(active.definition.presentation()),
                Optional.of(active.theme),
                Optional.of(active.playback),
                active.playbackPhase,
                active.playbackSkipped,
                Optional.ofNullable(active.speakerName),
                text,
                Optional.ofNullable(active.errorMessage),
                history,
                atEnd && ready ? active.visibleOptions : List.of(),
                atEnd && ready && pendingAccessQuery != null,
                pendingTargetRequest != null
        );
    }

    private void activate(
            ResourceLocation dialogueId,
            DialogueDefinition definition,
            List<DialogueSessionEffect> effects
    ) {
        pendingAccessQuery = null;
        pendingTargetRequest = null;
        active = createActive(dialogueId, definition, effects);
        enterCurrentStep(active, effects);
        prefetchOptions(active, effects);
    }

    // 预先查询所有去重后的目标 Dialogue，避免展示无权限选项。
    private void prefetchOptions(
            ActiveDialogue current,
            List<DialogueSessionEffect> effects
    ) {
        if (!(current.definition.end().exit()
                instanceof ChoiceExit optionsExit)) {
            current.visibleOptions = List.of();
            current.errorMessage = null;
            return;
        }
        List<ResourceLocation> targets = optionsExit.options().stream()
                .map(DialogueOption::target)
                .filter(DialogueTarget.class::isInstance)
                .map(DialogueTarget.class::cast)
                .map(DialogueTarget::dialogue)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
        if (targets.isEmpty()) {
            current.visibleOptions = optionsExit.options();
            return;
        }
        long requestId = nextRequestId();
        pendingAccessQuery = new PendingAccessQuery(
                requestId,
                current.generation,
                optionsExit.options()
        );
        effects.add(new DialogueSessionEffect.QueryAccess(requestId, targets));
    }

    // 根 Dialogue 的 Return 关闭界面，子 Dialogue 的 Return 回到根节点。
    private DialogueSessionUpdate performReturn() {
        if (active.currentDialogueId.equals(rootDialogueId)) {
            return update(List.of(new DialogueSessionEffect.Close()), true);
        }
        DialogueDefinition root = content.dialogue(rootDialogueId).orElse(null);
        if (root == null) {
            return update(
                    List.of(new DialogueSessionEffect.ReportError(
                            "Client is missing root dialogue " + rootDialogueId
                    )),
                    true
            );
        }
        List<DialogueSessionEffect> effects = new ArrayList<>();
        activate(rootDialogueId, root, effects);
        return update(effects, true);
    }

    // 应用当前步骤的 Speaker 变更并准备对应的场景播放。
    private void enterCurrentStep(
            ActiveDialogue current,
            List<DialogueSessionEffect> effects
    ) {
        applySpeaker(current, current.currentStepSpeaker(), effects);
        ScenePreparation preparation = current.sceneRuntime.prepare(
                current.currentStepActions()
        );
        current.playback = preparation.playback();
        current.sceneComplete = current.playback.blockingDurationMs() == 0;
        current.textComplete = current.currentStepText()
                .filter(text -> !text.isEmpty())
                .isEmpty();
        updatePlaybackPhase(current);
        current.playbackSkipped = false;
        preparation.errors().stream()
                .map(DialogueSessionEffect.ReportError::new)
                .forEach(effects::add);
        current.currentStepText()
                .filter(text -> !text.isEmpty())
                .ifPresent(text -> history.add(DialogueHistoryEntry.dialogue(
                        current.speakerName,
                        text
                )));
    }

    private void applySpeaker(
            ActiveDialogue current,
            Optional<SpeakerOperation> operation,
            List<DialogueSessionEffect> effects
    ) {
        if (operation.isEmpty()) {
            return;
        }
        if (operation.orElseThrow() instanceof SetSpeaker setSpeaker) {
            current.speakerName = content.speaker(setSpeaker.id())
                    .map(SpeakerDefinition::name)
                    .orElseGet(() -> {
                        effects.add(reportMissingClient(
                                "speaker",
                                setSpeaker.id()
                        ));
                        return setSpeaker.id().toString();
                    });
        } else if (operation.orElseThrow() instanceof HideSpeaker) {
            current.speakerName = null;
        }
    }

    private ActiveDialogue createActive(
            ResourceLocation dialogueId,
            DialogueDefinition definition,
            List<DialogueSessionEffect> effects
    ) {
        generation++;
        ResourceLocation themeId = definition.presentation().theme();
        ThemeDefinition theme = content.theme(themeId).orElseGet(() -> {
            effects.add(reportMissingClient("dialogue theme", themeId));
            return ThemeDefinition.DEFAULT;
        });
        ActiveDialogue result = new ActiveDialogue(
                generation,
                dialogueId,
                definition,
                theme,
                content
        );
        return result;
    }

    private DialogueSessionUpdate update(
            List<DialogueSessionEffect> effects,
            boolean changed
    ) {
        return new DialogueSessionUpdate(screenState(), effects, changed);
    }

    private void recordOption(DialogueOption option) {
        history.add(DialogueHistoryEntry.option(option.text()));
    }

    private long nextRequestId() {
        long requestId = nextRequestId++;
        if (nextRequestId == 0L) {
            nextRequestId = 1L;
        }
        return requestId;
    }

    private static void updatePlaybackPhase(ActiveDialogue current) {
        current.playbackPhase = current.sceneComplete && current.textComplete
                ? PlaybackPhase.READY
                : PlaybackPhase.PLAYING;
    }

    private static DialogueSessionEffect.ReportError reportMissingServer(
            ResourceLocation dialogueId
    ) {
        return new DialogueSessionEffect.ReportError(
                "Server is missing dialogue " + dialogueId
        );
    }

    private static DialogueSessionEffect.ReportError reportMissingClient(
            String type,
            ResourceLocation id
    ) {
        return new DialogueSessionEffect.ReportError(
                "Client is missing " + type + " " + id
        );
    }

    private static String requestFailureMessage(DialogueAccessDecision status) {
        return switch (status) {
            case REQUIREMENTS_NOT_MET ->
                    "This dialogue is no longer available.";
            case PROGRESS_UNAVAILABLE ->
                    "Progress data is unavailable. Try again later.";
            case INTERNAL_ERROR ->
                    "The server could not open this dialogue.";
            case DIALOGUE_NOT_FOUND ->
                    "The selected dialogue is missing on the server.";
            default -> "The dialogue request was rejected.";
        };
    }

    private static final class ActiveDialogue {
        private final long generation;
        private final ResourceLocation currentDialogueId;
        private final DialogueDefinition definition;
        private final ThemeDefinition theme;
        private final SceneRuntime sceneRuntime;
        private boolean initialStep = true;
        private int stepIndex;
        private ScenePlayback playback;
        private PlaybackPhase playbackPhase = PlaybackPhase.READY;
        private boolean playbackSkipped;
        private boolean sceneComplete = true;
        private boolean textComplete = true;
        @Nullable
        private String speakerName;
        @Nullable
        private String errorMessage;
        private List<DialogueOption> visibleOptions = List.of();

        private ActiveDialogue(
                long generation,
                ResourceLocation currentDialogueId,
                DialogueDefinition definition,
                ThemeDefinition theme,
                DialogueContentLookup content
        ) {
            this.generation = generation;
            this.currentDialogueId = currentDialogueId;
            this.definition = definition;
            this.theme = theme;
            sceneRuntime = new SceneRuntime(
                    definition.presentation(),
                    0.0F,
                    content::action,
                    generation << 32
            );
        }

        private Optional<SpeakerOperation> currentStepSpeaker() {
            if (stepIndex < definition.steps().size()) {
                return definition.steps().get(stepIndex).speaker();
            }
            return definition.end().speaker();
        }

        private List<SceneActionCall> currentStepActions() {
            List<SceneActionCall> actions;
            if (stepIndex < definition.steps().size()) {
                DialogueStep step = definition.steps().get(stepIndex);
                actions = step.actions();
            } else {
                DialogueEnd end = definition.end();
                actions = end.actions();
            }
            if (!initialStep) {
                return actions;
            }
            initialStep = false;
            return SceneTransitions.withDefaultFadeIn(actions);
        }

        private Optional<String> currentStepText() {
            if (stepIndex < definition.steps().size()) {
                return definition.steps().get(stepIndex).text();
            }
            return definition.end().text();
        }
    }

    private record PendingAccessQuery(
            long requestId,
            long generation,
            List<DialogueOption> options
    ) {
        private PendingAccessQuery {
            options = List.copyOf(options);
        }
    }

    private record PendingTargetRequest(
            long requestId,
            long generation,
            ResourceLocation target,
            DialogueOption option
    ) {
    }
}
