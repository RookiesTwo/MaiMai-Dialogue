package top.rookiestwo.maimai_dialogue_editor.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.rookiestwo.maimai_dialogue_editor.client.ui.EditorFragment;

import java.util.Optional;
import java.util.function.Consumer;

/** Native input/completion on a NeoForge GUI layer. No chat or command execution path. */
final class CommandInputScreen extends Screen {
    // ServerboundCommandSuggestionPacket uses read/writeUtf(32500).
    private static final int COMPLETION_LIMIT = 32500;
    final EditorFragment owner;
    private final Consumer<Optional<String>> finished;
    private String value;
    private EditBox input;
    private CommandSuggestions suggestions;
    private Button done;
    private Optional<String> result = Optional.empty();
    private boolean closed;

    CommandInputScreen(EditorFragment owner, String initial, Consumer<Optional<String>> finished) {
        super(Component.translatable("gui.maimai_dialogue_editor.command.title"));
        this.owner = owner;
        this.value = initial;
        this.finished = finished;
    }

    @Override protected void init() {
        int fieldWidth = Math.max(40, Math.min(640, width - 32));
        if (input == null) {
            input = new EditBox(font, (width - fieldWidth) / 2, 44, fieldWidth, 20,
                    Component.translatable("gui.maimai_dialogue_editor.edit.commands"));
            input.setMaxLength(Math.max(COMPLETION_LIMIT, value.length()));
            input.setValue(value);
            input.setCanLoseFocus(false);
        } else {
            // Keep the native input and selection during window/GUI-scale changes.
            input.setX((width - fieldWidth) / 2); input.setWidth(fieldWidth);
        }
        addRenderableWidget(input);
        int buttonWidth = Math.min(100, Math.max(30, (width - 30) / 2));
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> confirm())
                .bounds(width / 2 - buttonWidth - 4, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 4, height - 28, buttonWidth, 20).build());
        // Commands-only mode also accepts text without a leading slash. Offline authoring stays available.
        if (suggestions != null) suggestions.setAllowSuggestions(false);
        suggestions = null;
        input.setResponder(this::edited);
        edited(value);
    }

    private void edited(String text) {
        value = text;
        done.active = !text.isBlank() && !text.strip().equals("/");
        if (text.length() > COMPLETION_LIMIT || minecraft.player == null || minecraft.getConnection() == null) {
            if (suggestions != null) suggestions.setAllowSuggestions(false);
            suggestions = null;
            input.setSuggestion(null);
            input.setFormatter((line, offset) -> net.minecraft.util.FormattedCharSequence.forward(line, net.minecraft.network.chat.Style.EMPTY));
            return;
        }
        if (suggestions == null) {
            suggestions = new CommandSuggestions(minecraft, this, input, font, true, false, 1, 7, false, 0xE0202020);
            suggestions.setAllowHiding(false);
        }
        if (suggestions != null) {
            suggestions.setAllowSuggestions(true);
            suggestions.updateCommandInfo();
        }
    }

    @Override protected void setInitialFocus() { setInitialFocus(input); }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (suggestions != null && suggestions.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { confirm(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (suggestions != null && suggestions.mouseClicked(x, y, button)) return true;
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (suggestions != null && suggestions.mouseScrolled(vertical)) return true;
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private void confirm() {
        if (closed || !done.active || minecraft.screen != this) return;
        // Preserve quoting and internal whitespace; ChatScreen.normalizeChatMessage would change them.
        result = Optional.of(input.getValue().strip());
        onClose();
    }

    @Override public void onClose() {
        if (!closed && minecraft.screen == this) minecraft.popGuiLayer();
    }

    @Override public void removed() {
        if (closed) return;
        closed = true;
        if (suggestions != null) suggestions.setAllowSuggestions(false);
        finished.accept(result);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xD0101010);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 22, 0xFFFFFF);
        if (suggestions != null) suggestions.render(graphics, mouseX, mouseY);
    }

    @Override public boolean isPauseScreen() { return true; }
}
