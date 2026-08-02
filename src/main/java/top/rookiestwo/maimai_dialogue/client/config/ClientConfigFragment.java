package top.rookiestwo.maimai_dialogue.client.config;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.SeekBar;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.ModConfigSpec;
import top.rookiestwo.maimai_dialogue.client.DialogueTypography;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public final class ClientConfigFragment extends Fragment
        implements ScreenCallback {
    private static final int CONTENT_MAX_WIDTH_DP = 780;
    private static final int PAGE_PADDING_DP = 18;
    private static final int CARD_COLOR = 0xCC17191D;
    private static final int CARD_STROKE_COLOR = 0xFF555B66;
    private static final int BUTTON_COLOR = 0x2017191D;
    private static final int BUTTON_HOVER_COLOR = 0x40555B66;
    private static final int BUTTON_PRESSED_COLOR = 0x70555B66;
    private static final int BUTTON_HOVER_STROKE_COLOR = 0xFF8E97A7;
    private static final int OVERLAY_COLOR = 0xB3000000;
    private static final int ERROR_COLOR = 0xFFFF6B6B;

    private final List<Runnable> refreshers = new ArrayList<>();
    private final EnumMap<ClientControlAction, Button> keyButtons =
            new EnumMap<>(ClientControlAction.class);

    @Nullable
    private ConfigRoot root;
    @Nullable
    private TextView keyError;
    @Nullable
    private TextView fontPreview;
    @Nullable
    private Button fontButton;
    @Nullable
    private View fontOverlay;
    @Nullable
    private ClientControlAction capturingAction;
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
        addDecimalOption(
                card,
                "fast_forward_multiplier",
                1.0,
                32.0,
                0.5,
                ClientPreferences.DEFAULT_FAST_FORWARD_MULTIPLIER,
                () -> ClientConfig.VALUES.fastForwardMultiplier.get(),
                ClientConfig.VALUES.fastForwardMultiplier::set
        );
        addIntegerOption(
                card,
                "default_typewriter_interval",
                0,
                1_000,
                5,
                ClientPreferences.DEFAULT_TYPEWRITER_INTERVAL_MS,
                () -> ClientConfig.VALUES.defaultTypewriterIntervalMs.get(),
                value -> ClientConfig.VALUES.defaultTypewriterIntervalMs.set(
                        (int) value
                )
        );
        addIntegerOption(
                card,
                "skip_hold_duration",
                200,
                3_000,
                100,
                ClientPreferences.DEFAULT_SKIP_HOLD_DURATION_MS,
                () -> ClientConfig.VALUES.skipHoldDurationMs.get(),
                value -> ClientConfig.VALUES.skipHoldDurationMs.set((int) value)
        );
        return card;
    }

    private View createControlsCard(Context context) {
        LinearLayout card = createCard(context, "controls");
        addKeyOption(card, ClientControlAction.FAST_FORWARD);
        addKeyOption(card, ClientControlAction.ADVANCE);
        addKeyOption(card, ClientControlAction.SKIP);
        addKeyOption(card, ClientControlAction.HISTORY);
        TextView error = new TextView(context);
        error.setTextColor(ERROR_COLOR);
        error.setVisibility(View.GONE);
        int padding = error.dp(8);
        error.setPadding(padding, padding, padding, padding);
        card.addView(error, matchWidthWrapHeight());
        keyError = error;
        return card;
    }

    private View createAppearanceCard(Context context) {
        LinearLayout card = createCard(context, "appearance");
        LinearLayout fontRow = createOptionRow(context, "font_family");
        Button chooseFont = createOutlinedButton(context);
        chooseFont.setOnClickListener(view -> showFontPicker());
        fontRow.addView(chooseFont, controlParams(fontRow));
        card.addView(fontRow, matchWidthWrapHeight());
        fontButton = chooseFont;

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
        fontPreview = preview;

        addDecimalOption(
                card,
                "font_scale",
                0.5,
                2.0,
                0.05,
                ClientPreferences.DEFAULT_FONT_SCALE,
                () -> ClientConfig.VALUES.fontScale.get(),
                ClientConfig.VALUES.fontScale::set
        );
        refreshers.add(this::refreshFont);
        return card;
    }

    private LinearLayout createCard(Context context, String category) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = card.dp(12);
        card.setPadding(padding, padding, padding, padding);
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(CARD_COLOR);
        background.setCornerRadius(card.dp(5));
        background.setStroke(card.dp(1), CARD_STROKE_COLOR);
        card.setBackground(background);

        TextView title = new TextView(context);
        title.setText(I18n.get(
                "gui.maimai_dialogue.config.category." + category
        ));
        title.setTextSize(18);
        int titlePadding = title.dp(8);
        title.setPadding(
                titlePadding,
                titlePadding,
                titlePadding,
                titlePadding
        );
        card.addView(title, matchWidthWrapHeight());
        return card;
    }

    private LinearLayout createOptionRow(Context context, String option) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padding = row.dp(6);
        row.setPadding(padding, padding, padding, padding);
        TextView label = new TextView(context);
        label.setText(I18n.get(
                "gui.maimai_dialogue.config.option." + option
        ));
        String tooltipKey =
                "gui.maimai_dialogue.config.option." + option + ".tooltip";
        if (I18n.exists(tooltipKey)) {
            label.setTooltipText(I18n.get(tooltipKey));
        }
        row.addView(label, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0F
        ));
        return row;
    }

    private void addIntegerOption(
            LinearLayout card,
            String option,
            int min,
            int max,
            int step,
            int defaultValue,
            DoubleSupplier getter,
            DoubleConsumer setter
    ) {
        addNumericOption(
                card,
                option,
                min,
                max,
                step,
                defaultValue,
                getter,
                setter,
                true
        );
    }

    private void addDecimalOption(
            LinearLayout card,
            String option,
            double min,
            double max,
            double sliderStep,
            double defaultValue,
            DoubleSupplier getter,
            DoubleConsumer setter
    ) {
        addNumericOption(
                card,
                option,
                min,
                max,
                sliderStep,
                defaultValue,
                getter,
                setter,
                false
        );
    }

    private void addNumericOption(
            LinearLayout card,
            String option,
            double min,
            double max,
            double sliderStep,
            double defaultValue,
            DoubleSupplier getter,
            DoubleConsumer setter,
            boolean integer
    ) {
        Context context = card.getContext();
        LinearLayout row = createOptionRow(context, option);
        SeekBar slider = new SeekBar(context);
        int sliderMax = (int) Math.round((max - min) / sliderStep);
        slider.setMax(sliderMax);
        row.addView(slider, new LinearLayout.LayoutParams(
                row.dp(180),
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText input = new EditText(context);
        input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        input.setMinWidth(input.dp(76));
        row.addView(input, new LinearLayout.LayoutParams(
                row.dp(88),
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button reset = createOutlinedButton(context);
        reset.setText(I18n.get("gui.maimai_dialogue.config.reset"));
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        resetParams.setMargins(row.dp(12), 0, 0, 0);
        row.addView(reset, resetParams);
        card.addView(row, matchWidthWrapHeight());

        boolean[] updating = {false};
        Runnable refresh = () -> {
            double value = getter.getAsDouble();
            updating[0] = true;
            slider.setProgress((int) Math.round((value - min) / sliderStep));
            input.setText(formatNumber(value, integer));
            updating[0] = false;
        };
        Consumer<Double> commit = value -> {
            double clamped = Math.clamp(value, min, max);
            if (integer) {
                clamped = Math.rint(clamped);
            }
            setter.accept(clamped);
            ClientConfig.changed();
            refresh.run();
            refreshFont();
        };
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(
                    SeekBar seekBar,
                    int progress,
                    boolean fromUser
            ) {
                if (fromUser && !updating[0]) {
                    commit.accept(min + progress * sliderStep);
                }
            }
        });
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus || updating[0]) {
                return;
            }
            try {
                commit.accept(Double.parseDouble(input.getText().toString()));
            } catch (NumberFormatException exception) {
                refresh.run();
            }
        });
        reset.setOnClickListener(view -> commit.accept(defaultValue));
        refreshers.add(refresh);
    }

    private void addKeyOption(
            LinearLayout card,
            ClientControlAction action
    ) {
        Context context = card.getContext();
        String name = action.name().toLowerCase(Locale.ROOT) + "_key";
        LinearLayout row = createOptionRow(context, name);
        Button binding = createOutlinedButton(context);
        binding.setOnClickListener(view -> beginKeyCapture(action));
        row.addView(binding, controlParams(row));
        Button clear = createOutlinedButton(context);
        clear.setText(I18n.get("gui.maimai_dialogue.config.unbind"));
        clear.setOnClickListener(view -> setKey(action, DialogueKey.UNBOUND));
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        clearParams.setMargins(row.dp(8), 0, 0, 0);
        row.addView(clear, clearParams);
        Button reset = createOutlinedButton(context);
        reset.setText(I18n.get("gui.maimai_dialogue.config.reset"));
        reset.setOnClickListener(view -> setKey(action, defaultKey(action)));
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        resetParams.setMargins(row.dp(8), 0, 0, 0);
        row.addView(reset, resetParams);
        card.addView(row, matchWidthWrapHeight());
        keyButtons.put(action, binding);
        refreshers.add(() -> refreshKey(action));
    }

    private void beginKeyCapture(ClientControlAction action) {
        capturingAction = action;
        TextView error = keyError;
        if (error != null) {
            error.setVisibility(View.GONE);
        }
        refreshKeys();
        Button button = keyButtons.get(action);
        if (button != null) {
            button.setText(I18n.get(
                    "gui.maimai_dialogue.config.press_key"
            ));
        }
        ConfigRoot pageRoot = root;
        if (pageRoot != null) {
            pageRoot.requestFocus();
        }
    }

    private void captureKey(int keyCode) {
        ClientControlAction action = capturingAction;
        if (action == null) {
            return;
        }
        if (keyCode == KeyEvent.KEY_ESCAPE) {
            capturingAction = null;
            refreshKeys();
            return;
        }
        DialogueKey key;
        if (keyCode == KeyEvent.KEY_BACKSPACE
                || keyCode == KeyEvent.KEY_DELETE) {
            key = DialogueKey.UNBOUND;
        } else {
            try {
                key = DialogueKey.fromKeyCode(keyCode);
            } catch (IllegalArgumentException exception) {
                showKeyError("gui.maimai_dialogue.config.key_invalid");
                return;
            }
        }
        setKey(action, key);
    }

    private void setKey(ClientControlAction action, DialogueKey key) {
        ClientPreferences preferences = ClientConfig.get();
        if (!key.isUnbound()) {
            for (ClientControlAction candidate : ClientControlAction.values()) {
                if (candidate != action
                        && preferences.key(candidate).name().equals(key.name())) {
                    showKeyError("gui.maimai_dialogue.config.key_conflict");
                    return;
                }
            }
        }
        configValue(action).set(key.name());
        capturingAction = null;
        ClientConfig.changed();
        refreshKeys();
    }

    private void showKeyError(String translationKey) {
        TextView error = keyError;
        if (error != null) {
            error.setText(I18n.get(translationKey));
            error.setVisibility(View.VISIBLE);
        }
    }

    private void refreshKeys() {
        for (ClientControlAction action : ClientControlAction.values()) {
            refreshKey(action);
        }
    }

    private void refreshKey(ClientControlAction action) {
        Button button = keyButtons.get(action);
        if (button == null || capturingAction == action) {
            return;
        }
        button.setText(keyDisplayName(ClientConfig.get().key(action)));
    }

    private void refreshFont() {
        ClientPreferences preferences = ClientConfig.get();
        Button button = fontButton;
        if (button != null) {
            button.setText(preferences.fontFamily().isBlank()
                    ? I18n.get("gui.maimai_dialogue.config.follow_modernui")
                    : preferences.fontFamily()
            );
        }
        TextView preview = fontPreview;
        if (preview != null) {
            DialogueTypography.resolve(preferences).apply(preview, 18.0F);
        }
    }

    private void showFontPicker() {
        ConfigRoot pageRoot = root;
        if (pageRoot == null || fontOverlay != null) {
            return;
        }
        Context context = requireContext();
        FrameLayout overlay = new FrameLayout(context);
        ShapeDrawable overlayBackground = new ShapeDrawable();
        overlayBackground.setColor(OVERLAY_COLOR);
        overlay.setBackground(overlayBackground);
        overlay.setClickable(true);

        LinearLayout panel = createCard(context, "font_picker");
        EditText search = new EditText(context);
        search.setHint(I18n.get("gui.maimai_dialogue.config.font_search"));
        panel.addView(search, matchWidthWrapHeight());

        ScrollView scroll = new ScrollView(context);
        LinearLayout results = new LinearLayout(context);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results, matchWidthWrapHeight());
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0F
        ));

        Button close = createOutlinedButton(context);
        close.setText(I18n.get("gui.maimai_dialogue.config.close"));
        close.setOnClickListener(view -> dismissFontPicker());
        panel.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                Math.min(panel.dp(620), panel.dp(CONTENT_MAX_WIDTH_DP)),
                panel.dp(560),
                Gravity.CENTER
        );
        overlay.addView(panel, panelParams);
        pageRoot.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        fontOverlay = overlay;

        List<String> families = DialogueTypography.availableFontFamilies();
        Consumer<String> rebuild = query -> {
            String needle = query.strip().toLowerCase(Locale.ROOT);
            results.removeAllViews();
            addFontChoice(results, "");
            for (String family : families) {
                if (needle.isEmpty()
                        || family.toLowerCase(Locale.ROOT).contains(needle)) {
                    addFontChoice(results, family);
                }
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence value,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence value,
                    int start,
                    int before,
                    int count
            ) {
                rebuild.accept(value.toString());
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        rebuild.accept("");
        search.requestFocus();
    }

    private void addFontChoice(LinearLayout results, String family) {
        Button choice = createOutlinedButton(results.getContext());
        choice.setText(family.isBlank()
                ? I18n.get("gui.maimai_dialogue.config.follow_modernui")
                : family
        );
        choice.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        choice.setTypeface(DialogueTypography.resolveTypeface(family));
        choice.setOnClickListener(view -> {
            ClientConfig.VALUES.fontFamily.set(family);
            ClientConfig.changed();
            refreshFont();
            dismissFontPicker();
        });
        results.addView(choice, matchWidthWrapHeight());
    }

    private void dismissFontPicker() {
        ConfigRoot pageRoot = root;
        View overlay = fontOverlay;
        fontOverlay = null;
        if (pageRoot != null && overlay != null) {
            pageRoot.removeView(overlay);
            pageRoot.requestFocus();
        }
    }

    private void refreshAll() {
        for (Runnable refresher : refreshers) {
            refresher.run();
        }
        refreshKeys();
        refreshFont();
    }

    private static String formatNumber(double value, boolean integer) {
        if (integer) {
            return Integer.toString((int) Math.round(value));
        }
        return BigDecimal.valueOf(value)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String keyDisplayName(DialogueKey key) {
        if (key.isUnbound()) {
            return I18n.get("gui.maimai_dialogue.config.unbound");
        }
        if (key.isControl()) {
            return I18n.get("gui.maimai_dialogue.config.control");
        }
        return I18n.get(key.name());
    }

    private static DialogueKey defaultKey(ClientControlAction action) {
        return switch (action) {
            case FAST_FORWARD -> ClientPreferences.DEFAULT_FAST_FORWARD_KEY;
            case ADVANCE -> ClientPreferences.DEFAULT_ADVANCE_KEY;
            case SKIP -> ClientPreferences.DEFAULT_SKIP_KEY;
            case HISTORY -> ClientPreferences.DEFAULT_HISTORY_KEY;
        };
    }

    private static ModConfigSpec.ConfigValue<String> configValue(
            ClientControlAction action
    ) {
        return switch (action) {
            case FAST_FORWARD -> ClientConfig.VALUES.fastForwardKey;
            case ADVANCE -> ClientConfig.VALUES.advanceKey;
            case SKIP -> ClientConfig.VALUES.skipKey;
            case HISTORY -> ClientConfig.VALUES.historyKey;
        };
    }

    private static Button createOutlinedButton(Context context) {
        Button button = new Button(context);
        StateListDrawable background = new StateListDrawable();
        background.addState(
                new int[]{R.attr.state_pressed},
                createButtonShape(
                        button,
                        BUTTON_PRESSED_COLOR,
                        BUTTON_HOVER_STROKE_COLOR
                )
        );
        background.addState(
                new int[]{R.attr.state_hovered},
                createButtonShape(
                        button,
                        BUTTON_HOVER_COLOR,
                        BUTTON_HOVER_STROKE_COLOR
                )
        );
        background.addState(
                StateSet.WILD_CARD,
                createButtonShape(button, BUTTON_COLOR, CARD_STROKE_COLOR)
        );
        button.setBackground(background);
        return button;
    }

    private static ShapeDrawable createButtonShape(
            View view,
            int color,
            int strokeColor
    ) {
        ShapeDrawable shape = new ShapeDrawable();
        shape.setColor(color);
        shape.setCornerRadius(view.dp(6));
        shape.setStroke(view.dp(1), strokeColor);
        return shape;
    }

    private static LinearLayout.LayoutParams controlParams(View view) {
        return new LinearLayout.LayoutParams(
                view.dp(170),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private static LinearLayout.LayoutParams cardParams(View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int margin = view.dp(8);
        params.setMargins(0, margin, 0, margin);
        return params;
    }

    private static LinearLayout.LayoutParams matchWidthWrapHeight() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    @Override
    public void onDestroyView() {
        ConfigRoot pageRoot = root;
        if (pageRoot != null) {
            pageRoot.clearFocus();
        }
        ClientConfig.saveIfDirtyAsync();
        root = null;
        fontOverlay = null;
        fontPreview = null;
        fontButton = null;
        keyError = null;
        keyButtons.clear();
        refreshers.clear();
        capturingAction = null;
        super.onDestroyView();
    }

    @Override
    public boolean isBackKey(int keyCode, @NonNull KeyEvent event) {
        if (fontOverlay != null || capturingAction != null) {
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
            if (fontOverlay != null
                    && event.getKeyCode() == KeyEvent.KEY_ESCAPE
                    && event.getAction() == KeyEvent.ACTION_UP) {
                dismissFontPicker();
                return true;
            }
            if (capturingAction != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && !event.isCanceled()) {
                    captureKey(event.getKeyCode());
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
