package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.R;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.BitmapFactory;
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
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;
import top.rookiestwo.maimai_dialogue.client.config.ClientPreferences;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueBoxLayout;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Objects;

public final class DialogueFragment extends Fragment implements ScreenCallback {
    private static final String HISTORY_BACK_STACK = "dialogue_history";
    private static final String HISTORY_ICON = "history_icon.png";
    private static final int HISTORY_BUTTON_SIZE_DP = 40;
    private static final int HISTORY_ICON_PADDING_DP = 4;
    private static final int HISTORY_ICON_COLOR = 0xFFFFFFFF;
    private static final int HISTORY_ICON_HOVERED_COLOR = 0xFFBFBFBF;
    private static final int HISTORY_ICON_PRESSED_COLOR = 0xFF808080;
    private static final String SKIP_ICON = "skip_icon.png";
    private static final float NORMAL_PLAYBACK_RATE = 1.0F;

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
    @Nullable
    private HoldToSkipButton skipButton;
    @Nullable
    private Image historyIconImage;
    @Nullable
    private Image skipIconImage;
    @Nullable
    private DialogueScreenState latestState;
    private long confirmationGeneration = Long.MIN_VALUE;
    private long autoAdvancePlaybackToken = Long.MIN_VALUE;
    private boolean fastForwarding;

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
        ClientPreferences preferences = ClientConfig.get();
        DialogueTypography typography = DialogueTypography.resolve(preferences);
        DialogueBoxView dialogueBox = new DialogueBoxView(
                context,
                option -> Minecraft.getInstance().execute(
                        () -> controller.selectOption(option)
                )
        );
        dialogueBox.setTypography(typography);
        ImageButton historyEntry = createHistoryButton(context);
        historyEntry.setOnClickListener(view -> openHistory());
        HoldToSkipButton skipEntry = createSkipButton(context);
        skipEntry.setHoldDurationMs(preferences.skipHoldDurationMs());
        DialogueSceneView scene = new DialogueSceneView(context);
        DialogueRootLayout root = new DialogueRootLayout(
                context,
                scene,
                dialogueBox,
                historyEntry,
                skipEntry,
                preferences
        );
        root.setOnClickListener(view -> advanceFromUi());
        root.setAdvanceAction(this::advanceFromUi);
        root.setFastForwardAction(this::setFastForwarding);
        root.setHistoryAction(this::openHistory);
        scene.setDialogueBoxStateConsumer(root::setDialogueBoxState);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.requestFocus();

        rootLayout = root;
        sceneView = scene;
        boxView = dialogueBox;
        historyButton = historyEntry;
        skipButton = skipEntry;
        render(controller.viewState());
        return root;
    }

    // 根据不可变 screen state 分发对话框和场景状态。
    public void render(DialogueScreenState state) {
        DialogueRootLayout root = rootLayout;
        DialogueSceneView scene = sceneView;
        DialogueBoxView box = boxView;
        ImageButton historyEntry = historyButton;
        HoldToSkipButton skipEntry = skipButton;
        if (root == null
                || scene == null
                || box == null
                || historyEntry == null
                || skipEntry == null) {
            return;
        }

        latestState = state;

        box.post(() -> {
            if (rootLayout != root
                    || sceneView != scene
                    || boxView != box
                    || historyButton != historyEntry
                    || skipButton != skipEntry) {
                return;
            }
            if (root.hasSkipConfirmation()
                    && confirmationGeneration != state.generation()) {
                dismissSkipConfirmation();
            }
            if (state.generation() != renderedGeneration) {
                renderedGeneration = state.generation();
                ThemeDefinition theme = state.theme().orElse(
                        ThemeDefinition.DEFAULT
                );
                box.reset(theme);
                skipEntry.applyTheme(theme);
                applyPresentation(state, root, scene);
            }
            root.setSkipAvailable(
                    state.canSkipToEnd() && !root.hasSkipConfirmation()
            );

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
            scheduleFastForwardAdvance(state);
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
        DialogueRootLayout root = rootLayout;
        if (root != null && root.hasSkipConfirmation()) {
            return;
        }
        Minecraft.getInstance().execute(controller::advance);
    }

    private void setFastForwarding(boolean fastForwarding) {
        DialogueRootLayout root = rootLayout;
        if (root != null && root.hasSkipConfirmation()) {
            fastForwarding = false;
        }
        this.fastForwarding = fastForwarding;
        if (!fastForwarding) {
            autoAdvancePlaybackToken = Long.MIN_VALUE;
        }
        float playbackRate = fastForwarding
                ? (float) ClientConfig.get().fastForwardMultiplier()
                : NORMAL_PLAYBACK_RATE;
        DialogueSceneView scene = sceneView;
        if (scene != null) {
            scene.setPlaybackRate(playbackRate);
        }
        DialogueBoxView box = boxView;
        if (box != null) {
            box.setPlaybackRate(playbackRate);
        }
        if (fastForwarding) {
            scheduleFastForwardAdvance(latestState);
        }
    }

    private void scheduleFastForwardAdvance(
            @Nullable DialogueScreenState state
    ) {
        long playbackToken = state == null
                ? Long.MIN_VALUE
                : state.scenePlayback()
                        .map(ScenePlayback::token)
                        .orElse(Long.MIN_VALUE);
        if (state == null
                || !shouldAutoAdvance(
                        fastForwarding,
                        state.playbackPhase(),
                        state.canSkipToEnd()
                )
                || playbackToken == Long.MIN_VALUE
                || autoAdvancePlaybackToken == playbackToken) {
            return;
        }
        autoAdvancePlaybackToken = playbackToken;
        Minecraft.getInstance().execute(() -> {
            DialogueScreenState current = latestState;
            if (current == null
                    || current.scenePlayback()
                            .map(ScenePlayback::token)
                            .orElse(Long.MIN_VALUE) != playbackToken
                    || !shouldAutoAdvance(
                            fastForwarding,
                            current.playbackPhase(),
                            current.canSkipToEnd()
                    )) {
                return;
            }
            controller.advance();
        });
    }

    static boolean shouldAutoAdvance(
            boolean fastForwarding,
            PlaybackPhase phase,
            boolean canSkipToEnd
    ) {
        return fastForwarding
                && phase == PlaybackPhase.READY
                && canSkipToEnd;
    }

    private void onSkipHoldCompleted() {
        DialogueRootLayout root = rootLayout;
        DialogueScreenState state = latestState;
        if (root == null || state == null || !state.canSkipToEnd()) {
            return;
        }
        root.cancelTransientInput();
        String summary = state.skipSummary().orElse(null);
        if (summary == null) {
            root.setSkipAvailable(false);
            Minecraft.getInstance().execute(controller::skipToEnd);
            return;
        }

        confirmationGeneration = state.generation();
        ThemeDefinition theme = state.theme().orElse(ThemeDefinition.DEFAULT);
        DialogueSkipConfirmationView confirmation =
                new DialogueSkipConfirmationView(
                        requireContext(),
                        summary,
                        this::dismissSkipConfirmation,
                        this::confirmSkipToEnd
                );
        confirmation.applyTheme(theme);
        confirmation.setTypography(
                DialogueTypography.resolve(ClientConfig.get()),
                theme
        );
        root.showSkipConfirmation(
                confirmation,
                this::dismissSkipConfirmation
        );
        root.setSkipAvailable(false);
    }

    private void confirmSkipToEnd() {
        DialogueRootLayout root = rootLayout;
        if (root != null) {
            root.dismissSkipConfirmation();
            root.setSkipAvailable(false);
        }
        confirmationGeneration = Long.MIN_VALUE;
        Minecraft.getInstance().execute(controller::skipToEnd);
    }

    private void dismissSkipConfirmation() {
        DialogueRootLayout root = rootLayout;
        if (root == null) {
            return;
        }
        root.dismissSkipConfirmation();
        confirmationGeneration = Long.MIN_VALUE;
        DialogueScreenState state = latestState;
        root.setSkipAvailable(
                state != null && state.canSkipToEnd()
        );
        root.requestFocus();
    }

    private void openHistory() {
        ImageButton entry = historyButton;
        if (entry == null || !entry.isEnabled()) {
            return;
        }
        DialogueRootLayout root = rootLayout;
        if (root != null) {
            root.cancelTransientInput();
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

    private ImageButton createHistoryButton(Context context) {
        ImageButton button = new ImageButton(context);
        String historyLabel = I18n.get("gui.maimai_dialogue.history");
        button.setContentDescription(historyLabel);
        button.setTooltipText(historyLabel);
        int size = button.dp(HISTORY_BUTTON_SIZE_DP);
        button.setMaxWidth(size);
        button.setMaxHeight(size);
        Image icon = loadIcon(HISTORY_ICON);
        historyIconImage = icon;
        configureIconButton(button, icon, HISTORY_ICON_PADDING_DP);
        return button;
    }

    private HoldToSkipButton createSkipButton(Context context) {
        HoldToSkipButton button = new HoldToSkipButton(
                context,
                this::onSkipHoldCompleted
        );
        String label = I18n.get("gui.maimai_dialogue.skip_to_end");
        button.setContentDescription(label);
        button.setTooltipText(label);
        Image icon = loadIcon(SKIP_ICON);
        skipIconImage = icon;
        configureIconButton(button, icon, HoldToSkipButton.ICON_PADDING_DP);
        return button;
    }

    private static void configureIconButton(
            ImageButton button,
            Image icon,
            int paddingDp
    ) {
        button.setBackground(null);
        button.setAdjustViewBounds(true);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = button.dp(paddingDp);
        button.setPadding(padding, padding, padding, padding);
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
    }

    private static Image loadIcon(String entry) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                MaiMaiDialogue.MOD_ID,
                "textures/" + entry
        );
        try (var stream = Minecraft.getInstance()
                .getResourceManager()
                .open(location);
             var bitmap = BitmapFactory.decodeStream(stream)) {
            Image image = Image.createTextureFromBitmap(bitmap);
            if (image == null) {
                throw new IOException("Failed to upload image: " + location);
            }
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to load Dialogue icon " + location,
                    exception
            );
        }
    }

    private static void releaseIcon(
            @Nullable ImageButton button,
            @Nullable Image image
    ) {
        if (button != null) {
            button.setImage(null);
        }
        if (image != null) {
            image.close();
        }
    }

    @Override
    // 页面切换时释放 View 资源，但保留 Dialogue session。
    public void onDestroyView() {
        DialogueSceneView scene = sceneView;
        if (scene != null) {
            scene.clearScene();
        }
        DialogueRootLayout root = rootLayout;
        if (root != null) {
            root.cancelTransientInput();
            root.dismissSkipConfirmation();
        }
        DialogueBoxView box = boxView;
        if (box != null) {
            box.clear();
        }
        releaseIcon(historyButton, historyIconImage);
        releaseIcon(skipButton, skipIconImage);
        rootLayout = null;
        sceneView = null;
        boxView = null;
        historyButton = null;
        skipButton = null;
        historyIconImage = null;
        skipIconImage = null;
        latestState = null;
        confirmationGeneration = Long.MIN_VALUE;
        autoAdvancePlaybackToken = Long.MIN_VALUE;
        fastForwarding = false;
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
