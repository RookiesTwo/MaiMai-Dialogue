package top.rookiestwo.maimai_dialogue_editor.client.ui.controls;

/** Inspector folding preferences, independent of workbench layout and persistence. */
public interface PropertySectionState {
    boolean collapsed(String key);
    void collapsed(String key, boolean value);
    void expandPrefix(String prefix);
    void changed();
}
