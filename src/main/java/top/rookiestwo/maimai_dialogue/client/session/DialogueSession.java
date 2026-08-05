package top.rookiestwo.maimai_dialogue.client.session;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;
import top.rookiestwo.maimai_dialogue.client.PlaybackPhase;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePreparation;
import top.rookiestwo.maimai_dialogue.client.scene.SceneRuntime;
import top.rookiestwo.maimai_dialogue.client.scene.SceneTransitions;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueStep;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueText;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueOption;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueTarget;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueEnd;
import top.rookiestwo.maimai_dialogue.dialogue.HideSpeaker;
import top.rookiestwo.maimai_dialogue.dialogue.OptionTarget;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;
import top.rookiestwo.maimai_dialogue.dialogue.PresentationResolver;
import top.rookiestwo.maimai_dialogue.dialogue.ChoiceExit;
import top.rookiestwo.maimai_dialogue.dialogue.ReturnExit;
import top.rookiestwo.maimai_dialogue.dialogue.ReturnTarget;
import top.rookiestwo.maimai_dialogue.dialogue.SetSpeaker;
import top.rookiestwo.maimai_dialogue.dialogue.SceneResolver;
import top.rookiestwo.maimai_dialogue.dialogue.SpeakerOperation;
import top.rookiestwo.maimai_dialogue.dialogue.VisualAssetResolver;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneActionCall;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

public final class DialogueSession {
    private final DialogueContentLookup content;
    private final ResourceLocation rootDialogueId;
    private final List<DialogueHistoryEntry> history = new ArrayList<>();
    private final List<DialogueSessionEffect> initialEffects = new ArrayList<>();
    private final Map<DialogueTextKey, String> resolvedTexts = new HashMap<>();
    private final RandomGenerator random;
    private final IntSupplier defaultTypewriterIntervalMs;
    private long nextRequestId = 1L;
    private long generation;
    private ActiveDialogue active;
    @Nullable
    private PendingAccessQuery pendingAccessQuery;
    @Nullable
    private PendingTargetRequest pendingTargetRequest;
    @Nullable
    private PendingOptionCommand pendingOptionCommand;

    public DialogueSession(
            DialogueContentLookup content,
            ResourceLocation rootDialogueId,
            DialogueDefinition definition,
            long firstGeneration
    ) {
        this(
                content,
                rootDialogueId,
                definition,
                firstGeneration,
                () -> DialogueStep.DEFAULT_TYPEWRITER_INTERVAL_MS,
                RandomGenerator.getDefault()
        );
    }

    public DialogueSession(
            DialogueContentLookup content,
            ResourceLocation rootDialogueId,
            DialogueDefinition definition,
            long firstGeneration,
            IntSupplier defaultTypewriterIntervalMs
    ) {
        this(
                content,
                rootDialogueId,
                definition,
                firstGeneration,
                defaultTypewriterIntervalMs,
                RandomGenerator.getDefault()
        );
    }

    DialogueSession(
            DialogueContentLookup content,
            ResourceLocation rootDialogueId,
            DialogueDefinition definition,
            long firstGeneration,
            RandomGenerator random
    ) {
        this(
                content,
                rootDialogueId,
                definition,
                firstGeneration,
                () -> DialogueStep.DEFAULT_TYPEWRITER_INTERVAL_MS,
                random
        );
    }

    DialogueSession(
            DialogueContentLookup content,
            ResourceLocation rootDialogueId,
            DialogueDefinition definition,
            long firstGeneration,
            IntSupplier defaultTypewriterIntervalMs,
            RandomGenerator random
    ) {
        this.content = Objects.requireNonNull(content, "content");
        this.rootDialogueId = Objects.requireNonNull(
                rootDialogueId,
                "rootDialogueId"
        );
        this.random = Objects.requireNonNull(random, "random");
        this.defaultTypewriterIntervalMs = Objects.requireNonNull(
                defaultTypewriterIntervalMs,
                "defaultTypewriterIntervalMs"
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
                    accessError = I18n.get(
                            "gui.maimai_dialogue.error.progress_unavailable"
                    );
                } else if (status == DialogueAccessDecision.INTERNAL_ERROR) {
                    accessError = I18n.get(
                            "gui.maimai_dialogue.error.access_check_failed"
                    );
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

    // command 成功后才提交 Option，并继续执行其原有 target。
    public DialogueSessionUpdate handleOptionCommandResult(
            long requestId,
            ResourceLocation dialogueId,
            int optionIndex,
            OptionCommandDecision decision
    ) {
        PendingOptionCommand pending = pendingOptionCommand;
        if (pending == null
                || pending.requestId != requestId
                || pending.generation != active.generation
                || !pending.sourceDialogue.equals(dialogueId)
                || pending.optionIndex != optionIndex) {
            return update(List.of(), false);
        }
        pendingOptionCommand = null;
        List<DialogueSessionEffect> effects = new ArrayList<>();
        if (decision != OptionCommandDecision.EXECUTED) {
            active.errorMessage = commandFailureMessage(decision);
            reportCommandDevelopmentError(
                    pending,
                    decision,
                    effects
            );
            return update(effects, true);
        }

        DialogueOption option = pending.option;
        if (option.target() instanceof ReturnTarget) {
            recordOption(option);
            return performReturn();
        }
        if (option.target() instanceof DialogueTarget target) {
            DialogueDefinition definition = content.dialogue(
                    target.dialogue()
            ).orElse(null);
            if (definition == null) {
                active.visibleOptions = active.visibleOptions.stream()
                        .filter(visible -> !visible.equals(option))
                        .toList();
                active.errorMessage = I18n.get(
                        "gui.maimai_dialogue.error.dialogue_not_found"
                );
                effects.add(reportMissingClient(
                        "dialogue",
                        target.dialogue()
                ));
                return update(effects, true);
            }
            recordOption(option);
            activate(target.dialogue(), definition, effects);
            return update(effects, true);
        }
        return update(List.of(), false);
    }

    // 推进正文、跳过播放，或在结束节点执行 Return 行为。
    public DialogueSessionUpdate advance() {
        if (hasPendingOptionAction()) {
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

    // 结算当前 Dialogue 的剩余场景状态并直接完成 EndStep。
    public DialogueSessionUpdate skipToEnd() {
        if (hasPendingOptionAction()) {
            return update(List.of(), false);
        }
        int endIndex = active.definition.steps().size();
        if (active.stepIndex >= endIndex) {
            if (active.playbackPhase == PlaybackPhase.READY) {
                return update(List.of(), false);
            }
            active.sceneComplete = true;
            active.textComplete = true;
            active.playbackSkipped = true;
            updatePlaybackPhase(active);
            return update(List.of(), true);
        }

        List<DialogueSessionEffect> effects = new ArrayList<>();
        for (int index = active.stepIndex + 1;
             index < endIndex;
             index++) {
            active.stepIndex = index;
            DialogueStep step = active.definition.steps().get(index);
            applySpeaker(active, step.speaker(), effects);
            reportPreparationErrors(
                    active.sceneRuntime.prepare(step.actions()),
                    effects
            );
        }

        active.stepIndex = endIndex;
        active.resolvedText = resolveCurrentText(active);
        applySpeaker(active, active.definition.end().speaker(), effects);
        ScenePreparation endPreparation = active.sceneRuntime.prepare(
                active.definition.end().actions()
        );
        active.playback = endPreparation.playback();
        active.sceneComplete = true;
        active.textComplete = true;
        active.playbackSkipped = true;
        updatePlaybackPhase(active);
        reportPreparationErrors(endPreparation, effects);
        active.resolvedText
                .filter(text -> !text.isEmpty())
                .ifPresent(text -> history.add(DialogueHistoryEntry.dialogue(
                        active.speakerName,
                        text
                )));
        return update(effects, true);
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
        if (hasPendingOptionAction()
                || active.stepIndex < active.definition.steps().size()
                || !active.visibleOptions.contains(option)) {
            return update(List.of(), false);
        }
        if (!option.commands().isEmpty()) {
            return requestOptionCommand(option);
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
        boolean atEnd = active.stepIndex >= active.definition.steps().size();
        boolean ready = active.playbackPhase == PlaybackPhase.READY;
        return new DialogueScreenState(
                active.generation,
                Optional.of(active.presentation),
                Optional.of(active.theme),
                Optional.of(active.playback),
                active.playbackPhase,
                active.playbackSkipped,
                active.definition.skipSummary(),
                !atEnd && !hasPendingOptionAction(),
                active.currentTypewriterIntervalMs(
                        defaultTypewriterIntervalMs.getAsInt()
                ),
                Optional.ofNullable(active.speakerName),
                active.resolvedText,
                Optional.ofNullable(active.errorMessage),
                history,
                atEnd && ready ? active.visibleOptions : List.of(),
                atEnd && ready && pendingAccessQuery != null,
                hasPendingOptionAction()
        );
    }

    private void activate(
            ResourceLocation dialogueId,
            DialogueDefinition definition,
            List<DialogueSessionEffect> effects
    ) {
        pendingAccessQuery = null;
        pendingTargetRequest = null;
        pendingOptionCommand = null;
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
        current.resolvedText = resolveCurrentText(current);
        applySpeaker(current, current.currentStepSpeaker(), effects);
        ScenePreparation preparation = current.sceneRuntime.prepare(
                current.currentStepActions()
        );
        current.playback = preparation.playback();
        current.sceneComplete = current.playback.blockingDurationMs() == 0;
        current.textComplete = current.resolvedText
                .filter(text -> !text.isEmpty())
                .isEmpty();
        updatePlaybackPhase(current);
        current.playbackSkipped = false;
        reportPreparationErrors(preparation, effects);
        current.resolvedText
                .filter(text -> !text.isEmpty())
                .ifPresent(text -> history.add(DialogueHistoryEntry.dialogue(
                        current.speakerName,
                        text
                )));
    }

    private static void reportPreparationErrors(
            ScenePreparation preparation,
            List<DialogueSessionEffect> effects
    ) {
        preparation.errors().stream()
                .map(DialogueSessionEffect.ReportError::new)
                .forEach(effects::add);
    }

    // 每个 Dialogue 节点在当前 session 中只抽取一次正文。
    private Optional<String> resolveCurrentText(ActiveDialogue current) {
        Optional<DialogueText> text = current.currentStepText();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        DialogueTextKey key;
        if (current.stepIndex < current.definition.steps().size()) {
            key = new DialogueTextKey(
                    current.currentDialogueId,
                    DialogueTextNode.STEP,
                    current.stepIndex
            );
        } else {
            key = new DialogueTextKey(
                    current.currentDialogueId,
                    DialogueTextNode.END,
                    0
            );
        }
        return Optional.of(resolvedTexts.computeIfAbsent(
                key,
                ignored -> text.orElseThrow().select(random)
        ));
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
        PresentationResolver.Result resolvedPresentation =
                PresentationResolver.resolve(
                        definition.presentation(),
                        content::presentation
                );
        resolvedPresentation.errors().forEach(error -> effects.add(
                new DialogueSessionEffect.ReportError(
                        dialogueId + ": " + error
                )
        ));
        ResourceLocation themeId = resolvedPresentation.presentation()
                .theme();
        ThemeDefinition theme = content.theme(themeId).orElseGet(() -> {
            effects.add(reportMissingClient("dialogue theme", themeId));
            return ThemeDefinition.DEFAULT;
        });
        SceneResolver.Result resolvedScene = SceneResolver.resolve(
                resolvedPresentation.presentation(),
                content::scene
        );
        resolvedScene.errors().forEach(error -> effects.add(
                new DialogueSessionEffect.ReportError(
                        dialogueId + ": " + error
                )
        ));
        VisualAssetResolver.Result resolved = VisualAssetResolver.resolve(
                resolvedScene.presentation(),
                content::visualAsset
        );
        resolved.errors().forEach(error -> effects.add(
                new DialogueSessionEffect.ReportError(
                        dialogueId + ": " + error
                )
        ));
        ActiveDialogue result = new ActiveDialogue(
                generation,
                dialogueId,
                definition,
                resolved.presentation(),
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

    private DialogueSessionUpdate requestOptionCommand(DialogueOption option) {
        if (!(active.definition.end().exit() instanceof ChoiceExit exit)) {
            return update(List.of(), false);
        }
        int optionIndex = exit.options().indexOf(option);
        if (optionIndex < 0) {
            return update(List.of(), false);
        }
        if (option.target() instanceof DialogueTarget target
                && content.dialogue(target.dialogue()).isEmpty()) {
            active.errorMessage = I18n.get(
                    "gui.maimai_dialogue.error.dialogue_not_found"
            );
            return update(
                    List.of(reportMissingClient(
                            "dialogue",
                            target.dialogue()
                    )),
                    true
            );
        }

        long requestId = nextRequestId();
        pendingOptionCommand = new PendingOptionCommand(
                requestId,
                active.generation,
                active.currentDialogueId,
                optionIndex,
                option
        );
        active.errorMessage = null;
        return update(
                List.of(new DialogueSessionEffect.ExecuteOptionCommand(
                        requestId,
                        active.currentDialogueId,
                        optionIndex
                )),
                true
        );
    }

    private boolean hasPendingOptionAction() {
        return pendingTargetRequest != null || pendingOptionCommand != null;
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
            case REQUIREMENTS_NOT_MET -> I18n.get("gui.maimai_dialogue.error.requirements_not_met");
            case PROGRESS_UNAVAILABLE -> I18n.get("gui.maimai_dialogue.error.progress_unavailable");
            case INTERNAL_ERROR -> I18n.get("gui.maimai_dialogue.error.open_failed");
            case DIALOGUE_NOT_FOUND -> I18n.get("gui.maimai_dialogue.error.dialogue_not_found");
            default -> I18n.get("gui.maimai_dialogue.error.request_rejected");
        };
    }

    private static String commandFailureMessage(
            OptionCommandDecision decision
    ) {
        return switch (decision) {
            case SOURCE_REQUIREMENTS_NOT_MET, TARGET_REQUIREMENTS_NOT_MET ->
                    I18n.get("gui.maimai_dialogue.error.requirements_not_met");
            case PROGRESS_UNAVAILABLE ->
                    I18n.get("gui.maimai_dialogue.error.progress_unavailable");
            case SOURCE_DIALOGUE_NOT_FOUND, TARGET_DIALOGUE_NOT_FOUND ->
                    I18n.get("gui.maimai_dialogue.error.dialogue_not_found");
            case INVALID_OPTION ->
                    I18n.get("gui.maimai_dialogue.error.invalid_option_command");
            case COMMAND_FAILED ->
                    I18n.get("gui.maimai_dialogue.error.command_failed");
            case INTERNAL_ERROR ->
                    I18n.get("gui.maimai_dialogue.error.command_execution_failed");
            case EXECUTED -> "";
        };
    }

    private static void reportCommandDevelopmentError(
            PendingOptionCommand pending,
            OptionCommandDecision decision,
            List<DialogueSessionEffect> effects
    ) {
        if (decision == OptionCommandDecision.SOURCE_DIALOGUE_NOT_FOUND) {
            effects.add(reportMissingServer(pending.sourceDialogue));
        } else if (decision == OptionCommandDecision.TARGET_DIALOGUE_NOT_FOUND
                && pending.option.target() instanceof DialogueTarget target) {
            effects.add(reportMissingServer(target.dialogue()));
        } else if (decision == OptionCommandDecision.INVALID_OPTION) {
            effects.add(new DialogueSessionEffect.ReportError(
                    "Server rejected option command "
                            + pending.sourceDialogue
                            + "#"
                            + pending.optionIndex
            ));
        }
    }

    private static final class ActiveDialogue {
        private final long generation;
        private final ResourceLocation currentDialogueId;
        private final DialogueDefinition definition;
        private final Presentation presentation;
        private final ThemeDefinition theme;
        private final SceneRuntime sceneRuntime;
        private boolean initialStep = true;
        private int stepIndex;
        private ScenePlayback playback;
        private PlaybackPhase playbackPhase = PlaybackPhase.READY;
        private boolean playbackSkipped;
        private boolean sceneComplete = true;
        private boolean textComplete = true;
        private Optional<String> resolvedText = Optional.empty();
        @Nullable
        private String speakerName;
        @Nullable
        private String errorMessage;
        private List<DialogueOption> visibleOptions = List.of();

        private ActiveDialogue(
                long generation,
                ResourceLocation currentDialogueId,
                DialogueDefinition definition,
                Presentation presentation,
                ThemeDefinition theme,
                DialogueContentLookup content
        ) {
            this.generation = generation;
            this.currentDialogueId = currentDialogueId;
            this.definition = definition;
            this.presentation = presentation;
            this.theme = theme;
            sceneRuntime = new SceneRuntime(
                    presentation,
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

        private Optional<DialogueText> currentStepText() {
            if (stepIndex < definition.steps().size()) {
                return definition.steps().get(stepIndex).text();
            }
            return definition.end().text();
        }

        private int currentTypewriterIntervalMs(int clientDefault) {
            if (stepIndex < definition.steps().size()) {
                return definition.steps().get(stepIndex)
                        .resolveTypewriterIntervalMs(clientDefault);
            }
            return definition.end().resolveTypewriterIntervalMs(clientDefault);
        }
    }

    private enum DialogueTextNode {
        STEP,
        END
    }

    private record DialogueTextKey(
            ResourceLocation dialogueId,
            DialogueTextNode node,
            int index
    ) {
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

    private record PendingOptionCommand(
            long requestId,
            long generation,
            ResourceLocation sourceDialogue,
            int optionIndex,
            DialogueOption option
    ) {
    }
}
