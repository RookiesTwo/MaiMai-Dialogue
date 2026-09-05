package top.rookiestwo.maimai_dialogue.client.config.ui;

import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;
import top.rookiestwo.maimai_dialogue.client.config.ClientPreferences;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;
import top.rookiestwo.maimai_dialogue.client.ui.style.DialogueTypography;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static top.rookiestwo.maimai_dialogue.client.config.ui.ConfigWidgets.*;

final class FontPicker {
    private final java.util.function.Supplier<FrameLayout> root;
    private final java.util.function.Supplier<Context> context;
    private TextView fontPreview;
    private Button fontButton;
    private View fontOverlay;

    FontPicker(java.util.function.Supplier<FrameLayout> root, java.util.function.Supplier<Context> context) {
        this.root = root;
        this.context = context;
    }

    void bind(Button button, TextView preview) {
        fontButton = button;
        fontPreview = preview;
    }

    boolean isOpen() {
        return fontOverlay != null;
    }

    void clear() {
        fontOverlay = null;
        fontPreview = null;
        fontButton = null;
    }

    void refreshFont() {
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

    void showFontPicker() {
        FrameLayout pageRoot = root.get();
        if (pageRoot == null || fontOverlay != null) {
            return;
        }
        Context context = this.context.get();
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

    void dismissFontPicker() {
        FrameLayout pageRoot = root.get();
        View overlay = fontOverlay;
        fontOverlay = null;
        if (pageRoot != null && overlay != null) {
            pageRoot.removeView(overlay);
            pageRoot.requestFocus();
        }
    }
}
