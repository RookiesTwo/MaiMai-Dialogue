package top.rookiestwo.maimai_dialogue_editor.client;

import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.MuiScreen;
import icyllis.modernui.mc.neoforge.MuiForgeApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import top.rookiestwo.maimai_dialogue_editor.client.ui.EditorFragment;

public final class EditorScreens {
    private EditorScreens() {
    }

    public static Screen create(@Nullable Screen previousScreen) {
        return MuiForgeApi.get().createScreen(new EditorFragment(), null, previousScreen);
    }

    /** Native file dialogs can iconify an exclusive-fullscreen window. Restore only their original editor. */
    public static void restoreFocus(Fragment owner) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof MuiScreen screen && screen.getFragment() == owner) {
                long window = minecraft.getWindow().getWindow();
                if (org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window, org.lwjgl.glfw.GLFW.GLFW_ICONIFIED) != 0)
                    org.lwjgl.glfw.GLFW.glfwRestoreWindow(window);
                org.lwjgl.glfw.GLFW.glfwFocusWindow(window);
            }
        });
    }

    public static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("maimai_dialogue_editor")
                .executes(context -> {
                    openAfterChat();
                    return 1;
                }));
    }

    // ModernUI 的 View 焦点回调不等同于 Minecraft 窗口焦点，显式同步当前编辑器。
    public static void clientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MuiScreen screen && screen.getFragment() instanceof EditorFragment editor) {
            editor.updateWindowFocus(minecraft.isWindowActive());
        }
    }

    // tell 始终排队，避免新界面被同一次输入里的聊天关闭操作清掉。
    private static void openAfterChat() {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        minecraft.tell(() -> {
            if (player != null && minecraft.player == player && minecraft.level == level
                    && minecraft.screen == null) {
                minecraft.setScreen(create(null));
            }
        });
    }

    // 旧 View 的关闭回调不能关闭后来打开的另一个界面。
    public static void close(Fragment owner) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof MuiScreen screen && screen.getFragment() == owner) {
                minecraft.setScreen(screen.getPreviousScreen());
            }
        });
    }
}
