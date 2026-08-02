package top.rookiestwo.maimai_dialogue.client.config;

import net.minecraft.Util;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final Values VALUES;

    private static final AtomicBoolean DIRTY = new AtomicBoolean();
    private static volatile ClientPreferences preferences =
            ClientPreferences.defaults();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        VALUES = new Values(builder);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    public static ClientPreferences get() {
        return preferences;
    }

    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        reloadSnapshot();
        DIRTY.set(false);
    }

    public static void changed() {
        reloadSnapshot();
        DIRTY.set(true);
    }

    public static void saveIfDirtyAsync() {
        if (!DIRTY.compareAndSet(true, false)) {
            return;
        }
        Util.ioPool().execute(() -> {
            try {
                SPEC.save();
            } catch (RuntimeException exception) {
                DIRTY.set(true);
                MaiMaiDialogue.LOGGER.error(
                        "Failed to save MaiMai Dialogue client config.",
                        exception
                );
            }
        });
    }

    public static void resetAll() {
        VALUES.fastForwardMultiplier.set(
                ClientPreferences.DEFAULT_FAST_FORWARD_MULTIPLIER
        );
        VALUES.defaultTypewriterIntervalMs.set(
                ClientPreferences.DEFAULT_TYPEWRITER_INTERVAL_MS
        );
        VALUES.skipHoldDurationMs.set(
                ClientPreferences.DEFAULT_SKIP_HOLD_DURATION_MS
        );
        VALUES.fontFamily.set("");
        VALUES.fontScale.set(ClientPreferences.DEFAULT_FONT_SCALE);
        VALUES.fastForwardKey.set(
                ClientPreferences.DEFAULT_FAST_FORWARD_KEY.name()
        );
        VALUES.advanceKey.set(ClientPreferences.DEFAULT_ADVANCE_KEY.name());
        VALUES.skipKey.set(ClientPreferences.DEFAULT_SKIP_KEY.name());
        VALUES.historyKey.set(ClientPreferences.DEFAULT_HISTORY_KEY.name());
        changed();
    }

    private static void reloadSnapshot() {
        preferences = ClientPreferences.create(
                VALUES.fastForwardMultiplier.get(),
                VALUES.defaultTypewriterIntervalMs.get(),
                VALUES.skipHoldDurationMs.get(),
                VALUES.fontFamily.get(),
                VALUES.fontScale.get(),
                new DialogueKey(VALUES.fastForwardKey.get()),
                new DialogueKey(VALUES.advanceKey.get()),
                new DialogueKey(VALUES.skipKey.get()),
                new DialogueKey(VALUES.historyKey.get())
        );
        if (!preferences.conflicts().isEmpty()) {
            MaiMaiDialogue.LOGGER.warn(
                    "Disabled conflicting Dialogue controls: {}",
                    preferences.conflicts()
            );
        }
    }

    public static final class Values {
        public final ModConfigSpec.DoubleValue fastForwardMultiplier;
        public final ModConfigSpec.IntValue defaultTypewriterIntervalMs;
        public final ModConfigSpec.IntValue skipHoldDurationMs;
        public final ModConfigSpec.ConfigValue<String> fontFamily;
        public final ModConfigSpec.DoubleValue fontScale;
        public final ModConfigSpec.ConfigValue<String> fastForwardKey;
        public final ModConfigSpec.ConfigValue<String> advanceKey;
        public final ModConfigSpec.ConfigValue<String> skipKey;
        public final ModConfigSpec.ConfigValue<String> historyKey;

        private Values(ModConfigSpec.Builder builder) {
            builder.comment("Dialogue playback preferences").push("playback");
            fastForwardMultiplier = builder.comment(
                            "Playback multiplier while the fast-forward key is held."
                    )
                    .defineInRange("fastForwardMultiplier", 4.0, 1.0, 32.0);
            defaultTypewriterIntervalMs = builder.comment(
                            "Default milliseconds per code point when Dialogue JSON omits typewriter_interval_ms."
                    )
                    .defineInRange("defaultTypewriterIntervalMs", 30, 0, 1_000);
            skipHoldDurationMs = builder.comment(
                            "Milliseconds that the skip button or key must be held."
                    )
                    .defineInRange("skipHoldDurationMs", 600, 200, 3_000);
            builder.pop();

            builder.comment("Dialogue appearance preferences").push("appearance");
            fontFamily = builder.comment(
                            "Font family used by Dialogue screens. Empty follows Modern UI."
                    )
                    .define("fontFamily", "");
            fontScale = builder.comment(
                            "Scale applied to Dialogue theme text sizes."
                    )
                    .defineInRange("fontScale", 1.0, 0.5, 2.0);
            builder.pop();

            builder.comment("Dialogue-local keyboard bindings").push("controls");
            fastForwardKey = defineKey(builder, "fastForwardKey", "control");
            advanceKey = defineKey(
                    builder,
                    "advanceKey",
                    "key.keyboard.space"
            );
            skipKey = defineKey(
                    builder,
                    "skipKey",
                    DialogueKey.UNBOUND_NAME
            );
            historyKey = defineKey(
                    builder,
                    "historyKey",
                    "key.keyboard.h"
            );
            builder.pop();
        }

        private static ModConfigSpec.ConfigValue<String> defineKey(
                ModConfigSpec.Builder builder,
                String name,
                String defaultValue
        ) {
            return builder.define(name, defaultValue, DialogueKey::isValidName);
        }
    }
}
