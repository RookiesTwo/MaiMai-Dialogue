package top.rookiestwo.maimai_dialogue.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;

@EventBusSubscriber(modid = MaiMaiDialogue.MOD_ID)
public final class MaiMaiDialogueCommands {
    private MaiMaiDialogueCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    // 组合 Dialogue 与 Progress 两个独立的命令分支。
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("maimai_dialogue")
                        .requires(source -> source.hasPermission(2))
                        .then(DialogueCommandTree.create())
                        .then(ProgressCommandTree.create())
        );
    }
}
