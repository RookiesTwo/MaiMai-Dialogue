package top.rookiestwo.maimai_dialogue.client.ui.scene.probe;

import icyllis.modernui.mc.neoforge.MuiForgeApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;

@EventBusSubscriber(modid = MaiMaiDialogue.MOD_ID, value = Dist.CLIENT)
public final class GpuProbeEvents {
    private static boolean autoOpened;
    private GpuProbeEvents() { }

    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("maimai_dialogue_gpu_probe").executes(context -> {
            Minecraft minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            minecraft.tell(() -> {
                if (minecraft.level == level && minecraft.screen == null) open();
            });
            return 1;
        }));
    }

    /** One-run diagnostic launch option, never changes the user's saved Client configuration. */
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!autoOpened && "1".equals(System.getenv("MAIMAI_GPU_PROBE"))) {
            var minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
                autoOpened = true; open();
            }
        }
    }

    private static void open() {
        var minecraft = Minecraft.getInstance();
        var fragment = new GpuProbeFragment();
        minecraft.setScreen(MuiForgeApi.get().createScreen(fragment, fragment, minecraft.screen));
    }
}
