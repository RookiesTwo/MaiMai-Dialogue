package top.rookiestwo.maimai_dialogue.client.session;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import top.rookiestwo.maimai_dialogue.client.PlaybackPhase;
import top.rookiestwo.maimai_dialogue.dialogue.*;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneAction;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneActionCall;
import top.rookiestwo.maimai_dialogue.presentation.action.ActionSpec;
import top.rookiestwo.maimai_dialogue.presentation.action.ActionEasing;
import top.rookiestwo.maimai_dialogue.presentation.action.NumericKeyframe;
import top.rookiestwo.maimai_dialogue.presentation.action.NumericTrack;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueSessionTest {
    private static final ResourceLocation ROOT = id("root");
    private static final ResourceLocation CHILD = id("child");
    private static final ResourceLocation THEME = id("theme");

    @Test
    void resolvesOmittedTypewriterIntervalFromClientPreference() {
        DialogueDefinition root = new DialogueDefinition(
                Optional.empty(),
                new Presentation(THEME),
                List.of(new DialogueStep(
                        Optional.of(DialogueText.fixed("Inherited")),
                        Optional.empty()
                )),
                new DialogueEnd(
                        Optional.of(DialogueText.fixed("Explicit")),
                        30,
                        Optional.empty(),
                        List.of(),
                        ReturnExit.INSTANCE
                )
        );
        DialogueSession session = new DialogueSession(
                content(Map.of(ROOT, root)),
                ROOT,
                root,
                1L,
                () -> 75
        );

        assertEquals(75, session.start().state().typewriterIntervalMs());
        finishPlayback(session);
        assertEquals(30, session.advance().state().typewriterIntervalMs());
    }

    @Test
    void resolvesVisualAssetBeforeCreatingScreenState() {
        ResourceLocation assetId = id("characters/guide");
        ResourceLocation imageId = id("characters/guide/happy.png");
        VisualObject reference = new VisualObject(
                Optional.of(assetId),
                Map.of(),
                "happy",
                0.75F,
                0.9F,
                VisualAnchor.BOTTOM_CENTER,
                1.0F,
                Optional.empty(),
                1.0F,
                true,
                5
        );
        Presentation presentation = new Presentation(
                THEME,
                Optional.empty(),
                DialogueBoxLayout.DEFAULT,
                Map.of("guide", reference),
                Optional.empty()
        );
        DialogueDefinition root = new DialogueDefinition(
                Optional.empty(),
                presentation,
                List.of(),
                new DialogueEnd(
                        Optional.of(DialogueText.fixed("Ready")),
                        Optional.empty(),
                        ReturnExit.INSTANCE
                )
        );
        DialogueSession session = new DialogueSession(
                lookup(
                        Map.of(ROOT, root),
                        true,
                        Map.of(
                                assetId,
                                new VisualAssetDefinition(
                                        Map.of("happy", imageId),
                                        VisualSampling.NEAREST
                                )
                        )
                ),
                ROOT,
                root,
                1L
        );

        DialogueSessionUpdate started = session.start();
        VisualObject resolved = started.state()
                .presentation()
                .orElseThrow()
                .visualObjects()
                .get("guide");

        assertTrue(started.effects().isEmpty(), started.effects()::toString);
        assertFalse(resolved.referencesAsset());
        assertEquals(imageId, resolved.initialImage());
        assertEquals(VisualSampling.NEAREST, resolved.sampling());
        assertEquals(0.75F, resolved.x());
    }

    @Test
    void resolvesSceneBeforeVisualAssetAndScreenState() {
        ResourceLocation sceneId = id("scenes/room");
        ResourceLocation assetId = id("characters/guide");
        ResourceLocation imageId = id("characters/guide/neutral.png");
        VisualObject reference = new VisualObject(
                Optional.of(assetId),
                Map.of(),
                "neutral",
                0.8F,
                1.0F,
                VisualAnchor.BOTTOM_CENTER,
                1.0F,
                Optional.empty(),
                1.0F,
                true,
                10
        );
        SceneDefinition scene = new SceneDefinition(
                Optional.empty(),
                Map.of("guide", reference),
                Optional.empty()
        );
        Presentation presentation = new Presentation(
                THEME,
                Optional.of(sceneId),
                Optional.empty(),
                DialogueBoxLayout.DEFAULT,
                Map.of(),
                Optional.empty()
        );
        DialogueDefinition root = new DialogueDefinition(
                Optional.empty(),
                presentation,
                List.of(),
                new DialogueEnd(
                        Optional.of(DialogueText.fixed("Ready")),
                        Optional.empty(),
                        ReturnExit.INSTANCE
                )
        );
        DialogueSession session = new DialogueSession(
                lookup(
                        Map.of(ROOT, root),
                        true,
                        Map.of(sceneId, scene),
                        Map.of(
                                assetId,
                                new VisualAssetDefinition(
                                        Map.of("neutral", imageId),
                                        VisualSampling.LINEAR
                                )
                        )
                ),
                ROOT,
                root,
                1L
        );

        DialogueSessionUpdate started = session.start();
        Presentation resolvedPresentation = started.state()
                .presentation()
                .orElseThrow();
        VisualObject resolved = resolvedPresentation.visualObjects()
                .get("guide");

        assertTrue(started.effects().isEmpty(), started.effects()::toString);
        assertTrue(resolvedPresentation.scene().isEmpty());
        assertFalse(resolved.referencesAsset());
        assertEquals(imageId, resolved.initialImage());
        assertEquals(0.8F, resolved.x());
    }

    @Test
    void filtersOptionsAndNavigatesBackToRoot() {
        DialogueOption option = new DialogueOption(
                "Open child",
                OptionIcon.DIALOGUE,
                new DialogueTarget(CHILD)
        );
        DialogueDefinition root = dialogue(
                "Root",
                new ChoiceExit(List.of(option))
        );
        DialogueDefinition child = dialogue("Child", ReturnExit.INSTANCE);
        DialogueSession session = new DialogueSession(
                content(Map.of(ROOT, root, CHILD, child)),
                ROOT,
                root,
                1L
        );

        DialogueSessionUpdate started = session.start();
        DialogueSessionEffect.QueryAccess query = assertInstanceOf(
                DialogueSessionEffect.QueryAccess.class,
                started.effects().getFirst()
        );
        assertEquals(List.of(CHILD), query.targets());

        assertFalse(session.handleAccessResult(999L, Map.of()).changed());
        DialogueSessionUpdate access = session.handleAccessResult(
                query.requestId(),
                Map.of(CHILD, DialogueAccessDecision.ALLOWED)
        );
        assertTrue(access.changed());

        finishPlayback(session);
        DialogueSessionUpdate selected = session.selectOption(option);
        DialogueSessionEffect.RequestTarget request = assertInstanceOf(
                DialogueSessionEffect.RequestTarget.class,
                selected.effects().getFirst()
        );
        DialogueSessionUpdate openedChild = session.handleTargetResult(
                request.requestId(),
                CHILD,
                DialogueAccessDecision.ALLOWED
        );
        assertTrue(openedChild.changed());
        assertEquals("Child", openedChild.state().text().orElseThrow());

        finishPlayback(session);
        DialogueSessionUpdate returned = session.advance();
        assertEquals("Root", returned.state().text().orElseThrow());
        assertEquals(4, returned.state().history().size());
    }

    @Test
    void rootReturnProducesCloseEffect() {
        DialogueDefinition root = dialogue("Done", ReturnExit.INSTANCE);
        DialogueSession session = new DialogueSession(
                content(Map.of(ROOT, root)),
                ROOT,
                root,
                5L
        );
        session.start();
        finishPlayback(session);

        DialogueSessionUpdate update = session.advance();

        assertInstanceOf(
                DialogueSessionEffect.Close.class,
                update.effects().getFirst()
        );
    }

    @Test
    void commandOptionWaitsForSuccessBeforeNavigating() {
        DialogueOption option = new DialogueOption(
                "Run and open child",
                OptionIcon.DIALOGUE,
                Optional.of("say hello"),
                new DialogueTarget(CHILD)
        );
        DialogueDefinition root = dialogue(
                "Root",
                new ChoiceExit(List.of(option))
        );
        DialogueDefinition child = dialogue("Child", ReturnExit.INSTANCE);
        DialogueSession session = new DialogueSession(
                content(Map.of(ROOT, root, CHILD, child)),
                ROOT,
                root,
                1L
        );

        DialogueSessionEffect.QueryAccess query = assertInstanceOf(
                DialogueSessionEffect.QueryAccess.class,
                session.start().effects().getFirst()
        );
        session.handleAccessResult(
                query.requestId(),
                Map.of(CHILD, DialogueAccessDecision.ALLOWED)
        );
        finishPlayback(session);

        DialogueSessionUpdate selected = session.selectOption(option);
        DialogueSessionEffect.ExecuteOptionCommand command = assertInstanceOf(
                DialogueSessionEffect.ExecuteOptionCommand.class,
                selected.effects().getFirst()
        );
        assertEquals(ROOT, command.sourceDialogue());
        assertEquals(0, command.optionIndex());
        assertTrue(selected.state().requestingTarget());
        assertFalse(session.selectOption(option).changed());
        assertFalse(session.handleOptionCommandResult(
                command.requestId() + 1,
                ROOT,
                0,
                OptionCommandDecision.EXECUTED
        ).changed());

        DialogueSessionUpdate opened = session.handleOptionCommandResult(
                command.requestId(),
                ROOT,
                0,
                OptionCommandDecision.EXECUTED
        );
        assertEquals("Child", opened.state().text().orElseThrow());
        assertFalse(opened.state().requestingTarget());
        assertEquals(3, opened.state().history().size());
    }

    @Test
    void failedCommandKeepsOptionsAndCanBeRetried() {
        DialogueOption option = new DialogueOption(
                "Run and close",
                OptionIcon.NONE,
                Optional.of("say hello"),
                ReturnTarget.INSTANCE
        );
        DialogueDefinition root = dialogue(
                "Root",
                new ChoiceExit(List.of(option))
        );
        DialogueSession session = new DialogueSession(
                content(Map.of(ROOT, root)),
                ROOT,
                root,
                1L
        );
        session.start();
        finishPlayback(session);

        DialogueSessionEffect.ExecuteOptionCommand first = assertInstanceOf(
                DialogueSessionEffect.ExecuteOptionCommand.class,
                session.selectOption(option).effects().getFirst()
        );
        DialogueSessionUpdate failed = session.handleOptionCommandResult(
                first.requestId(),
                ROOT,
                0,
                OptionCommandDecision.COMMAND_FAILED
        );
        assertFalse(failed.state().requestingTarget());
        assertEquals(List.of(option), failed.state().options());
        assertTrue(failed.state().error().isPresent());
        assertTrue(failed.effects().isEmpty());

        DialogueSessionEffect.ExecuteOptionCommand retry = assertInstanceOf(
                DialogueSessionEffect.ExecuteOptionCommand.class,
                session.selectOption(option).effects().getFirst()
        );
        DialogueSessionUpdate succeeded = session.handleOptionCommandResult(
                retry.requestId(),
                ROOT,
                0,
                OptionCommandDecision.EXECUTED
        );
        assertInstanceOf(
                DialogueSessionEffect.Close.class,
                succeeded.effects().getFirst()
        );
    }

    @Test
    void missingLocalCommandTargetPreventsServerRequest() {
        DialogueOption option = new DialogueOption(
                "Missing child",
                OptionIcon.NONE,
                Optional.of("say hello"),
                new DialogueTarget(CHILD)
        );
        DialogueDefinition root = dialogue(
                "Root",
                new ChoiceExit(List.of(option))
        );
        DialogueSession session = new DialogueSession(
                content(Map.of(ROOT, root)),
                ROOT,
                root,
                1L
        );
        DialogueSessionEffect.QueryAccess query = assertInstanceOf(
                DialogueSessionEffect.QueryAccess.class,
                session.start().effects().getFirst()
        );
        session.handleAccessResult(
                query.requestId(),
                Map.of(CHILD, DialogueAccessDecision.ALLOWED)
        );
        finishPlayback(session);

        DialogueSessionUpdate selected = session.selectOption(option);

        assertInstanceOf(
                DialogueSessionEffect.ReportError.class,
                selected.effects().getFirst()
        );
        assertFalse(selected.state().requestingTarget());
        assertTrue(selected.effects().stream().noneMatch(
                DialogueSessionEffect.ExecuteOptionCommand.class::isInstance
        ));
    }

    @Test
    void missingThemeUsesDefaultAndReportsError() {
        DialogueDefinition root = dialogue("Fallback", ReturnExit.INSTANCE);
        DialogueSession session = new DialogueSession(
                contentWithoutTheme(Map.of(ROOT, root)),
                ROOT,
                root,
                1L
        );

        DialogueSessionUpdate update = session.start();

        assertEquals(ThemeDefinition.DEFAULT, update.state().theme().orElseThrow());
        assertTrue(update.effects().stream().anyMatch(
                DialogueSessionEffect.ReportError.class::isInstance
        ));
    }

    @Test
    void keepsRandomStepTextStableForWholeSession() {
        DialogueOption option = new DialogueOption(
                "Open child",
                OptionIcon.NONE,
                new DialogueTarget(CHILD)
        );
        DialogueText randomText = new DialogueText(List.of(
                "First variant",
                "Second variant"
        ));
        DialogueDefinition root = new DialogueDefinition(
                Optional.empty(),
                new Presentation(THEME),
                List.of(new DialogueStep(
                        Optional.of(randomText),
                        Optional.empty()
                )),
                new DialogueEnd(
                        Optional.of(DialogueText.fixed("Choose")),
                        Optional.empty(),
                        new ChoiceExit(List.of(option))
                )
        );
        DialogueDefinition child = dialogue("Child", ReturnExit.INSTANCE);
        DialogueSession session = new DialogueSession(
                content(Map.of(ROOT, root, CHILD, child)),
                ROOT,
                root,
                1L,
                new FixedRandom(1)
        );

        DialogueSessionUpdate started = session.start();
        assertEquals("Second variant", started.state().text().orElseThrow());
        assertEquals(
                "Second variant",
                started.state().history().getFirst().content()
        );
        DialogueSessionEffect.QueryAccess query = assertInstanceOf(
                DialogueSessionEffect.QueryAccess.class,
                started.effects().getFirst()
        );
        session.handleAccessResult(
                query.requestId(),
                Map.of(CHILD, DialogueAccessDecision.ALLOWED)
        );

        finishPlayback(session);
        session.advance();
        finishPlayback(session);
        DialogueSessionEffect.RequestTarget request = assertInstanceOf(
                DialogueSessionEffect.RequestTarget.class,
                session.selectOption(option).effects().getFirst()
        );
        session.handleTargetResult(
                request.requestId(),
                CHILD,
                DialogueAccessDecision.ALLOWED
        );
        finishPlayback(session);

        DialogueSessionUpdate returned = session.advance();

        assertEquals("Second variant", returned.state().text().orElseThrow());
        assertEquals(
                "Second variant",
                returned.state().history().getLast().content()
        );
    }

    @Test
    void newSessionCanSelectAnotherEndText() {
        DialogueText randomText = new DialogueText(List.of("First", "Second"));
        DialogueDefinition root = new DialogueDefinition(
                Optional.empty(),
                new Presentation(THEME),
                List.of(),
                new DialogueEnd(
                        Optional.of(randomText),
                        Optional.empty(),
                        ReturnExit.INSTANCE
                )
        );

        DialogueSession first = new DialogueSession(
                content(Map.of(ROOT, root)),
                ROOT,
                root,
                1L,
                new FixedRandom(0)
        );
        DialogueSession second = new DialogueSession(
                content(Map.of(ROOT, root)),
                ROOT,
                root,
                1L,
                new FixedRandom(1)
        );

        assertEquals("First", first.start().state().text().orElseThrow());
        assertEquals("Second", second.start().state().text().orElseThrow());
    }

    @Test
    void exposesTypewriterIntervalForEachDialogueNode() {
        DialogueDefinition root = new DialogueDefinition(
                Optional.empty(),
                new Presentation(THEME),
                List.of(new DialogueStep(
                        Optional.of(DialogueText.fixed("Slow")),
                        80,
                        Optional.empty(),
                        List.of()
                )),
                new DialogueEnd(
                        Optional.of(DialogueText.fixed("Instant")),
                        0,
                        Optional.empty(),
                        List.of(),
                        ReturnExit.INSTANCE
                )
        );
        DialogueSession session = new DialogueSession(
                content(Map.of(ROOT, root)),
                ROOT,
                root,
                1L
        );

        assertEquals(80, session.start().state().typewriterIntervalMs());
        finishPlayback(session);
        assertEquals(0, session.advance().state().typewriterIntervalMs());
    }

    @Test
    void playbackTokenChangesBetweenStepsWithinOneDialogueGeneration() {
        DialogueDefinition root = new DialogueDefinition(
                Optional.empty(),
                new Presentation(THEME),
                List.of(
                        new DialogueStep(
                                Optional.of(DialogueText.fixed("First")),
                                40,
                                Optional.empty(),
                                List.of()
                        ),
                        new DialogueStep(
                                Optional.of(DialogueText.fixed("Second")),
                                40,
                                Optional.empty(),
                                List.of()
                        )
                ),
                new DialogueEnd(
                        Optional.of(DialogueText.fixed("End")),
                        Optional.empty(),
                        ReturnExit.INSTANCE
                )
        );
        DialogueSession session = new DialogueSession(
                content(Map.of(ROOT, root)),
                ROOT,
                root,
                1L
        );

        DialogueScreenState first = session.start().state();
        finishPlayback(session);
        DialogueScreenState second = session.advance().state();

        assertEquals(first.generation(), second.generation());
        assertNotEquals(
                first.scenePlayback().orElseThrow().token(),
                second.scenePlayback().orElseThrow().token()
        );
    }

    @Test
    void skipsIntermediateTextButSettlesStateAndCompletesEnd() {
        ResourceLocation alpha = id("alpha");
        ResourceLocation beta = id("beta");
        CountingRandom random = new CountingRandom();
        DialogueDefinition root = new DialogueDefinition(
                Optional.empty(),
                Optional.of("**Summary**"),
                new Presentation(THEME),
                List.of(
                        new DialogueStep(
                                Optional.of(DialogueText.fixed("Current")),
                                Optional.of(new SetSpeaker(alpha))
                        ),
                        new DialogueStep(
                                Optional.of(new DialogueText(List.of(
                                        "Skipped A",
                                        "Skipped B"
                                ))),
                                DialogueStep.DEFAULT_TYPEWRITER_INTERVAL_MS,
                                Optional.of(new SetSpeaker(beta)),
                                List.of(action("dialogue", 0.1F, 0.0F))
                        )
                ),
                new DialogueEnd(
                        Optional.of(DialogueText.fixed("End")),
                        DialogueStep.DEFAULT_TYPEWRITER_INTERVAL_MS,
                        Optional.empty(),
                        List.of(action("dialogue", 0.0F, -0.1F)),
                        ReturnExit.INSTANCE
                )
        );
        DialogueContentLookup lookup = new DialogueContentLookup() {
            @Override
            public Optional<DialogueDefinition> dialogue(ResourceLocation id) {
                return ROOT.equals(id) ? Optional.of(root) : Optional.empty();
            }

            @Override
            public Optional<SpeakerDefinition> speaker(ResourceLocation id) {
                if (alpha.equals(id)) {
                    return Optional.of(new SpeakerDefinition("Alpha"));
                }
                if (beta.equals(id)) {
                    return Optional.of(new SpeakerDefinition("Beta"));
                }
                return Optional.empty();
            }

            @Override
            public Optional<ThemeDefinition> theme(ResourceLocation id) {
                return THEME.equals(id)
                        ? Optional.of(ThemeDefinition.DEFAULT)
                        : Optional.empty();
            }

            @Override
            public Optional<SceneAction> action(ResourceLocation id) {
                return Optional.empty();
            }
        };
        DialogueSession session = new DialogueSession(
                lookup,
                ROOT,
                root,
                1L,
                random
        );

        DialogueSessionUpdate started = session.start();
        assertEquals("Current", started.state().text().orElseThrow());
        assertEquals("**Summary**", started.state().skipSummary().orElseThrow());
        assertTrue(started.state().canSkipToEnd());

        DialogueSessionUpdate skipped = session.skipToEnd();

        assertEquals(PlaybackPhase.READY, skipped.state().playbackPhase());
        assertTrue(skipped.state().playbackSkipped());
        assertFalse(skipped.state().canSkipToEnd());
        assertEquals("End", skipped.state().text().orElseThrow());
        assertEquals("Beta", skipped.state().speaker().orElseThrow());
        assertEquals(
                List.of("Current", "End"),
                skipped.state().history().stream()
                        .map(DialogueHistoryEntry::content)
                        .toList()
        );
        assertEquals(2, random.selections);
        assertEquals(
                0.6F,
                skipped.state().scenePlayback().orElseThrow()
                        .end().dialogueBox().x(),
                0.0001F
        );
        assertEquals(
                0.88F,
                skipped.state().scenePlayback().orElseThrow()
                        .end().dialogueBox().y(),
                0.0001F
        );
        assertInstanceOf(
                DialogueSessionEffect.Close.class,
                session.advance().effects().getFirst()
        );
    }

    @Test
    void skipCompletesPlayingEndWithoutExecutingExit() {
        DialogueDefinition root = dialogue("End", ReturnExit.INSTANCE);
        DialogueSession session = new DialogueSession(
                content(Map.of(ROOT, root)),
                ROOT,
                root,
                1L
        );
        session.start();

        assertFalse(session.screenState().canSkipToEnd());

        DialogueSessionUpdate skipped = session.skipToEnd();

        assertEquals(PlaybackPhase.READY, skipped.state().playbackPhase());
        assertTrue(skipped.effects().isEmpty());
        assertFalse(session.skipToEnd().changed());
    }

    private static void finishPlayback(DialogueSession session) {
        DialogueScreenState state = session.screenState();
        long token = state.scenePlayback().orElseThrow().token();
        session.completeScene(state.generation(), token);
        session.completeText(state.generation(), token);
    }

    private static SceneActionCall action(
            String target,
            float x,
            float y
    ) {
        return new SceneActionCall(
                target,
                0,
                new ActionSpec.Inline(new SceneAction(
                        100,
                        ActionEasing.LINEAR,
                        true,
                        x == 0.0F
                                ? Optional.empty()
                                : Optional.of(track(x)),
                        y == 0.0F
                                ? Optional.empty()
                                : Optional.of(track(y)),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                ))
        );
    }

    private static NumericTrack track(float finalValue) {
        return new NumericTrack(List.of(
                new NumericKeyframe(1.0F, finalValue)
        ));
    }

    private static DialogueDefinition dialogue(
            String text,
            DialogueExit exit
    ) {
        return new DialogueDefinition(
                Optional.empty(),
                new Presentation(THEME),
                List.of(),
                new DialogueEnd(
                        Optional.of(DialogueText.fixed(text)),
                        Optional.empty(),
                        exit
                )
        );
    }

    private static DialogueContentLookup content(
            Map<ResourceLocation, DialogueDefinition> dialogues
    ) {
        return lookup(dialogues, true);
    }

    private static DialogueContentLookup contentWithoutTheme(
            Map<ResourceLocation, DialogueDefinition> dialogues
    ) {
        return lookup(dialogues, false);
    }

    private static DialogueContentLookup lookup(
            Map<ResourceLocation, DialogueDefinition> dialogues,
            boolean hasTheme
    ) {
        return lookup(dialogues, hasTheme, Map.of());
    }

    private static DialogueContentLookup lookup(
            Map<ResourceLocation, DialogueDefinition> dialogues,
            boolean hasTheme,
            Map<ResourceLocation, VisualAssetDefinition> visualAssets
    ) {
        return lookup(dialogues, hasTheme, Map.of(), visualAssets);
    }

    private static DialogueContentLookup lookup(
            Map<ResourceLocation, DialogueDefinition> dialogues,
            boolean hasTheme,
            Map<ResourceLocation, SceneDefinition> scenes,
            Map<ResourceLocation, VisualAssetDefinition> visualAssets
    ) {
        return new DialogueContentLookup() {
            @Override
            public Optional<DialogueDefinition> dialogue(ResourceLocation id) {
                return Optional.ofNullable(dialogues.get(id));
            }

            @Override
            public Optional<SpeakerDefinition> speaker(ResourceLocation id) {
                return Optional.empty();
            }

            @Override
            public Optional<ThemeDefinition> theme(ResourceLocation id) {
                return hasTheme && THEME.equals(id)
                        ? Optional.of(ThemeDefinition.DEFAULT)
                        : Optional.empty();
            }

            @Override
            public Optional<SceneAction> action(ResourceLocation id) {
                return Optional.empty();
            }

            @Override
            public Optional<VisualAssetDefinition> visualAsset(
                    ResourceLocation id
            ) {
                return Optional.ofNullable(visualAssets.get(id));
            }

            @Override
            public Optional<SceneDefinition> scene(ResourceLocation id) {
                return Optional.ofNullable(scenes.get(id));
            }
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static final class FixedRandom implements RandomGenerator {
        private final int index;

        private FixedRandom(int index) {
            this.index = index;
        }

        @Override
        public long nextLong() {
            return index;
        }

        @Override
        public int nextInt(int bound) {
            return Math.min(index, bound - 1);
        }
    }

    private static final class CountingRandom implements RandomGenerator {
        private int selections;

        @Override
        public long nextLong() {
            selections++;
            return 0L;
        }

        @Override
        public int nextInt(int bound) {
            selections++;
            return 0;
        }
    }
}
