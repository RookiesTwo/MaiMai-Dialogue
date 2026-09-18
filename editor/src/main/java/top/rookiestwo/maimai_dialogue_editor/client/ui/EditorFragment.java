package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import org.jetbrains.annotations.Nullable;
import top.rookiestwo.maimai_dialogue_editor.client.EditorScreens;

public final class EditorFragment extends Fragment implements ScreenCallback {
    private final EditorLayoutState layoutState = new EditorLayoutState();
    @Nullable
    private EditorWorkbench root;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        root = new EditorWorkbench(requireContext(), layoutState, () -> EditorScreens.close(this));
        root.requestFocus();
        return root;
    }

    // View 可重建，但当前打开期间的布局偏好由 Fragment 保留。
    @Override
    public void onDestroyView() {
        if (root != null) {
            root.cancelDrags();
            root = null;
        }
        super.onDestroyView();
    }

    @Override
    public boolean isBackKey(int keyCode, @NonNull KeyEvent event) {
        return keyCode == KeyEvent.KEY_ESCAPE;
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
