package top.rookiestwo.maimai_dialogue.client.session;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueText;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

// 缓存跟随整个 session，Return 回到根 Dialogue 时也不重新抽取正文。
final class DialogueTextCache {
    private final Map<Key, String> texts = new HashMap<>();
    private final RandomGenerator random;

    DialogueTextCache(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    Optional<String> resolve(ResourceLocation dialogue, int step, boolean end, Optional<DialogueText> text) {
        return text.map(value -> texts.computeIfAbsent(
                new Key(dialogue, end ? 0 : step, end), ignored -> value.select(random)
        ));
    }

    private record Key(ResourceLocation dialogue, int step, boolean end) {
    }
}
