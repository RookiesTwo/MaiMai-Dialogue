package top.rookiestwo.maimai_dialogue.content;

import java.util.Objects;

public final class ContentRepository<T> {
    private volatile T current;

    public ContentRepository(T initial) {
        current = Objects.requireNonNull(initial, "initial");
    }

    public T current() {
        return current;
    }

    // 一次性发布完整 snapshot，避免读取方看到部分更新。
    public void replace(T snapshot) {
        current = Objects.requireNonNull(snapshot, "snapshot");
    }
}
