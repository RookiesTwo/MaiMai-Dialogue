package top.rookiestwo.maimai_dialogue.client.resource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePreparation;
import top.rookiestwo.maimai_dialogue.client.scene.SceneRuntime;
import top.rookiestwo.maimai_dialogue.client.scene.SceneTransitions;
import top.rookiestwo.maimai_dialogue.dialogue.ContinueStep;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueTarget;
import top.rookiestwo.maimai_dialogue.dialogue.EndStep;
import top.rookiestwo.maimai_dialogue.dialogue.OptionsExit;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;
import top.rookiestwo.maimai_dialogue.dialogue.SetSpeaker;
import top.rookiestwo.maimai_dialogue.dialogue.SpeakerOperation;
import top.rookiestwo.maimai_dialogue.dialogue.VisualObject;
import top.rookiestwo.maimai_dialogue.dialogue.resource.DialogueSnapshot;
import top.rookiestwo.maimai_dialogue.presentation.action.ActionCall;
import top.rookiestwo.maimai_dialogue.presentation.action.resource.ActionSnapshot;
import top.rookiestwo.maimai_dialogue.speaker.resource.SpeakerSnapshot;
import top.rookiestwo.maimai_dialogue.theme.resource.ThemeSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ClientResourceValidator {
    private ClientResourceValidator() {
    }

    public static List<String> validate(
            ResourceManager resourceManager,
            DialogueSnapshot dialogues,
            SpeakerSnapshot speakers,
            ThemeSnapshot themes,
            ActionSnapshot actions
    ) {
        return validate(
                dialogues,
                speakers,
                themes,
                actions,
                imageId -> resourceManager.getResource(
                        ResourceLocation.fromNamespaceAndPath(
                                imageId.getNamespace(),
                                "textures/" + imageId.getPath()
                        )
                ).isPresent()
        );
    }

    static List<String> validate(
            DialogueSnapshot dialogues,
            SpeakerSnapshot speakers,
            ThemeSnapshot themes,
            ActionSnapshot actions,
            Predicate<ResourceLocation> imageExists
    ) {
        List<String> errors = new ArrayList<>();
        dialogues.definitions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)
                ))
                .forEach(entry -> validateDialogue(
                        entry.getKey(),
                        entry.getValue(),
                        dialogues,
                        speakers,
                        themes,
                        actions,
                        imageExists,
                        errors
                ));
        return List.copyOf(errors);
    }

    private static void validateDialogue(
            ResourceLocation dialogueId,
            DialogueDefinition dialogue,
            DialogueSnapshot dialogues,
            SpeakerSnapshot speakers,
            ThemeSnapshot themes,
            ActionSnapshot actions,
            Predicate<ResourceLocation> imageExists,
            List<String> errors
    ) {
        Presentation presentation = dialogue.presentation();
        if (themes.find(presentation.theme()).isEmpty()) {
            errors.add(dialogueId + ": missing Theme "
                    + presentation.theme());
        }
        presentation.background().ifPresent(background ->
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
        presentation.visualObjects().forEach((objectId, object) ->
                validateVisualObjectImages(
                        dialogueId,
                        objectId,
                        object,
                        imageExists,
                        errors
                )
        );

        SceneRuntime runtime = new SceneRuntime(
                presentation,
                0.0F,
                actions::find
        );
        boolean initialStep = true;
        for (int index = 0; index < dialogue.steps().size(); index++) {
            ContinueStep step = dialogue.steps().get(index);
            validateSpeaker(
                    dialogueId,
                    "step " + index,
                    step.speaker().orElse(null),
                    speakers,
                    errors
            );
            List<ActionCall> stepActions = initialStep
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

        EndStep end = dialogue.end();
        validateSpeaker(
                dialogueId,
                "end",
                end.speaker().orElse(null),
                speakers,
                errors
        );
        List<ActionCall> endActions = initialStep
                ? SceneTransitions.withDefaultFadeIn(end.actions())
                : end.actions();
        validateActions(
                dialogueId,
                "end",
                endActions,
                runtime,
                errors
        );
        if (end.exit() instanceof OptionsExit options) {
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
            SpeakerSnapshot speakers,
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
            List<ActionCall> calls,
            SceneRuntime runtime,
            List<String> errors
    ) {
        ScenePreparation preparation = runtime.prepare(calls);
        preparation.errors().forEach(error ->
                errors.add(dialogueId + " " + location + ": " + error)
        );
    }
}
