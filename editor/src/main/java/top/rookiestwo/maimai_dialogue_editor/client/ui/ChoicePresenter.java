package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.view.View;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Dropdowns are attached to the workspace root, never a separate native popup window. */
interface ChoicePresenter {
    record Item(String value, String label, boolean enabled) {
        Item(String value, String label) { this(value, label, true); }
    }
    void show(View anchor, List<Item> items, String selected, Consumer<String> chosen);
    /** Command menus keep their own width instead of inheriting an icon button's width. */
    void showMenu(View anchor, List<Item> items, String selected, Consumer<String> chosen);
    void showMenuAt(View anchor, float x, float y, List<Item> items, Consumer<String> chosen);
    void showSearchable(View anchor, List<Item> items, String selected, Consumer<String> chosen);
    void editCommand(View anchor, String initial, Consumer<String> confirmed);
    void showColor(View anchor, Supplier<String> value, Consumer<String> changed);
    void showColor(View anchor, Supplier<String> value, Consumer<String> changed,
                   Supplier<? extends top.rookiestwo.maimai_dialogue_editor.document.EditGesture> gesture);
}
