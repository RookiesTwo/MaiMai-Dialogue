package top.rookiestwo.maimai_dialogue_editor.client.ui.preview;

import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;

import top.rookiestwo.maimai_dialogue_editor.client.preview.EditorTimelinePreview;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.ChoicePresenter;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.view.View;
import net.minecraft.client.resources.language.I18n;
import top.rookiestwo.maimai_dialogue_editor.client.EditorContentPreparation;
import top.rookiestwo.maimai_dialogue.client.scene.*;
import top.rookiestwo.maimai_dialogue_editor.document.*;
import top.rookiestwo.maimai_dialogue_editor.preview.ActionSceneContext;
import top.rookiestwo.maimai_dialogue_editor.project.*;
import java.util.*;

/** Shared insertion commands for a timeline context menu and the inspector's playhead buttons. */
public final class EditorActionKeyframes {
    private record Target(long project, ProjectDraft draft, ActionWorkspace.Context context, int index,
                          ScenePlayback playback, ResolvedActionCall call) {}
    private final ProjectWorkspace project;
    private final EditorTimelinePreview preview;
    private final ChoicePresenter choices;
    public EditorActionKeyframes(ProjectWorkspace project, EditorTimelinePreview preview, ChoicePresenter choices) {
        this.project = project; this.preview = preview; this.choices = choices;
    }
    private Target current() {
        var context = project.actions().context();
        if (context == null || !preview.canSeek() || !project.actions().active() || project.actions().editing()) return null;
        int index = context.standalone() ? 0 : project.actions().selected();
        var call = preview.model().call(index);
        return call == null ? null : new Target(project.projectGeneration(), project.draft(), context, index, preview.playback(), call);
    }
    private boolean valid(Target target) {
        var now = current();
        return target != null && now != null && target.project() == now.project() && target.draft() == now.draft()
                && target.playback() == now.playback() && target.context().equals(now.context()) && target.index() == now.index();
    }
    private static String issue(Target target, String track, int time) {
        return target == null ? "unavailable" : ActionKeyframes.plan(target.call().action(), track, time, target.call().delayMs()).error();
    }
    public boolean canAdd(String track) { return issue(current(), track, preview.model().position()).isEmpty(); }
    public String tooltip(String track) {
        var target = current(); int time = preview.model().position(); String issue = issue(target, track, time);
        if (!issue.isEmpty()) return EditorWidgets.tr("timeline.keyframe." + issue);
        return I18n.get("gui.maimai_dialogue_editor.timeline.keyframe.at_playhead", time,
                EditorWidgets.tr("action." + track), time - target.call().delayMs());
    }
    public void addAtPlayhead(View anchor, String track) {
        var target = current(); int time = preview.model().position();
        if (!issue(target, track, time).isEmpty()) return;
        preview.seek(target.playback(), time);
        insert(anchor, 0, anchor.getHeight(), target, track, time);
    }
    void show(View anchor, float x, float y, Integer lane, int time) {
        if (!preview.canSeek()) return;
        var context = project.actions().context();
        if (context == null) return;
        if (lane != null && lane < 0) { message(anchor, x, y, EditorWidgets.tr("timeline.keyframe.readonly")); return; }
        int index = lane != null ? lane : context.standalone() ? 0 : project.actions().selected();
        var playback = preview.playback(); var draft = project.draft();
        preview.seek(playback, time);
        if (index >= 0) { tracks(anchor, x, y, index, time); return; }
        var items = preview.model().lanes().stream().filter(value -> value.callIndex() >= 0)
                .map(value -> new ChoicePresenter.Item(Integer.toString(value.callIndex()),
                        (value.callIndex() + 1) + " · " + (value.target().isEmpty() ? EditorWidgets.tr("action.no_target") : value.target()),
                        time >= value.startMs() && time <= value.endMs())).toList();
        if (items.isEmpty()) { message(anchor, x, y, EditorWidgets.tr("timeline.keyframe.no_action")); return; }
        choices.showMenuAt(anchor, x, y, items, selected -> {
            if (draft == project.draft() && playback == preview.playback() && context.equals(project.actions().context()))
                tracks(anchor, x, y, Integer.parseInt(selected), time);
        });
    }
    private void tracks(View anchor, float x, float y, int index, int time) {
        if (!project.actions().context().standalone()) project.actions().select(index);
        var target = current(); if (target == null) return;
        var items = new ArrayList<ChoicePresenter.Item>();
        items.add(new ChoicePresenter.Item("", EditorWidgets.tr("action.add_keyframe") + " · " + time + " ms", false));
        boolean reference = project.actions().mode().equals("reference");
        for (String track : ActionFields.TRACKS) {
            if (ActionKeyframes.track(target.call().action(), track).isEmpty()) continue;
            String issue = issue(target, track, time);
            String label = EditorWidgets.tr("action." + track);
            if (!issue.isEmpty()) label += " · " + EditorWidgets.tr("timeline.keyframe." + issue);
            else if (reference) label += " · " + EditorWidgets.tr("timeline.keyframe.make_inline");
            items.add(new ChoicePresenter.Item(track, label, issue.isEmpty()));
        }
        if (items.size() == 1) items.add(new ChoicePresenter.Item("", EditorWidgets.tr("timeline.keyframe.no_track"), false));
        choices.showMenuAt(anchor, x, y, items, track -> {
            if (valid(target)) insert(anchor, x, y, target, track, time);
        });
    }
    private void insert(View anchor, float x, float y, Target target, String track, int time) {
        if (!valid(target) || !issue(target, track, time).isEmpty()) return;
        if (!project.actions().mode().equals("reference")) {
            project.actions().addFrameAt(track, time, target.call().delayMs(), null); return;
        }
        String reference = project.actions().text(true, "action.id", "");
        EditorContentPreparation.prepare(project, external -> ActionSceneContext.reference(target.draft(), reference, external), (definition, failure) -> {
            if (!anchor.isAttachedToWindow() || !valid(target)) return;
            if (failure == null) project.actions().addFrameAt(track, time, target.call().delayMs(), definition);
            else message(anchor, x, y, String.valueOf(failure.getMessage()));
        });
    }
    private void message(View anchor, float x, float y, String text) {
        choices.showMenuAt(anchor, x, y, List.of(new ChoicePresenter.Item("", text, false)), ignored -> {});
    }
}
