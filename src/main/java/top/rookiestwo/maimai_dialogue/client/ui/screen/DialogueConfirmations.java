package top.rookiestwo.maimai_dialogue.client.ui.screen;

import top.rookiestwo.maimai_dialogue.client.controller.DialogueUiActions;
import top.rookiestwo.maimai_dialogue.client.ui.style.DialogueTypography;

import icyllis.modernui.core.Context;
import net.minecraft.client.resources.language.I18n;
import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

final class DialogueConfirmations {
    private final java.util.function.Supplier<DialogueRootLayout> rootSupplier;
    private final java.util.function.Supplier<DialogueScreenState> stateSupplier;
    private final java.util.function.Supplier<Context> context;
    private final DialogueUiActions controller;
    private long confirmationGeneration = Long.MIN_VALUE;
    private volatile boolean exitConfirmationPending;

    DialogueConfirmations(
            java.util.function.Supplier<DialogueRootLayout> root,
            java.util.function.Supplier<DialogueScreenState> state,
            java.util.function.Supplier<Context> context,
            DialogueUiActions controller
    ) {
        rootSupplier = root;
        stateSupplier = state;
        this.context = context;
        this.controller = controller;
    }

    void render(DialogueScreenState state) {
        DialogueRootLayout root = rootSupplier.get();
        if (root != null && root.hasConfirmation() && confirmationGeneration != state.generation()) {
            dismissConfirmation();
        }
    }

    void reset() {
        confirmationGeneration = Long.MIN_VALUE;
        exitConfirmationPending = false;
    }

    void onSkipHoldCompleted() {
        DialogueRootLayout root = rootSupplier.get();
        DialogueScreenState state = stateSupplier.get();
        if (root == null || state == null || !state.canSkipToEnd()) {
            return;
        }
        root.cancelTransientInput();
        String summary = state.skipSummary().orElse(null);
        if (summary == null) {
            root.setSkipAvailable(false);
            DialogueUiDispatch.toClient(controller::skipToEnd);
            return;
        }

        confirmationGeneration = state.generation();
        ThemeDefinition theme = state.theme().orElse(ThemeDefinition.DEFAULT);
        DialogueConfirmationView confirmation =
                new DialogueConfirmationView(
                        context.get(),
                        I18n.get("gui.maimai_dialogue.skip_confirm.title"),
                        summary,
                        I18n.get("gui.maimai_dialogue.skip_confirm.cancel"),
                        I18n.get("gui.maimai_dialogue.skip_confirm.confirm"),
                        true,
                        this::dismissConfirmation,
                        this::confirmSkipToEnd
                );
        confirmation.applyTheme(theme);
        confirmation.setTypography(
                DialogueTypography.resolve(ClientConfig.get()),
                theme
        );
        root.showConfirmation(
                confirmation,
                this::dismissConfirmation
        );
        root.setSkipAvailable(false);
    }

    private void confirmSkipToEnd() {
        DialogueRootLayout root = rootSupplier.get();
        if (root != null) {
            root.dismissConfirmation();
            root.setSkipAvailable(false);
        }
        confirmationGeneration = Long.MIN_VALUE;
        DialogueUiDispatch.toClient(controller::skipToEnd);
    }

    void showExitConfirmation() {
        exitConfirmationPending = false;
        DialogueRootLayout root = rootSupplier.get();
        DialogueScreenState state = stateSupplier.get();
        if (root == null
                || state == null
                || state.mustComplete()
                || root.hasConfirmation()) {
            return;
        }

        root.cancelTransientInput();
        confirmationGeneration = state.generation();
        ThemeDefinition theme = state.theme().orElse(ThemeDefinition.DEFAULT);
        DialogueConfirmationView confirmation =
                new DialogueConfirmationView(
                        context.get(),
                        null,
                        I18n.get("gui.maimai_dialogue.exit_confirm.body"),
                        I18n.get("gui.maimai_dialogue.exit_confirm.cancel"),
                        I18n.get("gui.maimai_dialogue.exit_confirm.confirm"),
                        false,
                        this::dismissConfirmation,
                        this::confirmExit
                );
        confirmation.applyTheme(theme);
        confirmation.setTypography(
                DialogueTypography.resolve(ClientConfig.get()),
                theme
        );
        root.showConfirmation(confirmation, this::dismissConfirmation);
        root.setSkipAvailable(false);
    }

    private void confirmExit() {
        DialogueRootLayout root = rootSupplier.get();
        if (root != null) {
            root.dismissConfirmation();
        }
        confirmationGeneration = Long.MIN_VALUE;
        exitConfirmationPending = false;
        DialogueUiDispatch.toClient(controller::closeFromUi);
    }

    void dismissConfirmation() {
        DialogueRootLayout root = rootSupplier.get();
        if (root == null) {
            return;
        }
        root.dismissConfirmation();
        confirmationGeneration = Long.MIN_VALUE;
        exitConfirmationPending = false;
        DialogueScreenState state = stateSupplier.get();
        root.setSkipAvailable(
                state != null && state.canSkipToEnd()
        );
        root.requestFocus();
    }

    public boolean shouldClose() {
        DialogueRootLayout root = rootSupplier.get();
        if (root == null) {
            return true;
        }
        if (root.hasConfirmation()) {
            root.post(this::dismissConfirmation);
            return false;
        }
        DialogueScreenState state = stateSupplier.get();
        if (state != null && state.mustComplete()) {
            exitConfirmationPending = false;
            return false;
        }
        if (!exitConfirmationPending) {
            exitConfirmationPending = true;
            root.post(this::showExitConfirmation);
        }
        return false;
    }
}
