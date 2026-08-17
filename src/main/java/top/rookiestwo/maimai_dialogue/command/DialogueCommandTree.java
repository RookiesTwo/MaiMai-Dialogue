package top.rookiestwo.maimai_dialogue.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.api.MaiMaiDialogueApi;
import top.rookiestwo.maimai_dialogue.internal.bootstrap.CommonServices;

final class DialogueCommandTree {
    private static final String PLAYER_ARGUMENT = "player";
    private static final String DIALOGUE_ARGUMENT = "dialogue";

    private DialogueCommandTree() {
    }

    // 构建负责打开 Dialogue 的命令分支。
    static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("open")
                .then(Commands.argument(
                                PLAYER_ARGUMENT,
                                EntityArgument.player()
                        )
                        .then(Commands.argument(
                                        DIALOGUE_ARGUMENT,
                                        ResourceLocationArgument.id()
                                )
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggestResource(
                                                CommonServices.get()
                                                        .serverDialogues()
                                                        .current()
                                                        .ids(),
                                                builder
                                        ))
                                .executes(DialogueCommandTree::open)));
    }

    private static int open(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, PLAYER_ARGUMENT);
        var dialogueId = ResourceLocationArgument.getId(
                context,
                DIALOGUE_ARGUMENT
        );
        MaiMaiDialogueApi.get().dialogues().open(player, dialogueId)
                .whenComplete((result, error) ->
                        source.getServer().execute(() -> {
                            if (error != null) {
                                source.sendFailure(Component.translatable(
                                        "commands.maimai_dialogue.dialogue.open.failed",
                                        dialogueId.toString()
                                ));
                                MaiMaiDialogue.LOGGER.error(
                                        "Failed to open dialogue {} for player {}",
                                        dialogueId,
                                        player.getUUID(),
                                        error
                                );
                                return;
                            }
                            switch (result) {
                                case SENT -> source.sendSuccess(
                                        () -> Component.translatable(
                                                "commands.maimai_dialogue.dialogue.open.sent",
                                                dialogueId.toString(),
                                                player.getName()
                                        ),
                                        true
                                );
                                case DIALOGUE_NOT_FOUND -> source.sendFailure(
                                        Component.translatable(
                                                "commands.maimai_dialogue.dialogue.open.not_found",
                                                dialogueId.toString()
                                        )
                                );
                                case REQUIREMENTS_NOT_MET -> source.sendFailure(
                                        Component.translatable(
                                                "commands.maimai_dialogue.dialogue.open.requirements_not_met",
                                                player.getName()
                                        )
                                );
                                case PROGRESS_UNAVAILABLE -> source.sendFailure(
                                        Component.translatable(
                                                "commands.maimai_dialogue.dialogue.open.progress_unavailable",
                                                player.getName()
                                        )
                                );
                                case PENDING_DIALOGUE_CONFLICT ->
                                        source.sendFailure(
                                                Component.translatable(
                                                        "commands.maimai_dialogue.dialogue.open.pending_conflict",
                                                        player.getName()
                                                )
                                        );
                                case PERSISTENCE_FAILED -> source.sendFailure(
                                        Component.translatable(
                                                "commands.maimai_dialogue.dialogue.open.persistence_failed",
                                                player.getName()
                                        )
                                );
                                case INTERNAL_ERROR -> source.sendFailure(
                                        Component.translatable(
                                                "commands.maimai_dialogue.dialogue.open.internal_error"
                                        )
                                );
                            }
                        })
                );
        return 1;
    }
}
