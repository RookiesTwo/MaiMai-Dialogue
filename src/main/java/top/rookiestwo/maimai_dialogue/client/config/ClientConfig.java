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
                    .defineInRange("fastForwardMultiplier", ClientPreferences.DEFAULT_FAST_FORWARD_MULTIPLIER, ClientPreferences.MIN_FAST_FORWARD_MULTIPLIER, ClientPreferences.MAX_FAST_FORWARD_MULTIPLIER);
            defaultTypewriterIntervalMs = builder.comment(
                            "Default milliseconds per code point when Dialogue JSON omits typewriter_interval_ms."
                    )
                    .defineInRange("defaultTypewriterIntervalMs", ClientPreferences.DEFAULT_TYPEWRITER_INTERVAL_MS, ClientPreferences.MIN_TYPEWRITER_INTERVAL_MS, ClientPreferences.MAX_TYPEWRITER_INTERVAL_MS);
            skipHoldDurationMs = builder.comment(
                            "Milliseconds that the skip button or key must be held."
                    )
                    .defineInRange("skipHoldDurationMs", ClientPreferences.DEFAULT_SKIP_HOLD_DURATION_MS, ClientPreferences.MIN_SKIP_HOLD_DURATION_MS, ClientPreferences.MAX_SKIP_HOLD_DURATION_MS);
            builder.pop();

            builder.comment("Dialogue appearance preferences").push("appearance");
            fontFamily = builder.comment(
                            "Font family used by Dialogue screens. Empty follows Modern UI."
                    )
                    .define("fontFamily", "");
            fontScale = builder.comment(
                            "Scale applied to Dialogue theme text sizes."
                    )
                    .defineInRange("fontScale", ClientPreferences.DEFAULT_FONT_SCALE, ClientPreferences.MIN_FONT_SCALE, ClientPreferences.MAX_FONT_SCALE);
            builder.pop();

            builder.comment("Dialogue-local keyboard bindings").push("controls");
            fastForwardKey = defineKey(builder, "fastForwardKey", ClientPreferences.DEFAULT_FAST_FORWARD_KEY.name());
            advanceKey = defineKey(
                    builder,
                    "advanceKey",
                    ClientPreferences.DEFAULT_ADVANCE_KEY.name()
            );
            skipKey = defineKey(
                    builder,
                    "skipKey",
                    ClientPreferences.DEFAULT_SKIP_KEY.name()
            );
            historyKey = defineKey(
                    builder,
                    "historyKey",
                    ClientPreferences.DEFAULT_HISTORY_KEY.name()
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
