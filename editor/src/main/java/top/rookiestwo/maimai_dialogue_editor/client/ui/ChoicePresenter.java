package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.view.View;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Dropdowns are attached to the workspace root, never a separate native popup window. */
interface ChoicePresenter {
    record Item(String value, String label) {}
    void show(View anchor, List<Item> items, String selected, Consumer<String> chosen);
    void showColor(View anchor, Supplier<String> value, Consumer<String> changed);
}
