package top.rookiestwo.maimai_dialogue_editor.preview;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.session.DialogueContentLookup;
import top.rookiestwo.maimai_dialogue.dialogue.*;
import top.rookiestwo.maimai_dialogue.progress.ProgressNode;
import java.util.*;

// 项目本地模拟设置；只包装预览查询，不改写资源、存档或全局仓库。
public record PreviewScenario(List<String> progress, List<TextChoice> texts, boolean commandFailure) {
    public record TextChoice(String dialogue, int step, int variant) {}
    public PreviewScenario {
        progress = progress == null ? List.of() : progress.stream().filter(Objects::nonNull)
                .filter(value -> ProgressNode.parse(value).result().isPresent()).distinct().toList();
        texts = texts == null ? List.of() : texts.stream().filter(Objects::nonNull)
                .filter(value -> value.dialogue() != null && ResourceLocation.tryParse(value.dialogue()) != null
                        && value.step() >= -1 && value.variant() >= 0).toList();
    }
    public static PreviewScenario defaults() { return new PreviewScenario(null, null, false); }
    public Set<ProgressNode> nodes() {
        return progress.stream().map(ProgressNode::new).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public PreviewScenario withProgress(String text) {
        var nodes = text.isBlank() ? List.<String>of() : Arrays.stream(text.strip().split("[\\s,]+"))
                .map(value -> ProgressNode.parse(value).getOrThrow().value()).distinct().toList();
        return new PreviewScenario(nodes, texts, commandFailure);
    }
    public int variant(String dialogue, int step) {
        return texts.stream().filter(value -> value.dialogue().equals(dialogue) && value.step() == step)
                .mapToInt(TextChoice::variant).findFirst().orElse(-1);
    }
    public PreviewScenario withVariant(String dialogue, int step, int variant) {
        var next = new ArrayList<>(texts); next.removeIf(value -> value.dialogue().equals(dialogue) && value.step() == step);
        if (variant >= 0) next.add(new TextChoice(dialogue, step, variant));
        return new PreviewScenario(progress, next, commandFailure);
    }
    private Optional<DialogueText> text(ResourceLocation id, int step, Optional<DialogueText> source) {
        int index = variant(id.toString(), step);
        return source.map(value -> index < 0 || index >= value.variants().size() ? value : DialogueText.fixed(value.variants().get(index)));
    }
    private DialogueDefinition dialogue(ResourceLocation id, DialogueDefinition source) {
        var steps = new ArrayList<DialogueStep>();
        for (int i = 0; i < source.steps().size(); i++) {
            var step = source.steps().get(i);
            steps.add(new DialogueStep(text(id, i, step.text()), step.typewriterIntervalMs(), step.speaker(), step.actions(),
                    step.usesDefaultTypewriterInterval(), step.typewriterSound()));
        }
        var end = source.end();
        return new DialogueDefinition(source.requires(), source.skipSummary(), source.mustComplete(), source.scene(), steps,
                new DialogueEnd(text(id, -1, end.text()), end.typewriterIntervalMs(), end.speaker(), end.actions(), end.exit(),
                        end.usesDefaultTypewriterInterval(), end.typewriterSound()), source.bgm());
    }
    public DialogueContentLookup wrap(DialogueContentLookup source) {
        return new DialogueContentLookup() {
            public Optional<DialogueDefinition> dialogue(ResourceLocation id) { return source.dialogue(id).map(value -> PreviewScenario.this.dialogue(id, value)); }
            public Optional<top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition> speaker(ResourceLocation id) { return source.speaker(id); }
            public Optional<top.rookiestwo.maimai_dialogue.theme.ThemeDefinition> theme(ResourceLocation id) { return source.theme(id); }
            public Optional<top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition> scene(ResourceLocation id) { return source.scene(id); }
            public Optional<top.rookiestwo.maimai_dialogue.presentation.visual.VisualAssetDefinition> visualAsset(ResourceLocation id) { return source.visualAsset(id); }
            public Optional<top.rookiestwo.maimai_dialogue.presentation.action.SceneAction> action(ResourceLocation id) { return source.action(id); }
        };
    }
}
