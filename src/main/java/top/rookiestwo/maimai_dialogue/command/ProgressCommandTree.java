package top.rookiestwo.maimai_dialogue.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.api.progress.ProgressChangeResult;
import top.rookiestwo.maimai_dialogue.internal.bootstrap.CommonServices;
import top.rookiestwo.maimai_dialogue.progress.ProgressNode;

import java.util.Comparator;
import java.util.concurrent.CompletionStage;

final class ProgressCommandTree {
    private static final String PLAYER_ARGUMENT = "player";
    private static final String NODE_ARGUMENT = "node";

    private ProgressCommandTree() {
    }

    // 构建 ProgressNode 的增删、查询和列表命令分支。
    static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("progress")
                .then(nodeCommand("add", ProgressCommandTree::add))
                .then(nodeCommand("remove", ProgressCommandTree::remove))
                .then(Commands.literal("list")
                        .then(Commands.argument(
                                        PLAYER_ARGUMENT,
                                        EntityArgument.player()
                                )
                                .executes(ProgressCommandTree::list)))
                .then(nodeCommand("check", ProgressCommandTree::check));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> nodeCommand(
            String name,
            com.mojang.brigadier.Command<CommandSourceStack> command
    ) {
        return Commands.literal(name)
                .then(Commands.argument(
                                PLAYER_ARGUMENT,
                                EntityArgument.player()
                        )
                        .then(Commands.argument(
                                        NODE_ARGUMENT,
                                        StringArgumentType.word()
                                )
                                .executes(command)));
    }

    private static int add(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, PLAYER_ARGUMENT);
        ProgressNode node = parseNode(context);
        sendMutationFeedback(
                context.getSource(),
                player,
                node,
                CommonServices.get().progress().add(player, node)
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
                CommonServices.get().progress().remove(player, node)
        );
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, PLAYER_ARGUMENT);
        var snapshot = CommonServices.get().progress().snapshotNow(player);
        if (snapshot.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "commands.maimai_dialogue.progress.unavailable",
                    player.getName()
            ));
            return 0;
        }
        Component nodes = snapshot.orElseThrow().nodes().stream()
                .sorted(Comparator.comparing(ProgressNode::value))
                .map(ProgressNode::value)
                .reduce((left, right) -> left + ", " + right)
                .<Component>map(Component::literal)
                .orElseGet(() -> Component.translatable(
                        "commands.maimai_dialogue.progress.list.empty"
                ));
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.maimai_dialogue.progress.list",
                        player.getName(),
                        nodes
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
        var snapshot = CommonServices.get().progress().snapshotNow(player);
        if (snapshot.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "commands.maimai_dialogue.progress.unavailable",
                    player.getName()
            ));
            return 0;
        }
        boolean present = snapshot.orElseThrow().contains(node);
        source.sendSuccess(
                () -> Component.translatable(
                        present
                                ? "commands.maimai_dialogue.progress.check.present"
                                : "commands.maimai_dialogue.progress.check.absent",
                        node.value()
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
                        Component.translatable(
                                "commands.maimai_dialogue.progress.invalid_node",
                                value
                        )
                ).create()
        );
    }

    // 等待异步持久化完成后，在 server thread 反馈命令结果。
    private static void sendMutationFeedback(
            CommandSourceStack source,
            ServerPlayer player,
            ProgressNode node,
            CompletionStage<ProgressChangeResult> operation
    ) {
        operation.whenComplete((result, error) ->
                source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.translatable(
                                "commands.maimai_dialogue.progress.save_failed",
                                node.value()
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
                            () -> feedback(
                                    result,
                                    node,
                                    player
                            ),
                            true
                    );
                })
        );
    }

    private static Component feedback(
            ProgressChangeResult result,
            ProgressNode node,
            ServerPlayer player
    ) {
        return switch (result) {
            case ADDED -> Component.translatable(
                    "commands.maimai_dialogue.progress.added",
                    node.value(),
                    player.getName()
            );
            case ALREADY_PRESENT -> Component.translatable(
                    "commands.maimai_dialogue.progress.already_present",
                    player.getName(),
                    node.value()
            );
            case REMOVED -> Component.translatable(
                    "commands.maimai_dialogue.progress.removed",
                    node.value(),
                    player.getName()
            );
            case NOT_PRESENT -> Component.translatable(
                    "commands.maimai_dialogue.progress.not_present",
                    player.getName(),
                    node.value()
            );
        };
    }
}
