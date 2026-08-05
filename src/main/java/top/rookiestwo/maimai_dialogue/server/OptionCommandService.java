package top.rookiestwo.maimai_dialogue.server;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
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
                    if (option == null || option.commands().isEmpty()) {
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
                                            option.commands()
                                    );
                                });
                    }

                    return executeOnServerThread(
                            player,
                            dialogueId,
                            optionIndex,
                            option.commands()
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
            List<String> commands
    ) {
        CompletableFuture<OptionCommandStatus> result = new CompletableFuture<>();
        MinecraftServer server = Objects.requireNonNull(
                player.getServer(),
                "player server"
        );
        Runnable execution = () -> {
            CommandSourceStack source;
            try {
                source = player.createCommandSourceStack()
                        .withPermission(COMMAND_PERMISSION_LEVEL)
                        .withSuppressedOutput();
            } catch (RuntimeException error) {
                MaiMaiDialogue.LOGGER.error(
                        "Failed to prepare option command sequence for dialogue {} option {} from player {}",
                        dialogueId,
                        optionIndex,
                        player.getUUID(),
                        error
                );
                result.complete(OptionCommandStatus.INTERNAL_ERROR);
                return;
            }
            CommandSequenceResult sequence = executeSequence(
                    commands,
                    command -> executeCommand(server, source, command)
            );
            if (sequence.successful()) {
                result.complete(OptionCommandStatus.EXECUTED);
                return;
            }

            int failedIndex = sequence.failedCommandIndex();
            String failedCommand = commands.get(failedIndex);
            if (sequence.error() != null) {
                MaiMaiDialogue.LOGGER.error(
                        "Option command {}/{} crashed for dialogue {} option {} from player {}: {}",
                        failedIndex + 1,
                        commands.size(),
                        dialogueId,
                        optionIndex,
                        player.getUUID(),
                        failedCommand,
                        sequence.error()
                );
                result.complete(OptionCommandStatus.INTERNAL_ERROR);
                return;
            }

            MaiMaiDialogue.LOGGER.warn(
                    "Option command {}/{} failed for dialogue {} option {} from player {}: {}",
                    failedIndex + 1,
                    commands.size(),
                    dialogueId,
                    optionIndex,
                    player.getUUID(),
                    failedCommand
            );
            result.complete(OptionCommandStatus.COMMAND_FAILED);
        };
        if (server.isSameThread()) {
            execution.run();
        } else {
            server.execute(execution);
        }
        return result;
    }

    private static boolean executeCommand(
            MinecraftServer server,
            CommandSourceStack source,
            String command
    ) {
        AtomicBoolean callbackInvoked = new AtomicBoolean();
        AtomicBoolean successful = new AtomicBoolean();
        CommandResultCallback callback = (success, value) -> {
            callbackInvoked.set(true);
            if (success) {
                successful.set(true);
            }
        };
        server.getCommands().performPrefixedCommand(
                source.withCallback(callback),
                command
        );
        return callbackInvoked.get() && successful.get();
    }

    // 按配置顺序执行，并在首个失败或异常处停止。
    static CommandSequenceResult executeSequence(
            List<String> commands,
            Predicate<String> executor
    ) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(executor, "executor");
        for (int index = 0; index < commands.size(); index++) {
            try {
                if (!executor.test(commands.get(index))) {
                    return new CommandSequenceResult(false, index, null);
                }
            } catch (RuntimeException error) {
                return new CommandSequenceResult(false, index, error);
            }
        }
        return new CommandSequenceResult(true, -1, null);
    }

    record CommandSequenceResult(
            boolean successful,
            int failedCommandIndex,
            RuntimeException error
    ) {
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
