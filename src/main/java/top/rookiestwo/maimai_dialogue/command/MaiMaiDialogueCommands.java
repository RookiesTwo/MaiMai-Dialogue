package top.rookiestwo.maimai_dialogue.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.api.MaiMaiDialogueApi;
import top.rookiestwo.maimai_dialogue.api.progress.ProgressChangeResult;
import top.rookiestwo.maimai_dialogue.progress.ProgressNode;
import top.rookiestwo.maimai_dialogue.progress.ProgressServices;

import java.util.Comparator;
import java.util.concurrent.CompletionStage;

@EventBusSubscriber(modid = MaiMaiDialogue.MOD_ID)
public final class MaiMaiDialogueCommands {
    private static final String PLAYER_ARGUMENT = "player";
    private static final String NODE_ARGUMENT = "node";
    private static final String DIALOGUE_ARGUMENT = "dialogue";

    private MaiMaiDialogueCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("maimai_dialogue")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("open")
                                .then(Commands.argument(
                                                PLAYER_ARGUMENT,
                                                EntityArgument.player()
                                        )
                                        .then(Commands.argument(
                                                        DIALOGUE_ARGUMENT,
                                                        ResourceLocationArgument.id()
                                                )
                                                .executes(
                                                        MaiMaiDialogueCommands::open
                                                ))))
                        .then(Commands.literal("progress")
                                .then(Commands.literal("add")
                                        .then(Commands.argument(
                                                        PLAYER_ARGUMENT,
                                                        EntityArgument.player()
                                                )
                                                .then(Commands.argument(
                                                                NODE_ARGUMENT,
                                                                StringArgumentType.word()
                                                        )
                                                        .executes(
                                                                MaiMaiDialogueCommands::add
                                                        ))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument(
                                                        PLAYER_ARGUMENT,
                                                        EntityArgument.player()
                                                )
                                                .then(Commands.argument(
                                                                NODE_ARGUMENT,
                                                                StringArgumentType.word()
                                                        )
                                                        .executes(
                                                                MaiMaiDialogueCommands::remove
                                                        ))))
                                .then(Commands.literal("list")
                                        .then(Commands.argument(
                                                        PLAYER_ARGUMENT,
                                                        EntityArgument.player()
                                                )
                                                .executes(
                                                        MaiMaiDialogueCommands::list
                                                )))
                                .then(Commands.literal("check")
                                        .then(Commands.argument(
                                                        PLAYER_ARGUMENT,
                                                        EntityArgument.player()
                                                )
                                                .then(Commands.argument(
                                                                NODE_ARGUMENT,
                                                                StringArgumentType.word()
                                                        )
                                                        .executes(
                                                                MaiMaiDialogueCommands::check
                                                        )))))
        );
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
                                        "Failed to open dialogue " + dialogueId + "."
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
                                                        + player.getName().getString()
                                                        + "."
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
                                                        + player.getName().getString()
                                                        + "."
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

    private static int add(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, PLAYER_ARGUMENT);
        ProgressNode node = parseNode(context);
        sendMutationFeedback(
                context.getSource(),
                player,
                node,
                ProgressServices.repository().add(player, node)
        );
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, PLAYER_ARGUMENT);
        ProgressNode node = parseNode(context);
        sendMutationFeedback(
                context.getSource(),
                player,
                node,
                ProgressServices.repository().remove(player, node)
        );
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, PLAYER_ARGUMENT);
        var snapshot = ProgressServices.repository().snapshotNow(player);
        if (snapshot.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Progress data is not available for " + player.getName().getString() + "."
            ));
            return 0;
        }

        String nodes = snapshot.orElseThrow().nodes().stream()
                .sorted(Comparator.comparing(ProgressNode::value))
                .map(ProgressNode::value)
                .reduce((left, right) -> left + ", " + right)
                .orElse("(empty)");
        source.sendSuccess(
                () -> Component.literal(
                        player.getName().getString() + ": " + nodes
                ),
                false
        );
        return snapshot.orElseThrow().nodes().size();
    }

    private static int check(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, PLAYER_ARGUMENT);
        ProgressNode node = parseNode(context);
        var snapshot = ProgressServices.repository().snapshotNow(player);
        if (snapshot.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Progress data is not available for " + player.getName().getString() + "."
            ));
            return 0;
        }

        boolean present = snapshot.orElseThrow().contains(node);
        source.sendSuccess(
                () -> Component.literal(
                        node.value() + (present ? " is present." : " is not present.")
                ),
                false
        );
        return present ? 1 : 0;
    }

    private static ProgressNode parseNode(
            CommandContext<CommandSourceStack> context
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String value = StringArgumentType.getString(context, NODE_ARGUMENT);
        return ProgressNode.parse(value).result().orElseThrow(
                () -> new SimpleCommandExceptionType(
                        Component.literal("Invalid progress node: " + value)
                ).create()
        );
    }

    private static void sendMutationFeedback(
            CommandSourceStack source,
            ServerPlayer player,
            ProgressNode node,
            CompletionStage<ProgressChangeResult> operation
    ) {
        operation.whenComplete((result, error) ->
                source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal(
                                "Failed to save progress node " + node.value() + "."
                        ));
                        MaiMaiDialogue.LOGGER.error(
                                "Failed to update progress node {} for player {}",
                                node,
                                player.getUUID(),
                                error
                        );
                        return;
                    }

                    source.sendSuccess(
                            () -> Component.literal(feedback(result, node, player)),
                            true
                    );
                })
        );
    }

    private static String feedback(
            ProgressChangeResult result,
            ProgressNode node,
            ServerPlayer player
    ) {
        String playerName = player.getName().getString();
        return switch (result) {
            case ADDED -> "Added " + node.value() + " to " + playerName + ".";
            case ALREADY_PRESENT ->
                    playerName + " already has " + node.value() + ".";
            case REMOVED ->
                    "Removed " + node.value() + " from " + playerName + ".";
            case NOT_PRESENT ->
                    playerName + " does not have " + node.value() + ".";
        };
    }
}
