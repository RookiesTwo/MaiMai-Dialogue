package top.rookiestwo.maimai_dialogue;

import com.mojang.logging.LogUtils;
import icyllis.modernui.mc.neoforge.MuiForgeApi;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;
import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;
import top.rookiestwo.maimai_dialogue.client.config.ui.ClientConfigFragment;

import java.util.function.Consumer;

@Mod(MaiMaiDialogue.MOD_ID)
public final class MaiMaiDialogue {
    public static final String MOD_ID = "maimai_dialogue";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Mod(value = MOD_ID, dist = Dist.CLIENT)
    public static final class Client {
        public Client(IEventBus modEventBus, ModContainer modContainer) {
            modContainer.registerConfig(
                    ModConfig.Type.CLIENT,
                    ClientConfig.SPEC,
                    MOD_ID + "/client.toml"
            );
            modEventBus.addListener(
                    (Consumer<ModConfigEvent>) ClientConfig::onConfigEvent
            );
            modContainer.registerExtensionPoint(
                    IConfigScreenFactory.class,
                    (minecraft, parent) -> MuiForgeApi.get().createScreen(
                            new ClientConfigFragment(),
                            null,
                            parent
                    )
            );
        }
    }
}
