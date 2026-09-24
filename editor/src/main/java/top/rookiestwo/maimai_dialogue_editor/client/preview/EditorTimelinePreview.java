package top.rookiestwo.maimai_dialogue_editor.client.preview;

import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.client.ui.screen.DialogueFragment;
import top.rookiestwo.maimai_dialogue_editor.document.ActionWorkspace;
import top.rookiestwo.maimai_dialogue_editor.preview.ActionTimeline;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// 时间轴绑定、手动采样和帧合并；播放与音频生命周期由所属预览提供。
public final class EditorTimelinePreview {
    interface Surface {
        PreviewDisplay display();
        DialogueFragment fragment();
        boolean active();
        boolean canSeek();
        void pauseForSeek();
        void replay();
    }

    private record Binding(long project, ProjectDraft draft, ActionWorkspace.Context context) {}
    private final ProjectWorkspace workspace;
    private final Surface surface;
    private final ActionTimeline model = new ActionTimeline();
    private final Set<Runnable> observers = new LinkedHashSet<>();
    private Runnable changed = () -> {};
    private Binding binding;
    private boolean framePending;
    private ScenePlayback canvasBase, canvasFrame, sampledPlayback;
    private int sampledPosition;

    EditorTimelinePreview(ProjectWorkspace workspace, Surface surface) {
        this.workspace = workspace;
        this.surface = surface;
    }

    private Binding currentBinding() {
        return new Binding(workspace.projectGeneration(), workspace.draft(), workspace.actions().context());
    }

    public ActionTimeline model() { return model; }
    public ScenePlayback playback() { return Objects.equals(binding, currentBinding()) ? model.playback() : null; }
    public void setListener(Runnable listener) { changed = listener; }
    public void addObserver(Runnable listener) { observers.add(listener); }
    public void removeObserver(Runnable listener) { observers.remove(listener); }
    void notifyChanged() { changed.run(); List.copyOf(observers).forEach(Runnable::run); }

    void bind(Object owner, ScenePlayback scene, int calls, boolean playing) {
        canvasBase = canvasFrame = null;
        sampledPlayback = null;
        binding = currentBinding();
        model.bind(owner, scene, calls, playing);
        notifyChanged();
    }

    void follow(Object owner, long token, int elapsed) {
        if (model.follow(owner, token, elapsed)) notifyChanged();
    }

    void clear(Object owner) {
        model.clear(owner);
        if (canvasBase != model.playback()) canvasBase = canvasFrame = null;
        if (sampledPlayback != model.playback()) sampledPlayback = null;
        notifyChanged();
    }

    void clear() { model.clear(); notifyChanged(); }

    void freezeFrame() {
        var fragment = surface.fragment();
        if (fragment != null && model.playback() != null) {
            fragment.renderScenePlaybackPreview(
                    canvasBase == model.playback() && canvasFrame != null ? canvasFrame : model.playback(), model.position());
            sampledPlayback = model.playback(); sampledPosition = model.position();
        }
    }

    public boolean canvasReady() {
        return model.manual() && sampledPlayback == playback() && sampledPosition == model.position();
    }

    public DialogueFragment fragment() { return canSeek() ? surface.fragment() : null; }
    public boolean canSeek() { return playback() != null && surface.canSeek(); }
    public void replay() { surface.replay(); }

    public void seek(ScenePlayback expected, int elapsed) {
        if (!canSeek() || !model.seek(expected, elapsed)) return;
        canvasBase = canvasFrame = null;
        surface.pauseForSeek();
        requestFrame();
        notifyChanged();
    }

    public void renderCanvasFrame(ScenePlayback expected, ScenePlayback frame, boolean immediate) {
        if (expected != model.playback()) return;
        canvasBase = expected; canvasFrame = frame;
        if (immediate) { cancelFrame(); freezeFrame(); }
        else requestFrame();
    }

    private final Runnable frame = this::renderFrame;

    private void renderFrame() {
        framePending = false;
        if (surface.active() && model.manual() && canSeek()) {
            freezeFrame();
            var display = surface.display();
            if (display != null) display.refreshActionCanvas();
        }
    }

    private void requestFrame() {
        var display = surface.display();
        if (!framePending && display != null) {
            framePending = true; display.root().postOnAnimation(frame);
        }
    }

    private void cancelFrame() {
        var display = surface.display();
        if (display != null) display.root().removeCallbacks(frame);
        framePending = false;
    }

    // 只在当前草稿通知中接纳刚完成的画布手势，不接受别的资源或旧采样结果。
    boolean matches(ActionWorkspace.CanvasCommit commit) {
        return commit != null && binding != null && model.manual()
                && binding.project() == workspace.projectGeneration() && binding.draft() == commit.before()
                && Objects.equals(commit.context(), workspace.actions().context())
                && commit.index() == workspace.actions().selected() && commit.original() == model.playback()
                && canvasBase == commit.original() && canvasFrame == commit.updated();
    }

    boolean replace(ActionWorkspace.CanvasCommit commit, int calls) {
        if (!model.replace(commit.original(), commit.updated(), calls)) return false;
        binding = currentBinding();
        canvasBase = canvasFrame = null;
        sampledPlayback = model.playback(); sampledPosition = model.position();
        return true;
    }

    void reset() {
        cancelFrame();
        canvasBase = canvasFrame = null;
        sampledPlayback = null;
        clear();
    }

    void releaseListeners() { changed = () -> {}; observers.clear(); }
}
