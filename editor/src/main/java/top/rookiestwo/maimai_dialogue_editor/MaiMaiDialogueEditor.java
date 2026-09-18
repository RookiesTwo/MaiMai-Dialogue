package top.rookiestwo.maimai_dialogue_editor;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import top.rookiestwo.maimai_dialogue_editor.client.EditorScreens;

@Mod(value = MaiMaiDialogueEditor.MOD_ID, dist = Dist.CLIENT)
public final class MaiMaiDialogueEditor {
    public static final String MOD_ID = "maimai_dialogue_editor";
    private static final Logger LOGGER = LogUtils.getLogger();

    // 两个入口共用相同的客户端工作台，不注册服务端命令。
    public MaiMaiDialogueEditor(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (minecraft, parent) -> EditorScreens.create(parent));
        NeoForge.EVENT_BUS.addListener(EditorScreens::registerCommands);
        NeoForge.EVENT_BUS.addListener(EditorScreens::clientTick);
        LOGGER.info("MaiMai Dialogue Editor workbench registered.");
    }
}
