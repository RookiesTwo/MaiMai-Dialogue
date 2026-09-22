package top.rookiestwo.maimai_dialogue_editor.preview;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.session.*;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.branch.DialogueOption;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Local effect interpreter. Audio effects are drained by the client host; commands/progress remain simulated. */
public final class EditorPreviewSession {
    public enum Status { RUNNING, FINISHED, STOPPED, FAILED }

    private final DialogueContentLookup content;
    private final List<String> simulatedCommands = new ArrayList<>();
    private final List<DialogueSessionEffect.ApplyBgm> bgm = new ArrayList<>();
    private DialogueSession session;
    private DialogueScreenState state = DialogueScreenState.empty(0);
    private Status status = Status.STOPPED;
    private String error = "";
    private DialogueSession.Position position;

    public EditorPreviewSession(DialogueContentLookup content, ResourceLocation root, int defaultIntervalMs) {
        this(content, root, defaultIntervalMs, 0);
    }

    /** A step index of -1 selects End. Earlier steps are settled through the real runtime. */
    public EditorPreviewSession(DialogueContentLookup content, ResourceLocation root, int defaultIntervalMs, int startStep) {
        this.content = content;
        try {
            DialogueDefinition definition = content.dialogue(root).orElseThrow(() ->
                    new IllegalArgumentException("Missing dialogue: " + root));
            if (!allowed(definition)) throw new IllegalArgumentException("Requires unmet: " + root);
            int target = startStep == -1 ? definition.steps().size() : startStep;
            if (target < 0 || target > definition.steps().size()) {
                throw new IllegalArgumentException("Invalid preview step: " + startStep);
            }
            // No completion token: even must_complete drafts cannot grant real progress.
            session = new DialogueSession(content, root, definition, 1L, () -> defaultIntervalMs);
            status = Status.RUNNING;
            apply(session.start());
            // Never traverse End's exit while seeking; the requested node still plays normally.
            for (int index = 0; index < target && running(); index++) {
                if (state.playbackPhase() == PlaybackPhase.PLAYING) apply(session.advance());
                if (running()) apply(session.advance());
            }
            // Seeking settles audio state without briefly starting every skipped track.
            if (bgm.size() > 1) { var last = bgm.getLast(); bgm.clear(); bgm.add(last); }
        } catch (RuntimeException failure) {
            fail(failure);
        }
    }

    public DialogueScreenState state() { return state; }
    public Status status() { return status; }
    public boolean running() { return status == Status.RUNNING; }
    public String error() { return error; }
    public List<String> simulatedCommands() { return List.copyOf(simulatedCommands); }
    public DialogueSession.Position position() { return position; }
    public List<DialogueSessionEffect.ApplyBgm> drainBgm() {
        var result = List.copyOf(bgm); bgm.clear(); return result;
    }

    public void advance() { update(DialogueSession::advance); }
    public void skipToEnd() { update(DialogueSession::skipToEnd); }
    public void selectOption(DialogueOption option) { update(current -> current.selectOption(option)); }
    public void completeScene(long generation, long token) { update(current -> current.completeScene(generation, token)); }
    public void completeText(long generation, long token) { update(current -> current.completeText(generation, token)); }

    public void stop() {
        session = null;
        state = DialogueScreenState.empty(0);
        status = Status.STOPPED;
        position = null;
        simulatedCommands.clear();
        bgm.clear();
    }

    private void update(Function<DialogueSession, DialogueSessionUpdate> operation) {
        if (!running()) return;
        try { apply(operation.apply(session)); }
        catch (RuntimeException failure) { fail(failure); }
    }

    private void apply(DialogueSessionUpdate first) {
        ArrayDeque<DialogueSessionUpdate> pending = new ArrayDeque<>();
        pending.add(first);
        while (!pending.isEmpty() && running()) {
            var update = pending.removeFirst();
            state = update.state();
            position = session.position();
            for (DialogueSessionEffect effect : update.effects()) {
                switch (effect) {
                    case DialogueSessionEffect.QueryAccess query -> {
                        var decisions = new LinkedHashMap<ResourceLocation, DialogueAccessDecision>();
                        for (var target : query.targets()) decisions.put(target, access(target));
                        pending.add(session.handleAccessResult(query.requestId(), decisions));
                    }
                    case DialogueSessionEffect.RequestTarget target -> pending.add(session.handleTargetResult(
                            target.requestId(), target.target(), access(target.target())));
                    case DialogueSessionEffect.ExecuteOptionCommand command -> {
                        // Record only, then simulate successful execution to preserve normal target navigation.
                        var source = content.dialogue(command.sourceDialogue()).orElseThrow();
                        if (source.end().exit() instanceof top.rookiestwo.maimai_dialogue.dialogue.branch.ChoiceExit choices) {
                            simulatedCommands.addAll(choices.options().get(command.optionIndex()).commands());
                        }
                        pending.add(session.handleOptionCommandResult(command.requestId(), command.sourceDialogue(),
                                command.optionIndex(), OptionCommandDecision.EXECUTED));
                    }
                    case DialogueSessionEffect.Close ignored -> {
                        bgm.clear();
                        status = Status.FINISHED;
                        session = null;
                        state = DialogueScreenState.empty(0);
                    }
                    case DialogueSessionEffect.ReportError report -> throw new IllegalArgumentException(report.message());
                    case DialogueSessionEffect.ApplyBgm operation -> bgm.add(operation);
                    case DialogueSessionEffect.CompleteRequiredDialogue ignored -> { /* Never persist progress. */ }
                }
                if (!running()) break;
            }
        }
    }

    private DialogueAccessDecision access(ResourceLocation target) {
        return content.dialogue(target).map(definition -> allowed(definition)
                ? DialogueAccessDecision.ALLOWED : DialogueAccessDecision.REQUIREMENTS_NOT_MET)
                .orElse(DialogueAccessDecision.DIALOGUE_NOT_FOUND);
    }

    private static boolean allowed(DialogueDefinition definition) {
        // A fresh local progress set; configurable simulation is introduced in step 11.
        return definition.requires().map(requirement -> requirement.evaluate(Set.of())).orElse(true);
    }

    private void fail(RuntimeException failure) {
        bgm.clear();
        error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        session = null;
        state = DialogueScreenState.empty(0);
        status = Status.FAILED;
        position = null;
    }
}
