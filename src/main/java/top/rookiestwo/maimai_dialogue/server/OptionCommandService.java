package top.rookiestwo.maimai_dialogue.server;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.content.DefinitionRegistry;
import top.rookiestwo.maimai_dialogue.dialogue.ChoiceExit;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueOption;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueTarget;
import top.rookiestwo.maimai_dialogue.network.DialogueAccessStatus;
import top.rookiestwo.maimai_dialogue.network.OptionCommandStatus;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class OptionCommandService {
    private static final int COMMAND_PERMISSION_LEVEL = 2;

    private final Supplier<DefinitionRegistry<DialogueDefinition>> dialogues;
    private final DialogueAccessService access;

    public OptionCommandService(
            Supplier<DefinitionRegistry<DialogueDefinition>> dialogues,
            DialogueAccessService access
    ) {
        this.dialogues = Objects.requireNonNull(dialogues, "dialogues");
        this.access = Objects.requireNonNull(access, "access");
    }

    // 验证服务端定义中的 Option 与目标，再以点击玩家为来源执行 command。
    public CompletionStage<OptionCommandStatus> execute(
            ServerPlayer player,
            ResourceLocation dialogueId,
            int optionIndex
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(dialogueId, "dialogueId");

        return access.evaluate(player, dialogueId)
                .thenCompose(sourceStatus -> {
                    OptionCommandStatus rejection = sourceRejection(
                            sourceStatus
                    );
                    if (rejection != null) {
                        return CompletableFuture.completedFuture(rejection);
                    }

                    DialogueOption option = findOption(
                            dialogueId,
                            optionIndex
                    );
                    if (option == null || option.command().isEmpty()) {
                        return CompletableFuture.completedFuture(
                                OptionCommandStatus.INVALID_OPTION
                        );
                    }

                    if (option.target() instanceof DialogueTarget target) {
                        return access.evaluate(player, target.dialogue())
                                .thenCompose(targetStatus -> {
                                    OptionCommandStatus targetRejection =
                                            targetRejection(targetStatus);
                                    if (targetRejection != null) {
                                        return CompletableFuture.completedFuture(
                                                targetRejection
                                        );
                                    }
                                    return executeOnServerThread(
                                            player,
                                            dialogueId,
                                            optionIndex,
                                            option.command().orElseThrow()
                                    );
                                });
                    }

                    return executeOnServerThread(
                            player,
                            dialogueId,
                            optionIndex,
                            option.command().orElseThrow()
                    );
                })
                .exceptionally(error -> {
                    MaiMaiDialogue.LOGGER.error(
                            "Failed to process option command for dialogue {} option {} from player {}",
                            dialogueId,
                            optionIndex,
                            player.getUUID(),
                            error
                    );
                    return OptionCommandStatus.INTERNAL_ERROR;
                });
    }

    private DialogueOption findOption(
            ResourceLocation dialogueId,
            int optionIndex
    ) {
        DialogueDefinition definition = dialogues.get()
                .find(dialogueId)
                .orElse(null);
        if (definition == null
                || !(definition.end().exit() instanceof ChoiceExit exit)
                || optionIndex < 0
                || optionIndex >= exit.options().size()) {
            return null;
        }
        return exit.options().get(optionIndex);
    }

    private CompletionStage<OptionCommandStatus> executeOnServerThread(
            ServerPlayer player,
            ResourceLocation dialogueId,
            int optionIndex,
            String command
    ) {
        CompletableFuture<OptionCommandStatus> result = new CompletableFuture<>();
        MinecraftServer server = player.getServer();
        Runnable execution = () -> {
            AtomicBoolean callbackInvoked = new AtomicBoolean();
            AtomicBoolean successful = new AtomicBoolean();
            CommandResultCallback callback = (success, value) -> {
                callbackInvoked.set(true);
                if (success) {
                    successful.set(true);
                }
            };
            try {
                server.getCommands().performPrefixedCommand(
                        player.createCommandSourceStack()
                                .withPermission(COMMAND_PERMISSION_LEVEL)
                                .withCallback(callback),
                        command
                );
                if (callbackInvoked.get() && successful.get()) {
                    result.complete(OptionCommandStatus.EXECUTED);
                    return;
                }
                MaiMaiDialogue.LOGGER.warn(
                        "Option command failed for dialogue {} option {} from player {}: {}",
                        dialogueId,
                        optionIndex,
                        player.getUUID(),
                        command
                );
                result.complete(OptionCommandStatus.COMMAND_FAILED);
            } catch (RuntimeException error) {
                MaiMaiDialogue.LOGGER.error(
                        "Option command crashed for dialogue {} option {} from player {}: {}",
                        dialogueId,
                        optionIndex,
                        player.getUUID(),
                        command,
                        error
                );
                result.complete(OptionCommandStatus.INTERNAL_ERROR);
            }
        };
        if (server.isSameThread()) {
            execution.run();
        } else {
            server.execute(execution);
        }
        return result;
    }

    private static OptionCommandStatus sourceRejection(
            DialogueAccessStatus status
    ) {
        return switch (status) {
            case ALLOWED -> null;
            case DIALOGUE_NOT_FOUND ->
                    OptionCommandStatus.SOURCE_DIALOGUE_NOT_FOUND;
            case REQUIREMENTS_NOT_MET ->
                    OptionCommandStatus.SOURCE_REQUIREMENTS_NOT_MET;
            case PROGRESS_UNAVAILABLE ->
                    OptionCommandStatus.PROGRESS_UNAVAILABLE;
            case INTERNAL_ERROR -> OptionCommandStatus.INTERNAL_ERROR;
        };
    }

    private static OptionCommandStatus targetRejection(
            DialogueAccessStatus status
    ) {
        return switch (status) {
            case ALLOWED -> null;
            case DIALOGUE_NOT_FOUND ->
                    OptionCommandStatus.TARGET_DIALOGUE_NOT_FOUND;
            case REQUIREMENTS_NOT_MET ->
                    OptionCommandStatus.TARGET_REQUIREMENTS_NOT_MET;
            case PROGRESS_UNAVAILABLE ->
                    OptionCommandStatus.PROGRESS_UNAVAILABLE;
            case INTERNAL_ERROR -> OptionCommandStatus.INTERNAL_ERROR;
        };
    }
}
