package top.rookiestwo.maimai_dialogue.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.api.MaiMaiDialogueApi;

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
                                source.sendFailure(Component.literal(
                                        "Failed to open dialogue "
                                                + dialogueId + "."
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
                                        () -> Component.literal(
                                                "Sent dialogue " + dialogueId
                                                        + " to "
                                                        + player.getName()
                                                        .getString() + "."
                                        ),
                                        true
                                );
                                case DIALOGUE_NOT_FOUND -> source.sendFailure(
                                        Component.literal(
                                                "Dialogue does not exist: "
                                                        + dialogueId
                                        )
                                );
                                case REQUIREMENTS_NOT_MET -> source.sendFailure(
                                        Component.literal(
                                                player.getName().getString()
                                                        + " does not meet the requirements."
                                        )
                                );
                                case PROGRESS_UNAVAILABLE -> source.sendFailure(
                                        Component.literal(
                                                "Progress data is unavailable for "
                                                        + player.getName()
                                                        .getString() + "."
                                        )
                                );
                                case INTERNAL_ERROR -> source.sendFailure(
                                        Component.literal(
                                                "An internal error prevented the dialogue from opening."
                                        )
                                );
                            }
                        })
                );
        return 1;
    }
}
