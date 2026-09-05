package top.rookiestwo.maimai_dialogue.client.session;

import java.util.List;
import java.util.Objects;

// session 只描述提示，翻译由客户端展示边界完成。
public record SessionMessage(String key, List<Object> arguments) {
    public SessionMessage {
        Objects.requireNonNull(key, "key");
        arguments = List.copyOf(arguments);
    }

    public static SessionMessage translated(String key, Object... arguments) {
        return new SessionMessage(key, List.of(arguments));
    }
}
