package top.rookiestwo.maimai_dialogue_editor.document.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

// 各编辑器在组装时注册一次；保存、撤销和失焦共用同一结束顺序。
public final class ActiveEdits {
    private record Entry(BooleanSupplier active, Consumer<Boolean> finish) {}
    private final List<Entry> entries = new ArrayList<>();

    public void register(BooleanSupplier active, Consumer<Boolean> finish) { entries.add(new Entry(active, finish)); }
    public boolean active() { return entries.stream().anyMatch(entry -> entry.active().getAsBoolean()); }
    public void finish(boolean commit) { entries.forEach(entry -> entry.finish().accept(commit)); }
}
