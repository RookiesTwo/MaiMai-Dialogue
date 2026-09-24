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
    private final PreviewScenario scenario;
    public record SimulationResult(String kind, String detail) {}
    private final List<SimulationResult> simulationResults = new ArrayList<>();
    public List<SimulationResult> simulationResults() { return List.copyOf(simulationResults); }
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
        this(content, root, defaultIntervalMs, startStep, PreviewScenario.defaults());
    }
    public EditorPreviewSession(DialogueContentLookup content, ResourceLocation root, int defaultIntervalMs, int startStep, PreviewScenario scenario) {
        this(content, root, defaultIntervalMs, startStep, scenario, 1L);
    }

    /** Reused preview views need fresh generation/token values, including when replaying the same Step. */
    public EditorPreviewSession(DialogueContentLookup content, ResourceLocation root, int defaultIntervalMs, int startStep,
                                PreviewScenario scenario, long firstGeneration) {
        this.scenario = scenario;
        this.content = scenario.wrap(content);
        try {
            DialogueDefinition definition = this.content.dialogue(root).orElseThrow(() ->
                    new IllegalArgumentException("Missing dialogue: " + root));
            if (!allowed(root, definition)) throw new IllegalArgumentException("Requires unmet: " + root);
            int target = startStep == -1 ? definition.steps().size() : startStep;
            if (target < 0 || target > definition.steps().size()) {
                throw new IllegalArgumentException("Invalid preview step: " + startStep);
            }
            // 完成令牌只存在于本地解释器中，完成效果只记入模拟结果。
            session = new DialogueSession(this.content, root, definition, firstGeneration, () -> defaultIntervalMs,
                    definition.mustComplete() ? java.util.Optional.of(new java.util.UUID(0, 1)) : java.util.Optional.empty());
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
    // 编辑刷新只完成当前节点，不能把已经 READY 的 End 当成一次推进而执行退出或命令。
    public void prepareForEditing() {
        if (running() && state.playbackPhase() == PlaybackPhase.PLAYING) advance();
    }
    public void selectOptionAfterRefresh(DialogueOption option) {
        prepareForEditing();
        if (running() && state.options().contains(option)) selectOption(option);
    }
    // 手动采样期间修改动作后，用户推进时按原阶段恢复语义，再推进最新定义。
    public void advanceAfterRefresh(PlaybackPhase previous) {
        if (previous == PlaybackPhase.READY && state.playbackPhase() == PlaybackPhase.PLAYING) advance();
        advance();
    }
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
        Set<DialogueSessionUpdate> simulatedFailures = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
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
                            for (String line : choices.options().get(command.optionIndex()).commands()) {
                                simulatedCommands.add(line);
                                simulationResults.add(new SimulationResult(scenario.commandFailure() ? "command_failed" : "command", line));
                                if (scenario.commandFailure()) break;
                            }
                        }
                        var response = session.handleOptionCommandResult(command.requestId(), command.sourceDialogue(),
                                command.optionIndex(), scenario.commandFailure() ? OptionCommandDecision.COMMAND_FAILED : OptionCommandDecision.EXECUTED);
                        if (scenario.commandFailure()) simulatedFailures.add(response);
                        pending.add(response);
                    }
                    case DialogueSessionEffect.Close ignored -> {
                        bgm.clear();
                        status = Status.FINISHED;
                        session = null;
                        state = DialogueScreenState.empty(0);
                    }
                    case DialogueSessionEffect.ReportError report -> {
                        // 人为模拟的命令失败由运行时错误状态呈现，保留当前选项；其他准备错误仍阻止预览。
                        if (!simulatedFailures.contains(update)) throw new IllegalArgumentException(report.message());
                    }
                    case DialogueSessionEffect.ApplyBgm operation -> bgm.add(operation);
                    case DialogueSessionEffect.CompleteRequiredDialogue completed ->
                            simulationResults.add(new SimulationResult("completed", completed.rootDialogueId().toString()));
                }
                if (!running()) break;
            }
        }
    }

    private DialogueAccessDecision access(ResourceLocation target) {
        return content.dialogue(target).map(definition -> allowed(target, definition)
                ? DialogueAccessDecision.ALLOWED : DialogueAccessDecision.REQUIREMENTS_NOT_MET)
                .orElse(DialogueAccessDecision.DIALOGUE_NOT_FOUND);
    }

    private boolean allowed(ResourceLocation id, DialogueDefinition definition) {
        boolean allowed = definition.requires().map(requirement -> requirement.evaluate(scenario.nodes())).orElse(true);
        definition.requires().ifPresent(requirement -> {
            var result = new SimulationResult(allowed ? "allowed" : "blocked", id + " · " + requirement.source());
            if (!simulationResults.contains(result)) simulationResults.add(result);
        });
        return allowed;
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
