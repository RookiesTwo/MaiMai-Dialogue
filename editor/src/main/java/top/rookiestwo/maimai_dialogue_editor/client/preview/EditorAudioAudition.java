package top.rookiestwo.maimai_dialogue_editor.client.preview;

import icyllis.modernui.core.Core;
import top.rookiestwo.maimai_dialogue.audio.BgmOperation;
import top.rookiestwo.maimai_dialogue.audio.TypewriterSound;
import top.rookiestwo.maimai_dialogue_editor.client.EditorDialogueAudio;
import top.rookiestwo.maimai_dialogue_editor.document.AudioWorkspace;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialSnapshot;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletionException;

// 属性面板试听的请求与音频 scope；切换目标、停止和释放都让旧回调失效。
public final class EditorAudioAudition {
    private final ProjectWorkspace workspace;
    private final Runnable beforePlay, refresh;
    private Runnable changed = () -> {};
    private EditorDialogueAudio audio;
    private long revision;
    private boolean loading, disposed;
    private ProjectDraft draft;
    private AudioWorkspace.Target target;
    private String error = "";

    EditorAudioAudition(ProjectWorkspace workspace, Runnable beforePlay, Runnable refresh) {
        this.workspace = workspace; this.beforePlay = beforePlay; this.refresh = refresh;
    }

    public void setListener(Runnable listener) { changed = listener; }
    public boolean active() { return loading || audio != null && audio.active(); }
    public String error() { return error; }
    void notifyChanged() { changed.run(); }

    public void play() {
        var model = workspace.audio();
        if (!model.active()) return;
        workspace.endEdit();
        stop();
        BgmOperation bgm;
        TypewriterSound typing;
        try {
            bgm = model.target().bgm() ? model.bgm().orElse(null) : null;
            typing = model.target().bgm() ? null : model.typing();
            if (bgm == null && typing == null || typing != null && !typing.enabled()) return;
        } catch (RuntimeException invalid) { error = String.valueOf(invalid.getMessage()); refresh.run(); return; }
        beforePlay.run();
        target = model.target(); draft = workspace.draft();
        var captured = draft; long expected = ++revision;
        loading = true; error = "";
        workspace.prepare(() -> {
            try { return MaterialSnapshot.prepare(captured); }
            catch (IOException failure) { throw new CompletionException(failure); }
        }).whenComplete((materials, failure) -> Core.getUiHandler().post(() -> {
            if (disposed || expected != revision || captured != workspace.draft()) return;
            loading = false;
            if (failure != null) error = String.valueOf(failure.getMessage());
            else {
                audio = new EditorDialogueAudio(materials, task -> workspace.prepare(() -> { task.run(); return null; }),
                        detail -> Core.getUiHandler().post(() -> {
                            if (disposed || expected != revision) return;
                            error = detail; refresh.run();
                        }), () -> Core.getUiHandler().post(refresh));
                if (bgm != null) audio.audition(bgm); else audio.audition(typing);
            }
            refresh.run();
        }));
        refresh.run();
    }

    public void stop() {
        ++revision; loading = false;
        if (audio != null) audio.close();
        audio = null; draft = null; target = null; error = "";
        changed.run();
    }

    void synchronize() {
        if (draft != null && (draft != workspace.draft() || !Objects.equals(target, workspace.audio().target()))) stop();
    }

    void releaseView() { stop(); changed = () -> {}; }
    void dispose() { disposed = true; releaseView(); }
}
