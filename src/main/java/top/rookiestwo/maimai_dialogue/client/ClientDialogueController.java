package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.mc.MuiModApi;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.client.resource.ClientContentSnapshot;
import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;
import top.rookiestwo.maimai_dialogue.client.session.DialogueAccessDecision;
import top.rookiestwo.maimai_dialogue.client.session.DialogueContentLookup;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;
import top.rookiestwo.maimai_dialogue.client.session.DialogueSession;
import top.rookiestwo.maimai_dialogue.client.session.DialogueSessionEffect;
import top.rookiestwo.maimai_dialogue.client.session.DialogueSessionUpdate;
import top.rookiestwo.maimai_dialogue.client.session.OptionCommandDecision;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueOption;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueStep;
import top.rookiestwo.maimai_dialogue.network.DialogueAccessStatus;
import top.rookiestwo.maimai_dialogue.network.OptionCommandStatus;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueAccessResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueRequestResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.ExecuteOptionCommandC2S;
import top.rookiestwo.maimai_dialogue.network.payload.OpenDialogueS2C;
import top.rookiestwo.maimai_dialogue.network.payload.OptionCommandResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.QueryDialogueAccessC2S;
import top.rookiestwo.maimai_dialogue.network.payload.RequestDialogueC2S;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneAction;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.function.Supplier;

public final class ClientDialogueController {
    private final Supplier<ClientContentSnapshot> content;
    private long nextRootRequestId = Long.MIN_VALUE;
    private long nextGeneration = 1L;
    @Nullable
    private PendingRootRequest pendingRootRequest;
    @Nullable
    private DialogueSession session;
    @Nullable
    private DialogueFragment fragment;

    ClientDialogueController(Supplier<ClientContentSnapshot> content) {
        this.content = Objects.requireNonNull(content, "content");
    }

    // 处理服务端主动下发的根 Dialogue 打开请求。
    public void handleOpen(OpenDialogueS2C payload) {
        requireClientThread();
        if (session != null) {
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
        openSession(payload.dialogueId(), definition);
    }

    // 向服务端请求打开一个新的根 Dialogue。
    public void requestRoot(ResourceLocation dialogueId) {
        requireClientThread();
        if (session != null || pendingRootRequest != null) {
            return;
        }
        long requestId = nextRootRequestId++;
        pendingRootRequest = new PendingRootRequest(requestId, dialogueId);
        PacketDistributor.sendToServer(new RequestDialogueC2S(
                requestId,
                dialogueId
        ));
    }

    // 把选项访问结果交给当前 session，并忽略已经过期的响应。
    public void handleAccessResult(DialogueAccessResultS2C payload) {
        requireClientThread();
        DialogueSession current = session;
        if (current == null) {
            return;
        }
        DialogueSessionUpdate update = current.handleAccessResult(
                payload.requestId(),
                payload.entries().stream().collect(Collectors.toMap(
                        entry -> entry.dialogueId(),
                        entry -> accessDecision(entry.status()),
                        (left, right) -> left
                ))
        );
        if (!update.changed()) {
            MaiMaiDialogue.LOGGER.debug(
                    "Ignoring stale dialogue access result {}.",
                    payload.requestId()
            );
            return;
        }
        applyUpdate(update);
    }

    // 区分根请求与 session 目标请求，并应用对应的服务端结果。
    public void handleRequestResult(DialogueRequestResultS2C payload) {
        requireClientThread();
        PendingRootRequest rootPending = pendingRootRequest;
        if (rootPending != null
                && rootPending.requestId == payload.requestId()
                && rootPending.dialogueId.equals(payload.dialogueId())) {
            handleRootResult(payload);
            return;
        }
        DialogueSession current = session;
        if (current == null) {
            return;
        }
        DialogueSessionUpdate update = current.handleTargetResult(
                payload.requestId(),
                payload.dialogueId(),
                accessDecision(payload.status())
        );
        if (!update.changed()) {
            MaiMaiDialogue.LOGGER.debug(
                    "Ignoring stale dialogue request result {}.",
                    payload.requestId()
            );
            return;
        }
        applyUpdate(update);
    }

    public void handleOptionCommandResult(OptionCommandResultS2C payload) {
        requireClientThread();
        DialogueSession current = session;
        if (current == null) {
            return;
        }
        DialogueSessionUpdate update = current.handleOptionCommandResult(
                payload.requestId(),
                payload.dialogueId(),
                payload.optionIndex(),
                optionCommandDecision(payload.status())
        );
        if (!update.changed()) {
            MaiMaiDialogue.LOGGER.debug(
                    "Ignoring stale option command result {}.",
                    payload.requestId()
            );
            return;
        }
        applyUpdate(update);
    }

    public void advance() {
        requireClientThread();
        applySessionUpdate(DialogueSession::advance);
    }

    public void skipToEnd() {
        requireClientThread();
        applySessionUpdate(DialogueSession::skipToEnd);
    }

    public void completePlayback(long generation, long playbackToken) {
        requireClientThread();
        applySessionUpdate(current -> current.completeScene(
                generation,
                playbackToken
        ));
    }

    public void completeTextPlayback(long generation, long playbackToken) {
        requireClientThread();
        applySessionUpdate(current -> current.completeText(
                generation,
                playbackToken
        ));
    }

    public void selectOption(DialogueOption option) {
        requireClientThread();
        applySessionUpdate(current -> current.selectOption(option));
    }

    // 清理与被销毁 Fragment 绑定的全部会话状态。
    public void onFragmentDestroyed(DialogueFragment destroyedFragment) {
        requireClientThread();
        if (fragment != destroyedFragment) {
            return;
        }
        fragment = null;
        session = null;
        pendingRootRequest = null;
        nextGeneration++;
    }

    public DialogueScreenState viewState() {
        DialogueSession current = session;
        if (current != null) {
            return current.screenState();
        }
        return new DialogueScreenState(
                nextGeneration,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                PlaybackPhase.READY,
                false,
                java.util.Optional.empty(),
                false,
                DialogueStep.DEFAULT_TYPEWRITER_INTERVAL_MS,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                List.of(),
                List.of(),
                false,
                false
        );
    }

    private void handleRootResult(DialogueRequestResultS2C payload) {
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
        openSession(payload.dialogueId(), definition);
    }

    // 创建纯 session，再由 controller 负责建立 Modern UI Screen。
    private void openSession(
            ResourceLocation rootDialogueId,
            DialogueDefinition definition
    ) {
        DialogueSession next = new DialogueSession(
                contentLookup(),
                rootDialogueId,
                definition,
                nextGeneration++,
                () -> ClientConfig.get().defaultTypewriterIntervalMs()
        );
        session = next;
        DialogueFragment newFragment = new DialogueFragment(this);
        fragment = newFragment;
        applyUpdate(next.start());
        var screen = MuiModApi.get().createScreen(
                newFragment,
                newFragment,
                null,
                "MaiMai Dialogue"
        );
        Minecraft.getInstance().setScreen(screen);
    }

    private void applySessionUpdate(
            java.util.function.Function<DialogueSession, DialogueSessionUpdate>
                    operation
    ) {
        DialogueSession current = session;
        if (current == null) {
            return;
        }
        DialogueSessionUpdate update = operation.apply(current);
        if (update.changed()) {
            applyUpdate(update);
        }
    }

    // 执行 session 产生的网络、关闭和错误报告 effect。
    private void applyUpdate(DialogueSessionUpdate update) {
        for (DialogueSessionEffect effect : update.effects()) {
            if (effect instanceof DialogueSessionEffect.QueryAccess query) {
                PacketDistributor.sendToServer(new QueryDialogueAccessC2S(
                        query.requestId(),
                        query.targets()
                ));
            } else if (effect
                    instanceof DialogueSessionEffect.RequestTarget request) {
                PacketDistributor.sendToServer(new RequestDialogueC2S(
                        request.requestId(),
                        request.target()
                ));
            } else if (effect
                    instanceof DialogueSessionEffect.ExecuteOptionCommand command) {
                PacketDistributor.sendToServer(new ExecuteOptionCommandC2S(
                        command.requestId(),
                        command.sourceDialogue(),
                        command.optionIndex()
                ));
            } else if (effect instanceof DialogueSessionEffect.Close) {
                Minecraft.getInstance().setScreen(null);
            } else if (effect
                    instanceof DialogueSessionEffect.ReportError error) {
                reportDevelopmentError(error.message());
            }
        }
        DialogueFragment currentFragment = fragment;
        if (currentFragment != null) {
            currentFragment.render(update.state());
        }
    }

    private DialogueContentLookup contentLookup() {
        return new DialogueContentLookup() {
            private ClientContentSnapshot snapshot() {
                return content.get();
            }

            @Override
            public java.util.Optional<DialogueDefinition> dialogue(
                    ResourceLocation id
            ) {
                return snapshot().dialogues().find(id);
            }

            @Override
            public java.util.Optional<top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition>
                    speaker(ResourceLocation id) {
                return snapshot().speakers().find(id);
            }

            @Override
            public java.util.Optional<top.rookiestwo.maimai_dialogue.theme.ThemeDefinition>
                    theme(ResourceLocation id) {
                return snapshot().themes().find(id);
            }

            @Override
            public java.util.Optional<top.rookiestwo.maimai_dialogue.dialogue.VisualAssetDefinition>
                    visualAsset(ResourceLocation id) {
                return snapshot().visualAssets().find(id);
            }

            @Override
            public java.util.Optional<SceneAction>
                    action(ResourceLocation id) {
                return snapshot().actions().find(id);
            }
        };
    }

    @Nullable
    private DialogueDefinition localDefinition(ResourceLocation id) {
        return content.get().dialogues().find(id).orElse(null);
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

    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException(
                    "Dialogue controller must run on the client thread."
            );
        }
    }

    private static DialogueAccessDecision accessDecision(
            DialogueAccessStatus status
    ) {
        return switch (status) {
            case ALLOWED -> DialogueAccessDecision.ALLOWED;
            case DIALOGUE_NOT_FOUND ->
                    DialogueAccessDecision.DIALOGUE_NOT_FOUND;
            case REQUIREMENTS_NOT_MET ->
                    DialogueAccessDecision.REQUIREMENTS_NOT_MET;
            case PROGRESS_UNAVAILABLE ->
                    DialogueAccessDecision.PROGRESS_UNAVAILABLE;
            case INTERNAL_ERROR -> DialogueAccessDecision.INTERNAL_ERROR;
        };
    }

    private static OptionCommandDecision optionCommandDecision(
            OptionCommandStatus status
    ) {
        return switch (status) {
            case EXECUTED -> OptionCommandDecision.EXECUTED;
            case SOURCE_DIALOGUE_NOT_FOUND ->
                    OptionCommandDecision.SOURCE_DIALOGUE_NOT_FOUND;
            case SOURCE_REQUIREMENTS_NOT_MET ->
                    OptionCommandDecision.SOURCE_REQUIREMENTS_NOT_MET;
            case TARGET_DIALOGUE_NOT_FOUND ->
                    OptionCommandDecision.TARGET_DIALOGUE_NOT_FOUND;
            case TARGET_REQUIREMENTS_NOT_MET ->
                    OptionCommandDecision.TARGET_REQUIREMENTS_NOT_MET;
            case PROGRESS_UNAVAILABLE ->
                    OptionCommandDecision.PROGRESS_UNAVAILABLE;
            case INVALID_OPTION -> OptionCommandDecision.INVALID_OPTION;
            case COMMAND_FAILED -> OptionCommandDecision.COMMAND_FAILED;
            case INTERNAL_ERROR -> OptionCommandDecision.INTERNAL_ERROR;
        };
    }

    private record PendingRootRequest(
            long requestId,
            ResourceLocation dialogueId
    ) {
    }
}
