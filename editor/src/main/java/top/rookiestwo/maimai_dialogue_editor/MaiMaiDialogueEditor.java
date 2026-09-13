package top.rookiestwo.maimai_dialogue_editor;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(value = MaiMaiDialogueEditor.MOD_ID, dist = Dist.CLIENT)
public final class MaiMaiDialogueEditor {
    public static final String MOD_ID = "maimai_dialogue_editor";
    private static final Logger LOGGER = LogUtils.getLogger();

    // 当前只验证附属 MOD 的独立加载，编辑界面在后续阶段接入。
    public MaiMaiDialogueEditor() {
        LOGGER.info("MaiMai Dialogue Editor scaffold loaded.");
    }
}
