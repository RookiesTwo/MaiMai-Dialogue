package top.rookiestwo.maimai_dialogue.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import icyllis.modernui.view.KeyEvent;

import java.util.Objects;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;

public record DialogueKey(String name) {
    public static final String CONTROL_NAME = "control";
    public static final String UNBOUND_NAME = "key.keyboard.unknown";
    public static final DialogueKey CONTROL = new DialogueKey(CONTROL_NAME);
    public static final DialogueKey UNBOUND = new DialogueKey(UNBOUND_NAME);

    public DialogueKey {
        Objects.requireNonNull(name, "name");
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Invalid Dialogue key: " + name);
        }
    }

    public static DialogueKey fromKeyCode(int keyCode) {
        if (keyCode == GLFW_KEY_LEFT_CONTROL
                || keyCode == GLFW_KEY_RIGHT_CONTROL) {
            return CONTROL;
        }
        if (keyCode == KeyEvent.KEY_ESCAPE) {
            throw new IllegalArgumentException("Escape cannot be bound.");
        }
        return new DialogueKey(
                InputConstants.Type.KEYSYM.getOrCreate(keyCode).getName()
        );
    }

    public boolean matches(int keyCode) {
        if (isUnbound()) {
            return false;
        }
        if (isControl()) {
            return keyCode == GLFW_KEY_LEFT_CONTROL
                    || keyCode == GLFW_KEY_RIGHT_CONTROL;
        }
        return InputConstants.Type.KEYSYM.getOrCreate(keyCode)
                .getName()
                .equals(name);
    }

    public boolean isControl() {
        return CONTROL_NAME.equals(name);
    }

    public boolean isUnbound() {
        return UNBOUND_NAME.equals(name);
    }

    public static boolean isValidName(Object value) {
        if (!(value instanceof String name) || name.isBlank()) {
            return false;
        }
        if (CONTROL_NAME.equals(name) || UNBOUND_NAME.equals(name)) {
            return true;
        }
        try {
            InputConstants.Key key = InputConstants.getKey(name);
            return key.getType() == InputConstants.Type.KEYSYM
                    && key.getValue() != KeyEvent.KEY_ESCAPE;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
