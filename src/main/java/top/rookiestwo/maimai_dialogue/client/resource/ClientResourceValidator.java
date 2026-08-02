package top.rookiestwo.maimai_dialogue.client.resource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import top.rookiestwo.maimai_dialogue.content.DefinitionRegistry;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePreparation;
import top.rookiestwo.maimai_dialogue.client.scene.SceneRuntime;
import top.rookiestwo.maimai_dialogue.client.scene.SceneTransitions;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueStep;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueTarget;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueEnd;
import top.rookiestwo.maimai_dialogue.dialogue.ChoiceExit;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;
import top.rookiestwo.maimai_dialogue.dialogue.SetSpeaker;
import top.rookiestwo.maimai_dialogue.dialogue.SpeakerOperation;
import top.rookiestwo.maimai_dialogue.dialogue.VisualObject;
import top.rookiestwo.maimai_dialogue.dialogue.VisualAssetDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.VisualAssetResolver;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneActionCall;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneAction;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ClientResourceValidator {
    private ClientResourceValidator() {
    }

    // 校验客户端全部 definition 之间的引用和图片资源。
    public static List<String> validate(
            ResourceManager resourceManager,
            ClientContentSnapshot snapshot
    ) {
        return validate(
                snapshot.dialogues(),
                snapshot.speakers(),
                snapshot.themes(),
                snapshot.visualAssets(),
                snapshot.actions(),
                imageId -> resourceManager.getResource(
                        ResourceLocation.fromNamespaceAndPath(
                                imageId.getNamespace(),
                                "textures/" + imageId.getPath()
                        )
                ).isPresent()
        );
    }

    static List<String> validate(
            DefinitionRegistry<DialogueDefinition> dialogues,
            DefinitionRegistry<SpeakerDefinition> speakers,
            DefinitionRegistry<ThemeDefinition> themes,
            DefinitionRegistry<SceneAction> actions,
            Predicate<ResourceLocation> imageExists
    ) {
        return validate(
                dialogues,
                speakers,
                themes,
                DefinitionRegistry.empty(),
                actions,
                imageExists
        );
    }

    static List<String> validate(
            DefinitionRegistry<DialogueDefinition> dialogues,
            DefinitionRegistry<SpeakerDefinition> speakers,
            DefinitionRegistry<ThemeDefinition> themes,
            DefinitionRegistry<VisualAssetDefinition> visualAssets,
            DefinitionRegistry<SceneAction> actions,
            Predicate<ResourceLocation> imageExists
    ) {
        List<String> errors = new ArrayList<>();
        visualAssets.entries().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)
                ))
                .forEach(entry -> entry.getValue().variants()
                        .forEach((variant, image) -> validateImage(
                                entry.getKey(),
                                "VisualAsset variant " + variant,
                                image,
                                imageExists,
                                errors
                        )));
        dialogues.entries().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)
                ))
                .forEach(entry -> validateDialogue(
                        entry.getKey(),
                        entry.getValue(),
                        dialogues,
                        speakers,
                        themes,
                        visualAssets,
                        actions,
                        imageExists,
                        errors
                ));
        return List.copyOf(errors);
    }

    private static void validateDialogue(
            ResourceLocation dialogueId,
            DialogueDefinition dialogue,
            DefinitionRegistry<DialogueDefinition> dialogues,
            DefinitionRegistry<SpeakerDefinition> speakers,
            DefinitionRegistry<ThemeDefinition> themes,
            DefinitionRegistry<VisualAssetDefinition> visualAssets,
            DefinitionRegistry<SceneAction> actions,
            Predicate<ResourceLocation> imageExists,
            List<String> errors
    ) {
        Presentation sourcePresentation = dialogue.presentation();
        if (themes.find(sourcePresentation.theme()).isEmpty()) {
            errors.add(dialogueId + ": missing Theme "
                    + sourcePresentation.theme());
        }
        sourcePresentation.background().ifPresent(background ->
                background.variants().forEach((variant, image) ->
                        validateImage(
                                dialogueId,
                                "Background variant " + variant,
                                image,
                                imageExists,
                                errors
                        )
                )
        );
        sourcePresentation.visualObjects().forEach((objectId, object) -> {
            if (!object.referencesAsset()) {
                validateVisualObjectImages(
                        dialogueId,
                        objectId,
                        object,
                        imageExists,
                        errors
                );
            }
        });
        VisualAssetResolver.Result resolution = VisualAssetResolver.resolve(
                sourcePresentation,
                visualAssets::find
        );
        resolution.errors().forEach(error ->
                errors.add(dialogueId + ": " + error)
        );
        Presentation presentation = resolution.presentation();

        SceneRuntime runtime = new SceneRuntime(
                presentation,
                0.0F,
                actions::find
        );
        boolean initialStep = true;
        for (int index = 0; index < dialogue.steps().size(); index++) {
            DialogueStep step = dialogue.steps().get(index);
            validateSpeaker(
                    dialogueId,
                    "step " + index,
                    step.speaker().orElse(null),
                    speakers,
                    errors
            );
            List<SceneActionCall> stepActions = initialStep
                    ? SceneTransitions.withDefaultFadeIn(step.actions())
                    : step.actions();
            initialStep = false;
            validateActions(
                    dialogueId,
                    "step " + index,
                    stepActions,
                    runtime,
                    errors
            );
        }

        DialogueEnd end = dialogue.end();
        validateSpeaker(
                dialogueId,
                "end",
                end.speaker().orElse(null),
                speakers,
                errors
        );
        List<SceneActionCall> endActions = initialStep
                ? SceneTransitions.withDefaultFadeIn(end.actions())
                : end.actions();
        validateActions(
                dialogueId,
                "end",
                endActions,
                runtime,
                errors
        );
        if (end.exit() instanceof ChoiceExit options) {
            options.options().forEach(option -> {
                if (option.target() instanceof DialogueTarget target
                        && !dialogues.contains(target.dialogue())) {
                    errors.add(dialogueId
                            + ": option \"" + option.text()
                            + "\" targets missing Dialogue "
                            + target.dialogue());
                }
            });
        }
    }

    private static void validateVisualObjectImages(
            ResourceLocation dialogueId,
            String objectId,
            VisualObject object,
            Predicate<ResourceLocation> imageExists,
            List<String> errors
    ) {
        object.variants().forEach((variant, image) ->
                validateImage(
                        dialogueId,
                        "VisualObject " + objectId + " variant " + variant,
                        image,
                        imageExists,
                        errors
                )
        );
    }

    private static void validateImage(
            ResourceLocation dialogueId,
            String owner,
            ResourceLocation image,
            Predicate<ResourceLocation> imageExists,
            List<String> errors
    ) {
        if (!imageExists.test(image)) {
            errors.add(dialogueId + ": " + owner
                    + " uses missing image " + image);
        }
    }

    private static void validateSpeaker(
            ResourceLocation dialogueId,
            String location,
            SpeakerOperation operation,
            DefinitionRegistry<SpeakerDefinition> speakers,
            List<String> errors
    ) {
        if (operation instanceof SetSpeaker set
                && speakers.find(set.id()).isEmpty()) {
            errors.add(dialogueId + " " + location
                    + ": missing Speaker " + set.id());
        }
    }

    private static void validateActions(
            ResourceLocation dialogueId,
            String location,
            List<SceneActionCall> calls,
            SceneRuntime runtime,
            List<String> errors
    ) {
        ScenePreparation preparation = runtime.prepare(calls);
        preparation.errors().forEach(error ->
                errors.add(dialogueId + " " + location + ": " + error)
        );
    }
}
