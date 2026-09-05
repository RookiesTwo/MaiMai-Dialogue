package top.rookiestwo.maimai_dialogue.client.config.ui;

import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;
import top.rookiestwo.maimai_dialogue.client.config.ClientControlAction;
import top.rookiestwo.maimai_dialogue.client.config.ClientPreferences;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

import static top.rookiestwo.maimai_dialogue.client.config.ui.ConfigWidgets.*;

public final class ClientConfigFragment extends Fragment
        implements ScreenCallback {

    private final FontPicker fonts = new FontPicker(() -> this.root, this::requireContext);
    private final KeyBindingEditor keys = new KeyBindingEditor(() -> this.root);
    private final NumericOptionEditor numeric = new NumericOptionEditor(fonts::refreshFont);

    @Nullable
    private ConfigRoot root;
    private boolean resetArmed;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable DataSet savedInstanceState
    ) {
        Context context = requireContext();
        ConfigRoot pageRoot = new ConfigRoot(context);
        root = pageRoot;

        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        int pagePadding = page.dp(PAGE_PADDING_DP);
        page.setPadding(pagePadding, pagePadding, pagePadding, pagePadding);

        page.addView(createHeader(context), matchWidthWrapHeight());

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);
        LinearLayout content = new MaxWidthLinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(createPlaybackCard(context), cardParams(content));
        content.addView(createControlsCard(context), cardParams(content));
        content.addView(createAppearanceCard(context), cardParams(content));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL
        ));
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0F
        ));
        pageRoot.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        pageRoot.setFocusable(true);
        pageRoot.setFocusableInTouchMode(true);
        pageRoot.requestFocus();
        refreshAll();
        return pageRoot;
    }

    private View createHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        Button back = createOutlinedButton(context);
        back.setText(I18n.get("gui.maimai_dialogue.config.back"));
        back.setOnClickListener(view -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen != null) {
                Minecraft.getInstance().screen.onClose();
            }
        }));
        header.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(context);
        title.setText(I18n.get("gui.maimai_dialogue.config.title"));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0F
        ));

        Button reset = createOutlinedButton(context);
        reset.setText(I18n.get("gui.maimai_dialogue.config.reset_all"));
        reset.setOnClickListener(view -> {
            if (!resetArmed) {
                resetArmed = true;
                reset.setText(I18n.get(
                        "gui.maimai_dialogue.config.reset_confirm"
                ));
                return;
            }
            resetArmed = false;
            ClientConfig.resetAll();
            refreshAll();
            reset.setText(I18n.get(
                    "gui.maimai_dialogue.config.reset_all"
            ));
        });
        header.addView(reset, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return header;
    }

    private View createPlaybackCard(Context context) {
        LinearLayout card = createCard(context, "playback");
        numeric.addDecimalOption(
                card,
                "fast_forward_multiplier",
                ClientPreferences.MIN_FAST_FORWARD_MULTIPLIER,
                ClientPreferences.MAX_FAST_FORWARD_MULTIPLIER,
                0.5,
                ClientPreferences.DEFAULT_FAST_FORWARD_MULTIPLIER,
                () -> ClientConfig.VALUES.fastForwardMultiplier.get(),
                ClientConfig.VALUES.fastForwardMultiplier::set
        );
        numeric.addIntegerOption(
                card,
                "default_typewriter_interval",
                ClientPreferences.MIN_TYPEWRITER_INTERVAL_MS,
                ClientPreferences.MAX_TYPEWRITER_INTERVAL_MS,
                5,
                ClientPreferences.DEFAULT_TYPEWRITER_INTERVAL_MS,
                () -> ClientConfig.VALUES.defaultTypewriterIntervalMs.get(),
                value -> ClientConfig.VALUES.defaultTypewriterIntervalMs.set(
                        (int) value
                )
        );
        numeric.addIntegerOption(
                card,
                "skip_hold_duration",
                ClientPreferences.MIN_SKIP_HOLD_DURATION_MS,
                ClientPreferences.MAX_SKIP_HOLD_DURATION_MS,
                100,
                ClientPreferences.DEFAULT_SKIP_HOLD_DURATION_MS,
                () -> ClientConfig.VALUES.skipHoldDurationMs.get(),
                value -> ClientConfig.VALUES.skipHoldDurationMs.set((int) value)
        );
        return card;
    }

    private View createControlsCard(Context context) {
        LinearLayout card = createCard(context, "controls");
        keys.addKeyOption(card, ClientControlAction.FAST_FORWARD);
        keys.addKeyOption(card, ClientControlAction.ADVANCE);
        keys.addKeyOption(card, ClientControlAction.SKIP);
        keys.addKeyOption(card, ClientControlAction.HISTORY);
        TextView error = new TextView(context);
        error.setTextColor(ERROR_COLOR);
        error.setVisibility(View.GONE);
        int padding = error.dp(8);
        error.setPadding(padding, padding, padding, padding);
        card.addView(error, matchWidthWrapHeight());
        keys.setErrorView(error);
        return card;
    }

    private View createAppearanceCard(Context context) {
        LinearLayout card = createCard(context, "appearance");
        LinearLayout fontRow = createOptionRow(context, "font_family");
        Button chooseFont = createOutlinedButton(context);
        chooseFont.setOnClickListener(view -> fonts.showFontPicker());
        fontRow.addView(chooseFont, controlParams(fontRow));
        card.addView(fontRow, matchWidthWrapHeight());

        TextView preview = new TextView(context);
        preview.setText(I18n.get("gui.maimai_dialogue.config.font_preview"));
        preview.setGravity(Gravity.CENTER);
        int previewPadding = preview.dp(18);
        preview.setPadding(
                previewPadding,
                previewPadding,
                previewPadding,
                previewPadding
        );
        card.addView(preview, matchWidthWrapHeight());
        fonts.bind(chooseFont, preview);

        numeric.addDecimalOption(
                card,
                "font_scale",
                ClientPreferences.MIN_FONT_SCALE,
                ClientPreferences.MAX_FONT_SCALE,
                0.05,
                ClientPreferences.DEFAULT_FONT_SCALE,
                () -> ClientConfig.VALUES.fontScale.get(),
                ClientConfig.VALUES.fontScale::set
        );
        return card;
    }

    private void refreshAll() {
        numeric.refreshAll();
        keys.refreshKeys();
        fonts.refreshFont();
    }

    @Override
    public void onDestroyView() {
        ConfigRoot pageRoot = root;
        if (pageRoot != null) {
            pageRoot.clearFocus();
        }
        ClientConfig.saveIfDirtyAsync();
        root = null;
        fonts.clear();
        keys.clear();
        numeric.clear();
        super.onDestroyView();
    }

    @Override
    public boolean isBackKey(int keyCode, @NonNull KeyEvent event) {
        if (fonts.isOpen() || keys.isCapturing()) {
            return false;
        }
        return ScreenCallback.super.isBackKey(keyCode, event);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean hasDefaultBackground() {
        return true;
    }

    @Override
    public boolean shouldBlurBackground() {
        return true;
    }

    private final class ConfigRoot extends FrameLayout {
        private ConfigRoot(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
            if (fonts.isOpen()
                    && event.getKeyCode() == KeyEvent.KEY_ESCAPE
                    && event.getAction() == KeyEvent.ACTION_UP) {
                fonts.dismissFontPicker();
                return true;
            }
            if (keys.isCapturing()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && !event.isCanceled()) {
                    keys.captureKey(event.getKeyCode());
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
    }

    private static final class MaxWidthLinearLayout extends LinearLayout {
        private MaxWidthLinearLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = Math.min(
                    MeasureSpec.getSize(widthMeasureSpec),
                    dp(CONTENT_MAX_WIDTH_DP)
            );
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    heightMeasureSpec
            );
        }
    }
}
