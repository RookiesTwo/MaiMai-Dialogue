package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.R;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.ImageButton;
import icyllis.modernui.widget.ImageView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueBoxLayout;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import javax.annotation.Nullable;
import java.util.Objects;

public final class DialogueFragment extends Fragment implements ScreenCallback {
    private static final String HISTORY_BACK_STACK = "dialogue_history";
    private static final String HISTORY_ICON = "history_icon.png";
    private static final int HISTORY_BUTTON_SIZE_DP = 40;
    private static final int HISTORY_ICON_PADDING_DP = 4;
    private static final int HISTORY_ICON_COLOR = 0xFFFFFFFF;
    private static final int HISTORY_ICON_HOVERED_COLOR = 0xFFBFBFBF;
    private static final int HISTORY_ICON_PRESSED_COLOR = 0xFF808080;

    private final ClientDialogueController controller;
    private long renderedGeneration = Long.MIN_VALUE;
    @Nullable
    private DialogueRootLayout rootLayout;
    @Nullable
    private DialogueSceneView sceneView;
    @Nullable
    private DialogueBoxView boxView;
    @Nullable
    private ImageButton historyButton;

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
        ImageButton historyEntry = createHistoryButton(context);
        historyEntry.setOnClickListener(view -> openHistory());
        DialogueSceneView scene = new DialogueSceneView(context);
        DialogueRootLayout root = new DialogueRootLayout(
                context,
                scene,
                dialogueBox,
                historyEntry
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
        render(controller.viewState());
        return root;
    }

    // 根据不可变 screen state 分发对话框和场景状态。
    public void render(DialogueScreenState state) {
        DialogueRootLayout root = rootLayout;
        DialogueSceneView scene = sceneView;
        DialogueBoxView box = boxView;
        ImageButton historyEntry = historyButton;
        if (root == null
                || scene == null
                || box == null
                || historyEntry == null) {
            return;
        }

        box.post(() -> {
            if (state.generation() != renderedGeneration) {
                renderedGeneration = state.generation();
                ThemeDefinition theme = state.theme().orElse(
                        ThemeDefinition.DEFAULT
                );
                box.reset(theme);
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
                    .map(ScenePlayback::token)
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
        Minecraft.getInstance().execute(controller::advance);
    }

    private void openHistory() {
        ImageButton entry = historyButton;
        if (entry == null || !entry.isEnabled()) {
            return;
        }
        entry.setEnabled(false);
        getParentFragmentManager()
                .beginTransaction()
                .add(
                        getId(),
                        new DialogueHistoryFragment(),
                        HISTORY_BACK_STACK
                )
                .addToBackStack(HISTORY_BACK_STACK)
                .commit();
    }

    void onHistoryClosed() {
        ImageButton entry = historyButton;
        if (entry != null) {
            entry.setEnabled(true);
        }
        DialogueRootLayout root = rootLayout;
        if (root != null) {
            root.requestFocus();
        }
    }

    @SuppressWarnings("deprecation")
    private static ImageButton createHistoryButton(Context context) {
        ImageButton button = new ImageButton(context);
        String historyLabel = I18n.get("gui.maimai_dialogue.history");
        button.setContentDescription(historyLabel);
        button.setTooltipText(historyLabel);
        button.setBackground(null);
        button.setAdjustViewBounds(true);
        int size = button.dp(HISTORY_BUTTON_SIZE_DP);
        button.setMaxWidth(size);
        button.setMaxHeight(size);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = button.dp(HISTORY_ICON_PADDING_DP);
        button.setPadding(padding, padding, padding, padding);

        Image icon = Image.create(MaiMaiDialogue.MOD_ID, HISTORY_ICON);
        button.setImage(icon);
        button.setImageTintList(new ColorStateList(
                new int[][]{
                        new int[]{R.attr.state_pressed},
                        new int[]{R.attr.state_hovered},
                        StateSet.WILD_CARD
                },
                new int[]{
                        HISTORY_ICON_PRESSED_COLOR,
                        HISTORY_ICON_HOVERED_COLOR,
                        HISTORY_ICON_COLOR
                }
        ));
        if (button.getDrawable() instanceof ImageDrawable drawable) {
            // The source icon is high resolution and should scale smoothly.
            drawable.setFilter(true);
        }
        return button;
    }

    @Override
    // 页面切换时释放 View 资源，但保留 Dialogue session。
    public void onDestroyView() {
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
        renderedGeneration = Long.MIN_VALUE;
        super.onDestroyView();
    }

    @Override
    // Screen 真正销毁时才通知 controller 结束当前会话。
    public void onDestroy() {
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
