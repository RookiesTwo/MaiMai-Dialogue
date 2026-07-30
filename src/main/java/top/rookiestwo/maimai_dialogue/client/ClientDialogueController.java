package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.mc.MuiModApi;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.dialogue.ContinueStep;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueOption;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueTarget;
import top.rookiestwo.maimai_dialogue.dialogue.EndStep;
import top.rookiestwo.maimai_dialogue.dialogue.HideSpeaker;
import top.rookiestwo.maimai_dialogue.dialogue.OptionTarget;
import top.rookiestwo.maimai_dialogue.dialogue.OptionsExit;
import top.rookiestwo.maimai_dialogue.dialogue.ReturnExit;
import top.rookiestwo.maimai_dialogue.dialogue.ReturnTarget;
import top.rookiestwo.maimai_dialogue.dialogue.SetSpeaker;
import top.rookiestwo.maimai_dialogue.dialogue.SpeakerOperation;
import top.rookiestwo.maimai_dialogue.dialogue.resource.DialogueSnapshots;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePreparation;
import top.rookiestwo.maimai_dialogue.client.scene.SceneRuntime;
import top.rookiestwo.maimai_dialogue.client.scene.SceneTransitions;
import top.rookiestwo.maimai_dialogue.network.DialogueAccessEntry;
import top.rookiestwo.maimai_dialogue.network.DialogueAccessStatus;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueAccessResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueRequestResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.OpenDialogueS2C;
import top.rookiestwo.maimai_dialogue.network.payload.QueryDialogueAccessC2S;
import top.rookiestwo.maimai_dialogue.network.payload.RequestDialogueC2S;
import top.rookiestwo.maimai_dialogue.presentation.action.ActionCall;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;
import top.rookiestwo.maimai_dialogue.speaker.resource.SpeakerSnapshots;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import top.rookiestwo.maimai_dialogue.theme.resource.ThemeSnapshots;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ClientDialogueController {
    public static final ClientDialogueController INSTANCE =
            new ClientDialogueController();
    private long nextRequestId = 1L;
    private long generation;
    @Nullable
    private ActiveDialogue active;
    @Nullable
    private DialogueFragment fragment;
    @Nullable
    private PendingAccessQuery pendingAccessQuery;
    @Nullable
    private PendingTargetRequest pendingTargetRequest;
    @Nullable
    private PendingRootRequest pendingRootRequest;
    @Nullable
    private List<HistoryEntry> historyEntries;

    private ClientDialogueController() {
    }

    public void handleOpen(OpenDialogueS2C payload) {
        requireClientThread();
        if (active != null) {
            MaiMaiDialogue.LOGGER.debug(
                    "Ignoring OpenDialogueS2C for {} because a dialogue UI is already open.",
                    payload.dialogueId()
            );
            return;
        }

        DialogueDefinition definition = localDefinition(payload.dialogueId());
        if (definition == null) {
            reportDevelopmentError(
                    "Client is missing dialogue " + payload.dialogueId()
            );
            return;
        }

        pendingRootRequest = null;
        activate(payload.dialogueId(), payload.dialogueId(), definition, true);
    }

    public void requestRoot(ResourceLocation dialogueId) {
        requireClientThread();
        if (active != null || pendingRootRequest != null) {
            return;
        }

        long requestId = nextRequestId();
        pendingRootRequest = new PendingRootRequest(requestId, dialogueId);
        PacketDistributor.sendToServer(new RequestDialogueC2S(
                requestId,
                dialogueId
        ));
    }

    public void handleAccessResult(DialogueAccessResultS2C payload) {
        requireClientThread();
        PendingAccessQuery pending = pendingAccessQuery;
        ActiveDialogue current = active;
        if (pending == null
                || current == null
                || pending.requestId != payload.requestId()
                || pending.generation != current.generation) {
            MaiMaiDialogue.LOGGER.debug(
                    "Ignoring stale dialogue access result {}.",
                    payload.requestId()
            );
            return;
        }

        pendingAccessQuery = null;
        Map<ResourceLocation, DialogueAccessStatus> statuses =
                payload.entries().stream().collect(Collectors.toMap(
                        DialogueAccessEntry::dialogueId,
                        DialogueAccessEntry::status,
                        (left, right) -> left
                ));

        List<DialogueOption> visible = new ArrayList<>();
        String accessError = null;
        for (DialogueOption option : pending.options) {
            OptionTarget target = option.target();
            if (target instanceof ReturnTarget) {
                visible.add(option);
            } else if (target instanceof DialogueTarget dialogueTarget) {
                DialogueAccessStatus status = statuses.get(
                        dialogueTarget.dialogue()
                );
                if (status == DialogueAccessStatus.ALLOWED) {
                    visible.add(option);
                } else if (status == DialogueAccessStatus.DIALOGUE_NOT_FOUND) {
                    reportDevelopmentError(
                            "Server is missing dialogue "
                                    + dialogueTarget.dialogue()
                    );
                } else if (status == DialogueAccessStatus.PROGRESS_UNAVAILABLE) {
                    accessError = "Progress data is unavailable. Try again later.";
                } else if (status == DialogueAccessStatus.INTERNAL_ERROR) {
                    accessError = "The server could not check dialogue access.";
                }
            }
        }
        current.visibleOptions = List.copyOf(visible);
        current.errorMessage = accessError;
        publishView();
    }

    public void handleRequestResult(DialogueRequestResultS2C payload) {
        requireClientThread();
        PendingRootRequest rootPending = pendingRootRequest;
        if (rootPending != null
                && rootPending.requestId == payload.requestId()
                && rootPending.dialogueId.equals(payload.dialogueId())) {
            pendingRootRequest = null;
            if (payload.status() != DialogueAccessStatus.ALLOWED) {
                if (payload.status() == DialogueAccessStatus.DIALOGUE_NOT_FOUND) {
                    reportDevelopmentError(
                            "Server is missing dialogue " + payload.dialogueId()
                    );
                }
                return;
            }

            DialogueDefinition definition = localDefinition(payload.dialogueId());
            if (definition == null) {
                reportDevelopmentError(
                        "Client is missing dialogue " + payload.dialogueId()
                );
                return;
            }
            activate(
                    payload.dialogueId(),
                    payload.dialogueId(),
                    definition,
                    true
            );
            return;
        }

        PendingTargetRequest pending = pendingTargetRequest;
        ActiveDialogue current = active;
        if (pending == null
                || current == null
                || pending.requestId != payload.requestId()
                || pending.generation != current.generation
                || !pending.target.equals(payload.dialogueId())) {
            MaiMaiDialogue.LOGGER.debug(
                    "Ignoring stale dialogue request result {}.",
                    payload.requestId()
            );
            return;
        }

        pendingTargetRequest = null;
        if (payload.status() != DialogueAccessStatus.ALLOWED) {
            if (payload.status() == DialogueAccessStatus.DIALOGUE_NOT_FOUND) {
                reportDevelopmentError(
                        "Server is missing dialogue " + payload.dialogueId()
                );
                current.visibleOptions = current.visibleOptions.stream()
                        .filter(option -> !(option.target() instanceof DialogueTarget target)
                                || !target.dialogue().equals(payload.dialogueId()))
                        .toList();
            }
            current.errorMessage = requestFailureMessage(payload.status());
            publishView();
            return;
        }

        DialogueDefinition definition = localDefinition(payload.dialogueId());
        if (definition == null) {
            reportDevelopmentError(
                    "Client is missing dialogue " + payload.dialogueId()
            );
            publishView();
            return;
        }
        recordOption(pending.option());
        activate(
                current.rootDialogueId,
                payload.dialogueId(),
                definition,
                false
        );
    }

    public void advance() {
        requireClientThread();
        ActiveDialogue current = active;
        if (current == null || pendingTargetRequest != null) {
            return;
        }

        if (current.playbackPhase == PlaybackPhase.PLAYING) {
            current.playbackPhase = PlaybackPhase.READY;
            current.sceneComplete = true;
            current.textComplete = true;
            current.playbackSkipped = true;
            publishView();
            return;
        }

        if (current.stepIndex < current.definition.steps().size()) {
            current.stepIndex++;
            enterCurrentStep(current);
            publishView();
            return;
        }

        if (current.definition.end().exit() instanceof ReturnExit) {
            performReturn();
            return;
        }

        if (current.definition.end().exit() instanceof OptionsExit
                && pendingAccessQuery == null
                && current.visibleOptions.isEmpty()) {
            performReturn();
        }
    }

    public void completePlayback(long completedGeneration, long playbackToken) {
        requireClientThread();
        ActiveDialogue current = active;
        if (current == null
                || current.generation != completedGeneration
                || current.playback.token() != playbackToken
                || current.sceneComplete) {
            return;
        }
        current.sceneComplete = true;
        updatePlaybackPhase(current);
        publishView();
    }

    public void completeTextPlayback(
            long completedGeneration,
            long playbackToken
    ) {
        requireClientThread();
        ActiveDialogue current = active;
        if (current == null
                || current.generation != completedGeneration
                || current.playback.token() != playbackToken
                || current.textComplete) {
            return;
        }
        current.textComplete = true;
        updatePlaybackPhase(current);
        publishView();
    }

    public void selectOption(DialogueOption option) {
        requireClientThread();
        ActiveDialogue current = active;
        if (current == null
                || pendingTargetRequest != null
                || current.stepIndex < current.definition.steps().size()
                || !current.visibleOptions.contains(option)) {
            return;
        }

        if (option.target() instanceof ReturnTarget) {
            recordOption(option);
            performReturn();
            return;
        }
        if (option.target() instanceof DialogueTarget target) {
            long requestId = nextRequestId();
            pendingTargetRequest = new PendingTargetRequest(
                    requestId,
                    current.generation,
                    target.dialogue(),
                    option
            );
            current.errorMessage = null;
            PacketDistributor.sendToServer(new RequestDialogueC2S(
                    requestId,
                    target.dialogue()
            ));
            publishView();
        }
    }

    public void onFragmentDestroyed(DialogueFragment destroyedFragment) {
        requireClientThread();
        if (fragment != destroyedFragment) {
            return;
        }
        fragment = null;
        active = null;
        pendingAccessQuery = null;
        pendingTargetRequest = null;
        pendingRootRequest = null;
        historyEntries = null;
        generation++;
    }

    public DialogueViewState viewState() {
        ActiveDialogue current = active;
        if (current == null) {
            return new DialogueViewState(
                    generation,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    PlaybackPhase.READY,
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    List.of(),
                    List.of(),
                    false,
                    false
            );
        }

        Optional<String> text;
        if (current.stepIndex < current.definition.steps().size()) {
            text = current.definition.steps()
                    .get(current.stepIndex)
                    .text();
        } else {
            text = current.definition.end().text();
        }

        boolean atEnd = current.stepIndex >= current.definition.steps().size();
        boolean ready = current.playbackPhase == PlaybackPhase.READY;
        return new DialogueViewState(
                current.generation,
                Optional.of(current.definition.presentation()),
                Optional.of(current.theme),
                Optional.of(current.playback),
                current.playbackPhase,
                current.playbackSkipped,
                Optional.ofNullable(current.speakerName),
                text,
                Optional.ofNullable(current.errorMessage),
                historyEntries != null
                        ? List.copyOf(historyEntries)
                        : List.of(),
                atEnd && ready ? current.visibleOptions : List.of(),
                atEnd && ready && pendingAccessQuery != null,
                pendingTargetRequest != null
        );
    }

    private void activate(
            ResourceLocation rootDialogueId,
            ResourceLocation currentDialogueId,
            DialogueDefinition definition,
            boolean openScreen
    ) {
        generation++;
        pendingAccessQuery = null;
        pendingTargetRequest = null;
        if (openScreen) {
            historyEntries = new ArrayList<>();
        }

        ActiveDialogue next = new ActiveDialogue(
                generation,
                rootDialogueId,
                currentDialogueId,
                definition
        );
        active = next;
        enterCurrentStep(next);
        prefetchOptions(next);

        if (openScreen) {
            DialogueFragment newFragment = new DialogueFragment(this);
            fragment = newFragment;
            var screen = MuiModApi.get().createScreen(
                    newFragment,
                    newFragment,
                    null,
                    "MaiMai Dialogue"
            );
            Minecraft.getInstance().setScreen(screen);
        } else {
            publishView();
        }
    }

    private void prefetchOptions(ActiveDialogue current) {
        if (!(current.definition.end().exit() instanceof OptionsExit optionsExit)) {
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
        PacketDistributor.sendToServer(new QueryDialogueAccessC2S(
                requestId,
                targets
        ));
    }

    private void performReturn() {
        ActiveDialogue current = active;
        if (current == null) {
            return;
        }
        if (current.currentDialogueId.equals(current.rootDialogueId)) {
            Minecraft.getInstance().setScreen(null);
            return;
        }

        DialogueDefinition root = localDefinition(current.rootDialogueId);
        if (root == null) {
            reportDevelopmentError(
                    "Client is missing root dialogue "
                            + current.rootDialogueId
            );
            return;
        }
        activate(
                current.rootDialogueId,
                current.rootDialogueId,
                root,
                false
        );
    }

    private void applySpeaker(Optional<SpeakerOperation> operation) {
        ActiveDialogue current = active;
        if (current == null || operation.isEmpty()) {
            return;
        }
        SpeakerOperation speakerOperation = operation.orElseThrow();
        if (speakerOperation instanceof SetSpeaker setSpeaker) {
            current.speakerName = SpeakerSnapshots.client()
                    .find(setSpeaker.id())
                    .map(SpeakerDefinition::name)
                    .orElseGet(() -> {
                        reportDevelopmentError(
                                "Client is missing speaker "
                                        + setSpeaker.id()
                        );
                        return setSpeaker.id().toString();
                    });
        } else if (speakerOperation instanceof HideSpeaker) {
            current.speakerName = null;
        }
    }

    private void enterCurrentStep(ActiveDialogue current) {
        applySpeaker(current.currentStepSpeaker());
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
        for (String error : preparation.errors()) {
            reportDevelopmentError(error);
        }
        List<HistoryEntry> entries = historyEntries;
        if (entries != null) {
            current.currentStepText()
                    .filter(text -> !text.isEmpty())
                    .ifPresent(text ->
                    entries.add(HistoryEntry.dialogue(
                            current.speakerName,
                            text
                    ))
            );
        }
    }

    private void publishView() {
        DialogueFragment currentFragment = fragment;
        if (currentFragment != null) {
            currentFragment.render(viewState());
        }
    }

    private static void updatePlaybackPhase(ActiveDialogue current) {
        current.playbackPhase =
                current.sceneComplete && current.textComplete
                        ? PlaybackPhase.READY
                        : PlaybackPhase.PLAYING;
    }

    private void recordOption(DialogueOption option) {
        List<HistoryEntry> entries = historyEntries;
        if (entries != null) {
            entries.add(HistoryEntry.option(option.text()));
        }
    }

    @Nullable
    private static DialogueDefinition localDefinition(ResourceLocation dialogueId) {
        return DialogueSnapshots.client().find(dialogueId).orElse(null);
    }

    static void reportDevelopmentError(String message) {
        MaiMaiDialogue.LOGGER.error(message);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal("[MaiMai Dialogue] " + message),
                    false
            );
        }
    }

    private static String requestFailureMessage(
            DialogueAccessStatus status
    ) {
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

    private long nextRequestId() {
        long requestId = nextRequestId++;
        if (nextRequestId == 0L) {
            nextRequestId = 1L;
        }
        return requestId;
    }

    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException(
                    "Dialogue controller must run on the client thread."
            );
        }
    }

    private static final class ActiveDialogue {
        private final long generation;
        private final ResourceLocation rootDialogueId;
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
                ResourceLocation rootDialogueId,
                ResourceLocation currentDialogueId,
                DialogueDefinition definition
        ) {
            this.generation = generation;
            this.rootDialogueId = rootDialogueId;
            this.currentDialogueId = currentDialogueId;
            this.definition = definition;
            this.theme = resolveTheme(definition);
            this.sceneRuntime = new SceneRuntime(
                    definition.presentation(),
                    0.0F,
                    generation << 32
            );
        }

        private Optional<SpeakerOperation> currentStepSpeaker() {
            if (stepIndex < definition.steps().size()) {
                ContinueStep step = definition.steps().get(stepIndex);
                return step.speaker();
            }
            EndStep step = definition.end();
            return step.speaker();
        }

        private List<ActionCall> currentStepActions() {
            List<ActionCall> actions;
            if (stepIndex < definition.steps().size()) {
                actions = definition.steps().get(stepIndex).actions();
            } else {
                actions = definition.end().actions();
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

        private static ThemeDefinition resolveTheme(
                DialogueDefinition definition
        ) {
            ResourceLocation themeId = definition.presentation().theme();
            return ThemeSnapshots.client().find(themeId).orElseGet(() -> {
                reportDevelopmentError(
                        "Client is missing dialogue theme " + themeId
                );
                return ThemeDefinition.DEFAULT;
            });
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

    private record PendingRootRequest(
            long requestId,
            ResourceLocation dialogueId
    ) {
    }
}
