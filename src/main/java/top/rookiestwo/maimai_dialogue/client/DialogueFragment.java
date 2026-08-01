package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import net.minecraft.client.Minecraft;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueBoxLayout;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import javax.annotation.Nullable;
import java.util.Objects;

public final class DialogueFragment extends Fragment implements ScreenCallback {
    private final ClientDialogueController controller;
    private ThemeDefinition currentTheme = ThemeDefinition.DEFAULT;
    private long renderedGeneration = Long.MIN_VALUE;
    @Nullable
    private DialogueRootLayout rootLayout;
    @Nullable
    private DialogueSceneView sceneView;
    @Nullable
    private DialogueBoxView boxView;
    @Nullable
    private Button historyButton;
    @Nullable
    private DialogueHistoryView historyView;

    public DialogueFragment(ClientDialogueController controller) {
        this.controller = controller;
    }

    @Override
    // 创建顶层 UI，并把子视图之间的生命周期连接起来。
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable DataSet savedInstanceState
    ) {
        var context = Objects.requireNonNull(getContext(), "Fragment context");
        DialogueBoxView dialogueBox = new DialogueBoxView(
                context,
                option -> Minecraft.getInstance().execute(
                        () -> controller.selectOption(option)
                )
        );
        Button historyEntry = new Button(context);
        historyEntry.setText("History");
        historyEntry.setOnClickListener(view -> setHistoryOpen(true));
        DialogueHistoryView historyOverlay = new DialogueHistoryView(
                context,
                () -> setHistoryOpen(false)
        );
        DialogueSceneView scene = new DialogueSceneView(context);
        DialogueRootLayout root = new DialogueRootLayout(
                context,
                scene,
                dialogueBox,
                historyEntry,
                historyOverlay
        );
        root.setOnClickListener(view -> advanceFromUi());
        root.setAdvanceAction(this::advanceFromUi);
        scene.setDialogueOpacityConsumer(root::setDialogueOpacity);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.requestFocus();

        rootLayout = root;
        sceneView = scene;
        boxView = dialogueBox;
        historyButton = historyEntry;
        historyView = historyOverlay;
        render(controller.viewState());
        return root;
    }

    // 根据不可变 screen state 分发对话框、历史和场景状态。
    public void render(DialogueScreenState state) {
        DialogueRootLayout root = rootLayout;
        DialogueSceneView scene = sceneView;
        DialogueBoxView box = boxView;
        DialogueHistoryView history = historyView;
        Button historyEntry = historyButton;
        if (root == null
                || scene == null
                || box == null
                || history == null
                || historyEntry == null) {
            return;
        }

        box.post(() -> {
            if (state.generation() != renderedGeneration) {
                renderedGeneration = state.generation();
                currentTheme = state.theme().orElse(ThemeDefinition.DEFAULT);
                box.reset(currentTheme);
                history.applyTheme(currentTheme);
                historyEntry.setTextColor(currentTheme.text().primary().argb());
                historyEntry.setTextSize(
                        currentTheme.text().auxiliarySizeSp()
                );
                DialogueBoxView.applyControlButtonTheme(
                        historyEntry,
                        currentTheme
                );
                applyPresentation(state, root, scene);
            }

            state.scenePlayback().ifPresent(playback ->
                    scene.renderPlayback(
                            playback,
                            state.playbackSkipped(),
                            () -> Minecraft.getInstance().execute(
                                    () -> controller.completePlayback(
                                            state.generation(),
                                            playback.token()
                                    )
                            )
                    )
            );
            long textToken = state.scenePlayback()
                    .map(playback -> playback.token())
                    .orElse(Long.MIN_VALUE);
            box.render(
                    state,
                    () -> Minecraft.getInstance().execute(
                            () -> controller.completeTextPlayback(
                                    state.generation(),
                                    textToken
                            )
                    )
            );
            history.render(state.history());
        });
    }

    private static void applyPresentation(
            DialogueScreenState state,
            DialogueRootLayout root,
            DialogueSceneView scene
    ) {
        Presentation presentation = state.presentation().orElse(null);
        if (presentation == null) {
            scene.clearScene();
            root.setDialogueBoxLayout(DialogueBoxLayout.DEFAULT);
            return;
        }
        scene.apply(presentation);
        root.setDialogueBoxLayout(presentation.dialogueBox());
    }

    private void advanceFromUi() {
        DialogueHistoryView history = historyView;
        if (history != null && history.isOpen()) {
            return;
        }
        Minecraft.getInstance().execute(controller::advance);
    }

    private void setHistoryOpen(boolean open) {
        DialogueHistoryView history = historyView;
        if (history == null) {
            return;
        }
        Button entry = historyButton;
        if (entry != null) {
            entry.setVisibility(open ? View.GONE : View.VISIBLE);
        }
        if (open) {
            history.open();
        } else {
            if (history.isOpen()) {
                history.close();
            }
            DialogueRootLayout root = rootLayout;
            if (root != null) {
                root.requestFocus();
            }
        }
    }

    @Override
    // 释放播放资源并通知 controller 销毁当前会话。
    public void onDestroy() {
        DialogueSceneView scene = sceneView;
        if (scene != null) {
            scene.clearScene();
        }
        DialogueBoxView box = boxView;
        if (box != null) {
            box.clear();
        }
        rootLayout = null;
        sceneView = null;
        boxView = null;
        historyButton = null;
        historyView = null;
        renderedGeneration = Long.MIN_VALUE;
        Minecraft.getInstance().execute(
                () -> controller.onFragmentDestroyed(this)
        );
        super.onDestroy();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
