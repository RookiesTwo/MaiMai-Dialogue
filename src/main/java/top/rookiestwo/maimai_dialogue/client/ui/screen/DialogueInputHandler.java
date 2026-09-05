package top.rookiestwo.maimai_dialogue.client.ui.screen;

import icyllis.modernui.view.KeyEvent;
import top.rookiestwo.maimai_dialogue.client.config.ClientControlAction;
import top.rookiestwo.maimai_dialogue.client.config.ClientPreferences;

import java.util.function.Consumer;

final class DialogueInputHandler {
    private final ClientPreferences preferences;
    private final HoldToSkipButton skipButton;
    private final java.util.function.BooleanSupplier hasConfirmation;
    Runnable advanceAction = () -> {
    };
    Runnable historyAction = () -> {
    };
    Runnable exitAction = () -> {
    };
    Consumer<Boolean> fastForwardAction = ignored -> {
    };
    Runnable confirmationEscapeAction = () -> {
    };
    private boolean fastForwardKeyDown;
    private boolean advanceKeyDown;
    private boolean skipKeyDown;
    private boolean historyKeyDown;
    private boolean escapeKeyDown;

    DialogueInputHandler(ClientPreferences preferences, HoldToSkipButton skipButton,
                         java.util.function.BooleanSupplier hasConfirmation) {
        this.preferences = preferences;
        this.skipButton = skipButton;
        this.hasConfirmation = hasConfirmation;
    }

    void reset() {
        escapeKeyDown = false;
        cancelTransientInput();
    }

    boolean handle(KeyEvent event) {
        int keyCode = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN
                && !event.isCanceled();
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        if (keyCode == KeyEvent.KEY_ESCAPE) {
            if (hasConfirmation.getAsBoolean()) {
                if (down && !escapeKeyDown) {
                    escapeKeyDown = true;
                    confirmationEscapeAction.run();
                } else if (up) {
                    escapeKeyDown = false;
                }
                return true;
            }
            if (down && !escapeKeyDown) {
                escapeKeyDown = true;
                exitAction.run();
            } else if (up) {
                escapeKeyDown = false;
            }
            return true;
        }
        if (hasConfirmation.getAsBoolean()) {
            return true;
        }
        if (preferences.matches(ClientControlAction.FAST_FORWARD, keyCode)) {
            if (down && !fastForwardKeyDown) {
                fastForwardKeyDown = true;
                fastForwardAction.accept(true);
            } else if (up && fastForwardKeyDown) {
                fastForwardKeyDown = false;
                fastForwardAction.accept(false);
            }
            return true;
        }
        if (preferences.matches(ClientControlAction.ADVANCE, keyCode)) {
            if (down) {
                advanceKeyDown = true;
            } else if (up && advanceKeyDown) {
                advanceKeyDown = false;
                advanceAction.run();
            }
            return true;
        }
        if (preferences.matches(ClientControlAction.SKIP, keyCode)) {
            if (down && !skipKeyDown) {
                skipKeyDown = true;
                skipButton.beginHold();
            } else if (up && skipKeyDown) {
                skipKeyDown = false;
                skipButton.cancelHold();
            }
            return true;
        }
        if (preferences.matches(ClientControlAction.HISTORY, keyCode)) {
            if (down) {
                historyKeyDown = true;
            } else if (up && historyKeyDown) {
                historyKeyDown = false;
                historyAction.run();
            }
            return true;
        }
        return false;
    }

    void cancelTransientInput() {
        fastForwardKeyDown = false;
        advanceKeyDown = false;
        skipKeyDown = false;
        historyKeyDown = false;
        fastForwardAction.accept(false);
        skipButton.cancelHold();
    }
}
