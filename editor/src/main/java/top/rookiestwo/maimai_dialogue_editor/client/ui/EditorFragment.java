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
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;
import top.rookiestwo.maimai_dialogue.client.bootstrap.ClientServices;
import top.rookiestwo.maimai_dialogue_editor.export.ExportWorkspace;
import top.rookiestwo.maimai_dialogue_editor.export.PackExporter;
import java.util.concurrent.CompletableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EditorFragment extends Fragment implements ScreenCallback {
    private final EditorLayoutState layoutState = new EditorLayoutState();
    @Nullable
    private EditorWorkspaceView root;
    private ProjectWorkspace workspace;
    private EditorPreviewHost preview;
    private ExportWorkspace exports;
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
            workspace.materials().setFilePicker(new top.rookiestwo.maimai_dialogue_editor.client.NativeMaterialFilePicker(
                    () -> EditorScreens.restoreFocus(this)));
            var assets = new top.rookiestwo.maimai_dialogue_editor.client.EditorPreviewAssets(
                    io, task -> Core.getUiHandler().post(task));
            assets.setLoadFailure(workspace.materials()::reportLoadFailure);
            workspace.materials().setPreview(assets);
            preview = new EditorPreviewHost(this, workspace, assets,
                    new top.rookiestwo.maimai_dialogue_editor.client.EditorAudioPreview(io, task -> Core.getUiHandler().post(task)));
            exports = new ExportWorkspace(workspace, io, task -> Core.getUiHandler().post(task), () -> {
                CompletableFuture<ExportWorkspace.Environment> result = new CompletableFuture<>();
                Minecraft.getInstance().execute(() -> {
                    try {
                        var version = SharedConstants.getCurrentVersion();
                        var content = ClientServices.get().content().current();
                        var manager = Minecraft.getInstance().getResourceManager();
                        var sounds = Minecraft.getInstance().getSoundManager().getAvailableSounds().stream()
                                .map(Object::toString).collect(java.util.stream.Collectors.toSet());
                        io.execute(() -> {
                            try {
                                var images = manager.listResources("textures", id -> !id.getPath().endsWith(".mcmeta")).keySet().stream()
                                        .map(id -> id.getNamespace() + ":" + id.getPath().substring("textures/".length()))
                                        .collect(java.util.stream.Collectors.toSet());
                                result.complete(new ExportWorkspace.Environment(content,
                                        version.getPackVersion(PackType.CLIENT_RESOURCES), version.getPackVersion(PackType.SERVER_DATA),
                                        new top.rookiestwo.maimai_dialogue_editor.material.MaterialPack.External(images, sounds)));
                            } catch (RuntimeException failure) { result.completeExceptionally(failure); }
                        });
                    } catch (RuntimeException failure) { result.completeExceptionally(failure); }
                });
                return result;
            }, new PackExporter(Minecraft.getInstance().gameDirectory.toPath().resolve("maimai-dialogue-exports")));
        }
        layoutState.restore(workspace.layoutPreferences());
        layoutState.changed = () -> workspace.layoutPreferences(layoutState.snapshot());
        root = new EditorWorkspaceView(requireContext(), layoutState, workspace, preview, exports, this);
        workspace.setListener(root::refresh);
        workspace.setStatusListener(root::refreshSaveState);
        exports.setListener(root::refresh);
        if (workspace.page() == ProjectWorkspace.Page.NONE && workspace.resources().form() == ResourceWorkspace.Form.NONE) {
            root.requestFocus();
        }
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preview.onViewReady();
        workspace.startSession();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (preview != null) preview.onViewReady();
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
        if (workspace != null) workspace.flushAutosave();
        if (preview != null) preview.releaseView();
        if (exports != null) exports.setListener(() -> {});
        if (workspace != null) {
            workspace.endEdit();
            workspace.setListener(() -> {});
            workspace.setStatusListener(() -> {});
        }
        if (root != null) {
            root.cancelDrags();
            root.releaseDropdown();
            root = null;
        }
        if (workspace != null) workspace.flushSession();
        super.onDestroyView();
    }

    @Override
    public boolean isBackKey(int keyCode, @NonNull KeyEvent event) {
        if (keyCode == KeyEvent.KEY_ESCAPE && event.getRepeatCount() == 0 && root != null && !root.nativeInputOpen()) root.escape();
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
        if (exports != null) exports.dispose();
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
