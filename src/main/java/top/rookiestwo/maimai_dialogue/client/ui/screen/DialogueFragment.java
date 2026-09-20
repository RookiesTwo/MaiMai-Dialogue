package top.rookiestwo.maimai_dialogue.client.ui.screen;

import top.rookiestwo.maimai_dialogue.client.controller.DialogueScreenHandle;
import icyllis.modernui.mc.MuiModApi;

import top.rookiestwo.maimai_dialogue.client.controller.DialogueUiActions;
import top.rookiestwo.maimai_dialogue.client.ui.box.DialogueBoxView;
import top.rookiestwo.maimai_dialogue.client.ui.history.DialogueHistoryFragment;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueSceneView;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueImageSource;
import top.rookiestwo.maimai_dialogue.client.ui.style.DialogueTypography;

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
import icyllis.modernui.view.KeyEvent;
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
import top.rookiestwo.maimai_dialogue.presentation.DialogueBoxLayout;
import top.rookiestwo.maimai_dialogue.presentation.Presentation;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Objects;

public final class DialogueFragment extends Fragment implements ScreenCallback, DialogueScreenHandle {
    /** DISPLAY_ONLY keeps the corner controls visible without history or skip interaction. */
    public enum CornerControls { INTERACTIVE, DISPLAY_ONLY }

    private static final String HISTORY_BACK_STACK = "dialogue_history";
    private static final String HISTORY_ICON = "history_icon.png";
    private static final int HISTORY_BUTTON_SIZE_DP = 40;
    private static final int HISTORY_ICON_PADDING_DP = 4;
    private static final int HISTORY_ICON_COLOR = 0xFFFFFFFF;
    private static final int HISTORY_ICON_HOVERED_COLOR = 0xFFBFBFBF;
    private static final int HISTORY_ICON_PRESSED_COLOR = 0xFF808080;
    private static final String SKIP_ICON = "skip_icon.png";
    private static final float NORMAL_PLAYBACK_RATE = 1.0F;

    private final DialogueUiActions controller;
    private final CornerControls cornerControls;
    private final DialogueImageSource images;
    private final FastForwardPlayback fastForward;
    private final DialogueConfirmations confirmations;
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

    public DialogueFragment(DialogueUiActions controller) {
        this(controller, CornerControls.INTERACTIVE);
    }

    public DialogueFragment(DialogueUiActions controller, CornerControls cornerControls) {
        this(controller, cornerControls, DialogueImageSource.RESOURCES);
    }

    /** Owns the scoped image source until this Fragment is destroyed. Existing constructors use game resources. */
    public DialogueFragment(DialogueUiActions controller, CornerControls cornerControls, DialogueImageSource images) {
        this.controller = controller;
        this.images = Objects.requireNonNull(images);
        this.cornerControls = Objects.requireNonNull(cornerControls, "cornerControls");
        fastForward = new FastForwardPlayback(() -> latestState, controller);
        confirmations = new DialogueConfirmations(
                () -> rootLayout, () -> latestState, this::requireContext, controller
        );
    }

    @Override
    public void show() {
        Minecraft.getInstance().setScreen(MuiModApi.get().createScreen(
                this, this, null, "MaiMai Dialogue"
        ));
    }

    @Override
    public void close() {
        Minecraft.getInstance().setScreen(null);
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
        DialogueScreenState initialState = controller.viewState();
        DialogueTypography typography = DialogueTypography.resolve(preferences);
        DialogueBoxView dialogueBox = new DialogueBoxView(
                context,
                option -> DialogueUiDispatch.toClient(
                        () -> controller.selectOption(option)
                )
        );
        // The first render is queued; an idle preview must already be hidden before attachment.
        dialogueBox.setVisibility(dialogueBoxVisibility(initialState));
        dialogueBox.setTypography(typography);
        ImageButton historyEntry = createHistoryButton(context);
        historyEntry.setOnClickListener(view -> openHistory());
        HoldToSkipButton skipEntry = createSkipButton(context);
        skipEntry.setHoldDurationMs(preferences.skipHoldDurationMs());
        DialogueSceneView scene = new DialogueSceneView(context, images);
        DialogueRootLayout root = new DialogueRootLayout(
                context,
                scene,
                dialogueBox,
                historyEntry,
                skipEntry,
                preferences
        );
        if (cornerControls == CornerControls.DISPLAY_ONLY) {
            root.setCornerControlsInteractive(false);
            // Keep decorative controls readable over the editor's light transparency background.
            historyEntry.setImageTintList(ColorStateList.valueOf(0xFF667080));
            skipEntry.setImageTintList(ColorStateList.valueOf(0xFF667080));
        }
        dialogueBox.setOptionsExpandedChanged(root::setOptionsExpanded);
        dialogueBox.setAdvanceAction(this::advanceFromUi);
        root.setOnClickListener(view -> advanceFromUi());
        root.setAdvanceAction(this::advanceFromUi);
        root.setFastForwardAction(this::setFastForwarding);
        root.setHistoryAction(this::openHistory);
        root.setExitAction(confirmations::showExitConfirmation);
        scene.setDialogueBoxStateConsumer(root::setDialogueBoxState);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        if (cornerControls == CornerControls.INTERACTIVE || initialState.scenePlayback().isPresent()) {
            root.requestFocus();
        }

        rootLayout = root;
        sceneView = scene;
        boxView = dialogueBox;
        historyButton = historyEntry;
        skipButton = skipEntry;
        render(initialState);
        return root;
    }

    private int dialogueBoxVisibility(DialogueScreenState state) {
        return cornerControls == CornerControls.DISPLAY_ONLY && state.presentation().isEmpty()
                ? View.GONE : View.VISIBLE;
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

        DialogueUiDispatch.toView(box,
                () -> rootLayout == root
                        && sceneView == scene
                        && boxView == box
                        && historyButton == historyEntry
                        && skipButton == skipEntry,
                () -> {
                    box.setVisibility(dialogueBoxVisibility(state));
                    confirmations.render(state);
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
                            state.canSkipToEnd() && !root.hasConfirmation()
                    );

                    long audioToken = state.scenePlayback().map(ScenePlayback::token).orElse(Long.MIN_VALUE);
                    scene.setPlaybackProgress(elapsed -> DialogueUiDispatch.toClient(
                            () -> controller.audioFrame(state.generation(), audioToken, elapsed)));
                    box.setTextRevealListener((end, audible) -> DialogueUiDispatch.toClient(
                            () -> controller.textRevealed(state.generation(), audioToken, end, audible)));
                    state.scenePlayback().ifPresent(playback ->
                            scene.renderPlayback(
                                    playback,
                                    state.playbackSkipped(),
                                    () -> DialogueUiDispatch.toClient(
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
                            () -> DialogueUiDispatch.toClient(
                                    () -> controller.completeTextPlayback(
                                            state.generation(),
                                            textToken
                                    )
                            )
                    );
                    fastForward.schedule(state);
                });
    }

    /** Refresh the current View's metrics/layout without restarting its session or playback. */
    public void refreshViewport() {
        DialogueRootLayout root = rootLayout;
        if (root == null) return;
        DialogueUiDispatch.toView(root, () -> rootLayout == root && root.isAttachedToWindow(),
                root::requestViewportRefresh);
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
        if (root != null && root.hasConfirmation()) {
            return;
        }
        DialogueUiDispatch.toClient(controller::advance);
    }

    private void setFastForwarding(boolean fastForwarding) {
        DialogueRootLayout root = rootLayout;
        if (root != null && root.hasConfirmation()) {
            fastForwarding = false;
        }
        fastForward.setEnabled(fastForwarding);
        boolean audioFastForward = fastForwarding;
        DialogueUiDispatch.toClient(() -> controller.setAudioFastForward(this, audioFastForward));
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
            fastForward.schedule(latestState);
        }
    }

    private void openHistory() {
        if (cornerControls == CornerControls.DISPLAY_ONLY) return;
        ImageButton entry = historyButton;
        if (entry == null || !entry.isEnabled()) {
            return;
        }
        DialogueRootLayout root = rootLayout;
        if (root != null) {
            root.cancelTransientInput();
        }
        entry.setEnabled(false);
        DialogueUiDispatch.toClient(() -> controller.setAudioHistoryOpen(this, true));
        getParentFragmentManager()
                .beginTransaction()
                .add(
                        getId(),
                        new DialogueHistoryFragment(
                                controller::viewState,
                                this::onHistoryClosed
                        ),
                        HISTORY_BACK_STACK
                )
                .addToBackStack(HISTORY_BACK_STACK)
                .commit();
    }

    void onHistoryClosed() {
        DialogueUiDispatch.toClient(() -> controller.setAudioHistoryOpen(this, false));
        ImageButton entry = historyButton;
        if (entry != null) {
            entry.setEnabled(cornerControls == CornerControls.INTERACTIVE);
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
                confirmations::onSkipHoldCompleted
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
            root.dismissConfirmation();
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
        confirmations.reset();
        fastForward.setEnabled(false);
        DialogueUiDispatch.toClient(() -> {
            controller.setAudioFastForward(this, false);
            controller.setAudioHistoryOpen(this, false);
        });
        renderedGeneration = Long.MIN_VALUE;
        super.onDestroyView();
    }

    @Override
    // Screen 真正销毁时才通知 controller 结束当前会话。
    public void onDestroy() {
        images.close();
        DialogueUiDispatch.toClient(
                () -> controller.onScreenDestroyed(this)
        );
        super.onDestroy();
    }

    @Override
    public boolean isBackKey(int keyCode, @NonNull KeyEvent event) {
        return keyCode == KeyEvent.KEY_ESCAPE;
    }

    @Override
    public boolean shouldClose() {
        return confirmations.shouldClose();
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
