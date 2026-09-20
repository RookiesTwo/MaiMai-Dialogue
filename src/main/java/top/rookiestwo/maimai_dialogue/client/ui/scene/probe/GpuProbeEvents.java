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
        event.getDispatcher().register(Commands.literal("maimai_dialogue_gpu_probe")
                .executes(context -> scheduleOpen(false))
                .then(Commands.literal("crt").executes(context -> scheduleOpen(true))));
    }

    private static int scheduleOpen(boolean crt) {
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        minecraft.tell(() -> {
            if (minecraft.level == level && minecraft.screen == null) open(crt);
        });
        return 1;
    }

    /** One-run diagnostic launch option, never changes the user's saved Client configuration. */
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        String mode = System.getenv("MAIMAI_GPU_PROBE");
        if (!autoOpened && ("1".equals(mode) || "crt".equals(mode))) {
            var minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
                autoOpened = true; open("crt".equals(mode));
            }
        }
    }

    private static void open(boolean crt) {
        var minecraft = Minecraft.getInstance();
        var fragment = new GpuProbeFragment(crt);
        minecraft.setScreen(MuiForgeApi.get().createScreen(fragment, fragment, minecraft.screen));
    }
}
