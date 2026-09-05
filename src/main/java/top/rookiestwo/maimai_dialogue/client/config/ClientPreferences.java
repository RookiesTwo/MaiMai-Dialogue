package top.rookiestwo.maimai_dialogue.client.config;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ClientPreferences(
        double fastForwardMultiplier,
        int defaultTypewriterIntervalMs,
        int skipHoldDurationMs,
        String fontFamily,
        double fontScale,
        DialogueKey fastForwardKey,
        DialogueKey advanceKey,
        DialogueKey skipKey,
        DialogueKey historyKey,
        Set<ClientControlAction> conflicts
) {
    public static final double MIN_FAST_FORWARD_MULTIPLIER = 1.0;
    public static final double MAX_FAST_FORWARD_MULTIPLIER = 32.0;
    public static final int MIN_TYPEWRITER_INTERVAL_MS = 0;
    public static final int MAX_TYPEWRITER_INTERVAL_MS = 1_000;
    public static final int MIN_SKIP_HOLD_DURATION_MS = 200;
    public static final int MAX_SKIP_HOLD_DURATION_MS = 3_000;
    public static final double MIN_FONT_SCALE = 0.5;
    public static final double MAX_FONT_SCALE = 2.0;

    public static final double DEFAULT_FAST_FORWARD_MULTIPLIER = 4.0;
    public static final int DEFAULT_TYPEWRITER_INTERVAL_MS = 30;
    public static final int DEFAULT_SKIP_HOLD_DURATION_MS = 600;
    public static final double DEFAULT_FONT_SCALE = 1.0;
    public static final DialogueKey DEFAULT_FAST_FORWARD_KEY =
            DialogueKey.CONTROL;
    public static final DialogueKey DEFAULT_ADVANCE_KEY =
            new DialogueKey("key.keyboard.space");
    public static final DialogueKey DEFAULT_SKIP_KEY = DialogueKey.UNBOUND;
    public static final DialogueKey DEFAULT_HISTORY_KEY =
            new DialogueKey("key.keyboard.h");

    public ClientPreferences {
        if (fastForwardMultiplier < MIN_FAST_FORWARD_MULTIPLIER
                || fastForwardMultiplier > MAX_FAST_FORWARD_MULTIPLIER) {
            throw new IllegalArgumentException(
                    "fastForwardMultiplier must be between 1 and 32."
            );
        }
        if (defaultTypewriterIntervalMs < MIN_TYPEWRITER_INTERVAL_MS
                || defaultTypewriterIntervalMs > MAX_TYPEWRITER_INTERVAL_MS) {
            throw new IllegalArgumentException(
                    "defaultTypewriterIntervalMs must be between 0 and 1000."
            );
        }
        if (skipHoldDurationMs < MIN_SKIP_HOLD_DURATION_MS || skipHoldDurationMs > MAX_SKIP_HOLD_DURATION_MS) {
            throw new IllegalArgumentException(
                    "skipHoldDurationMs must be between 200 and 3000."
            );
        }
        if (fontScale < MIN_FONT_SCALE || fontScale > MAX_FONT_SCALE) {
            throw new IllegalArgumentException(
                    "fontScale must be between 0.5 and 2.0."
            );
        }
        Objects.requireNonNull(fontFamily, "fontFamily");
        Objects.requireNonNull(fastForwardKey, "fastForwardKey");
        Objects.requireNonNull(advanceKey, "advanceKey");
        Objects.requireNonNull(skipKey, "skipKey");
        Objects.requireNonNull(historyKey, "historyKey");
        conflicts = Set.copyOf(conflicts);
    }

    public static ClientPreferences defaults() {
        return create(
                DEFAULT_FAST_FORWARD_MULTIPLIER,
                DEFAULT_TYPEWRITER_INTERVAL_MS,
                DEFAULT_SKIP_HOLD_DURATION_MS,
                "",
                DEFAULT_FONT_SCALE,
                DEFAULT_FAST_FORWARD_KEY,
                DEFAULT_ADVANCE_KEY,
                DEFAULT_SKIP_KEY,
                DEFAULT_HISTORY_KEY
        );
    }

    public static ClientPreferences create(
            double fastForwardMultiplier,
            int defaultTypewriterIntervalMs,
            int skipHoldDurationMs,
            String fontFamily,
            double fontScale,
            DialogueKey fastForwardKey,
            DialogueKey advanceKey,
            DialogueKey skipKey,
            DialogueKey historyKey
    ) {
        Map<ClientControlAction, DialogueKey> bindings = Map.of(
                ClientControlAction.FAST_FORWARD, fastForwardKey,
                ClientControlAction.ADVANCE, advanceKey,
                ClientControlAction.SKIP, skipKey,
                ClientControlAction.HISTORY, historyKey
        );
        return new ClientPreferences(
                fastForwardMultiplier,
                defaultTypewriterIntervalMs,
                skipHoldDurationMs,
                fontFamily,
                fontScale,
                fastForwardKey,
                advanceKey,
                skipKey,
                historyKey,
                findConflicts(bindings)
        );
    }

    public DialogueKey key(ClientControlAction action) {
        return switch (action) {
            case FAST_FORWARD -> fastForwardKey;
            case ADVANCE -> advanceKey;
            case SKIP -> skipKey;
            case HISTORY -> historyKey;
        };
    }

    public boolean matches(ClientControlAction action, int keyCode) {
        return !conflicts.contains(action) && key(action).matches(keyCode);
    }

    private static Set<ClientControlAction> findConflicts(
            Map<ClientControlAction, DialogueKey> bindings
    ) {
        Map<String, ClientControlAction> owners = new HashMap<>();
        EnumSet<ClientControlAction> conflicts = EnumSet.noneOf(
                ClientControlAction.class
        );
        bindings.forEach((action, key) -> {
            if (key.isUnbound()) {
                return;
            }
            ClientControlAction owner = owners.putIfAbsent(key.name(), action);
            if (owner != null) {
                conflicts.add(owner);
                conflicts.add(action);
            }
        });
        return conflicts;
    }
}
