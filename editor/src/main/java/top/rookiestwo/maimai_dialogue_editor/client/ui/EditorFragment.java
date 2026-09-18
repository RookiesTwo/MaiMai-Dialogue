package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import org.jetbrains.annotations.Nullable;
import top.rookiestwo.maimai_dialogue_editor.client.EditorScreens;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectStore;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceWorkspace;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EditorFragment extends Fragment implements ScreenCallback {
    private final EditorLayoutState layoutState = new EditorLayoutState();
    @Nullable
    private EditorWorkspaceView root;
    private ProjectWorkspace workspace;
    private EditorPreviewHost preview;
    private ExecutorService io;
    private volatile boolean gameWindowFocused = true;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        if (workspace == null) {
            io = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "MaiMai-Editor-IO");
                thread.setDaemon(true);
                return thread;
            });
            workspace = new ProjectWorkspace(new ProjectStore(Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("maimai-dialogue-projects")), io,
                    task -> Core.getUiHandler().post(task), () -> EditorScreens.close(this));
            workspace.windowFocusChanged(gameWindowFocused);
            preview = new EditorPreviewHost(this, workspace);
        }
        root = new EditorWorkspaceView(requireContext(), layoutState, workspace, preview);
        workspace.setListener(root::refresh);
        if (workspace.page() == ProjectWorkspace.Page.NONE && workspace.resources().form() == ResourceWorkspace.Form.NONE) {
            root.requestFocus();
        }
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preview.onViewReady();
    }

    // 由 Minecraft 客户端线程调用，只在焦点变化时跨线程投递；失焦不依赖 PopupWindow。
    public void updateWindowFocus(boolean focused) {
        if (gameWindowFocused == focused) return;
        gameWindowFocused = focused;
        Core.getUiHandler().post(() -> {
            if (workspace != null) workspace.windowFocusChanged(focused);
            if (!focused && root != null) {
                root.cancelDrags();
                root.dismissChoices();
            }
        });
    }

    // View 可重建，但当前打开期间的布局偏好由 Fragment 保留。
    @Override
    public void onDestroyView() {
        if (preview != null) preview.releaseView();
        if (workspace != null) {
            workspace.endEdit();
            workspace.setListener(() -> {});
        }
        if (root != null) {
            root.cancelDrags();
            root.releaseDropdown();
            root = null;
        }
        super.onDestroyView();
    }

    @Override
    public boolean isBackKey(int keyCode, @NonNull KeyEvent event) {
        if (keyCode == KeyEvent.KEY_ESCAPE && event.getRepeatCount() == 0 && root != null) root.escape();
        return false;
    }

    @Override
    public boolean shouldClose() {
        // ModernUI calls this fallback on the Minecraft thread; confirmation belongs on the UI thread.
        Core.getUiHandler().post(() -> {
            if (root != null) root.escape();
        });
        return false;
    }

    @Override
    public void onDestroy() {
        if (preview != null) preview.dispose();
        if (workspace != null) workspace.dispose();
        if (io != null) io.shutdown();
        super.onDestroy();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean hasDefaultBackground() {
        return false;
    }

    @Override
    public boolean shouldBlurBackground() {
        return false;
    }
}
