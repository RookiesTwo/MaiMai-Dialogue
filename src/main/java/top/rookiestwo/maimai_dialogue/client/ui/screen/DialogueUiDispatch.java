package top.rookiestwo.maimai_dialogue.client.ui.screen;

import icyllis.modernui.view.View;
import net.minecraft.client.Minecraft;

import java.util.function.BooleanSupplier;

final class DialogueUiDispatch {
    private DialogueUiDispatch() {
    }

    static void toClient(Runnable operation) {
        Minecraft.getInstance().execute(operation);
    }

    // View 身份检查留在 UI 队列中执行；它与 session 的 generation/token 检查互不替代。
    static void toView(View owner, BooleanSupplier isCurrent, Runnable operation) {
        owner.post(() -> {
            if (isCurrent.getAsBoolean()) {
                operation.run();
            }
        });
    }
}
