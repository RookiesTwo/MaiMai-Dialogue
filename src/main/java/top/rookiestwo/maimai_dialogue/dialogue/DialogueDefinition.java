package top.rookiestwo.maimai_dialogue.dialogue;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.content.DefinitionCodecs;
import top.rookiestwo.maimai_dialogue.audio.BgmOperation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.datafixers.util.Pair;
import top.rookiestwo.maimai_dialogue.progress.ProgressExpression;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public record DialogueDefinition(
        Optional<ProgressExpression> requires,
        Optional<String> skipSummary,
        boolean mustComplete,
        ResourceLocation scene,
        List<DialogueStep> steps,
        DialogueEnd end,
        Optional<BgmOperation> bgm
) {
    private static final Codec<String> SKIP_SUMMARY_CODEC =
            Codec.STRING.validate(value -> value.isBlank()
                    ? DataResult.error(
                            () -> "skip_summary must not be blank."
                    )
                    : DataResult.success(value));

    private static final Codec<DialogueDefinition> BASE_CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            ProgressExpression.CODEC.optionalFieldOf("requires")
                                    .forGetter(DialogueDefinition::requires),
                            optionalSkipSummaryFieldOf("skip_summary")
                                    .forGetter(DialogueDefinition::skipSummary),
                            Codec.BOOL.optionalFieldOf("must_complete", false)
                                    .forGetter(DialogueDefinition::mustComplete),
                            ResourceLocation.CODEC.fieldOf("scene")
                                    .forGetter(DialogueDefinition::scene),
                            DialogueStep.CODEC.listOf()
                                    .optionalFieldOf("steps", List.of())
                                    .forGetter(DialogueDefinition::steps),
                            DialogueEnd.CODEC.fieldOf("end")
                                    .forGetter(DialogueDefinition::end),
                            BgmOperation.CODEC.optionalFieldOf("bgm")
                                    .forGetter(DialogueDefinition::bgm)
                    ).apply(instance, DialogueDefinition::new)
            );

    public static final Codec<DialogueDefinition> CODEC = DefinitionCodecs.rejectFields(BASE_CODEC, "presentation");

    public DialogueDefinition {
        Objects.requireNonNull(requires, "requires");
        Objects.requireNonNull(skipSummary, "skipSummary");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(bgm, "bgm");
        steps = List.copyOf(steps);
    }

    public DialogueDefinition(
            Optional<ProgressExpression> requires, Optional<String> skipSummary,
            boolean mustComplete, ResourceLocation scene, List<DialogueStep> steps, DialogueEnd end
    ) {
        this(requires, skipSummary, mustComplete, scene, steps, end, Optional.empty());
    }

    public DialogueDefinition(
            Optional<ProgressExpression> requires,
            Optional<String> skipSummary,
            ResourceLocation scene,
            List<DialogueStep> steps,
            DialogueEnd end
    ) {
        this(
                requires,
                skipSummary,
                false,
                scene,
                steps,
                end
        );
    }

    public DialogueDefinition(
            Optional<ProgressExpression> requires,
            ResourceLocation scene,
            List<DialogueStep> steps,
            DialogueEnd end
    ) {
        this(
                requires,
                Optional.empty(),
                false,
                scene,
                steps,
                end
        );
    }

    // 区分字段缺失与显式 JSON null，避免把无效摘要当作未配置。
    private static MapCodec<Optional<String>> optionalSkipSummaryFieldOf(
            String name
    ) {
        return new MapCodec<>() {
            @Override
            public <T> DataResult<Optional<String>> decode(
                    DynamicOps<T> ops,
                    MapLike<T> input
            ) {
                Optional<T> value = input.entries()
                        .filter(entry -> ops.getStringValue(entry.getFirst())
                                .result()
                                .filter(name::equals)
                                .isPresent())
                        .map(Pair::getSecond)
                        .findFirst();
                if (value.isEmpty()) {
                    return DataResult.success(Optional.empty());
                }
                return SKIP_SUMMARY_CODEC.parse(
                        ops,
                        value.orElseThrow()
                ).map(Optional::of);
            }

            @Override
            public <T> RecordBuilder<T> encode(
                    Optional<String> input,
                    DynamicOps<T> ops,
                    RecordBuilder<T> prefix
            ) {
                return input.isPresent()
                        ? prefix.add(name, SKIP_SUMMARY_CODEC.encodeStart(
                                ops,
                                input.orElseThrow()
                        ))
                        : prefix;
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }
        };
    }
}
