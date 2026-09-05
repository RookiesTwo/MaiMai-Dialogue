package top.rookiestwo.maimai_dialogue.client.config.ui;

import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;
import top.rookiestwo.maimai_dialogue.client.config.ClientControlAction;
import top.rookiestwo.maimai_dialogue.client.config.ClientPreferences;
import top.rookiestwo.maimai_dialogue.client.config.DialogueKey;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Locale;

import static top.rookiestwo.maimai_dialogue.client.config.ui.ConfigWidgets.*;

final class KeyBindingEditor {
    private final java.util.function.Supplier<FrameLayout> root;
    private final EnumMap<ClientControlAction, Button> keyButtons = new EnumMap<>(ClientControlAction.class);
    private TextView keyError;
    private ClientControlAction capturingAction;

    KeyBindingEditor(java.util.function.Supplier<FrameLayout> root) {
        this.root = root;
    }

    void setErrorView(TextView error) {
        keyError = error;
    }

    boolean isCapturing() {
        return capturingAction != null;
    }

    void clear() {
        keyError = null;
        keyButtons.clear();
        capturingAction = null;
    }

    void addKeyOption(
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
        FrameLayout pageRoot = root.get();
        if (pageRoot != null) {
            pageRoot.requestFocus();
        }
    }

    void captureKey(int keyCode) {
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

    void refreshKeys() {
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
}
