package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneActionCall;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DialogueEnd(
        Optional<String> text,
        Optional<SpeakerOperation> speaker,
        List<SceneActionCall> actions,
        DialogueExit exit
) {
    public static final Codec<DialogueEnd> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf("text").forGetter(DialogueEnd::text),
                    SpeakerOperation.CODEC.optionalFieldOf("speaker")
                            .forGetter(DialogueEnd::speaker),
                    SceneActionCall.CODEC.listOf()
                            .optionalFieldOf("actions", List.of())
                            .forGetter(DialogueEnd::actions),
                    DialogueExit.CODEC.fieldOf("exit").forGetter(DialogueEnd::exit)
            ).apply(instance, DialogueEnd::new)
    );

    public DialogueEnd {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(exit, "exit");
        actions = List.copyOf(actions);
    }

    public DialogueEnd(
            Optional<String> text,
            Optional<SpeakerOperation> speaker,
            DialogueExit exit
    ) {
        this(text, speaker, List.of(), exit);
    }
}
