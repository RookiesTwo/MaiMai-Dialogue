package top.rookiestwo.maimai_dialogue.content;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

/** Rejects removed structural fields instead of silently discarding their meaning. */
public final class DefinitionCodecs {
    private DefinitionCodecs() {}

    public static <A> Codec<A> rejectFields(Codec<A> delegate, String... fields) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getMap(input).flatMap(map -> {
                    for (String field : fields) {
                        if (map.get(field) != null) {
                            return DataResult.error(() -> "Unsupported field '" + field + "'.");
                        }
                    }
                    return delegate.decode(ops, input);
                });
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return delegate.encode(input, ops, prefix);
            }
        };
    }
}
