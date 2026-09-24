package top.rookiestwo.maimai_dialogue_editor.client.preview;


import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.Image;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.audio.TypewriterSound;
import top.rookiestwo.maimai_dialogue.client.controller.*;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.client.session.*;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueImageSource;
import top.rookiestwo.maimai_dialogue.dialogue.branch.DialogueOption;
import top.rookiestwo.maimai_dialogue_editor.client.EditorDialogueAudio;
import top.rookiestwo.maimai_dialogue_editor.client.EditorContentPreparation;
import top.rookiestwo.maimai_dialogue_editor.preview.*;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import java.util.*;

/** UI-thread lifecycle of a standalone action preview. All asynchronous completions are request-scoped. */
public final class EditorActionPreview {
    private final PreviewMount mount;
    private final top.rookiestwo.maimai_dialogue_editor.client.EditorPreviewAssets assets;
    private final EditorTimelinePreview timeline;
    private final EditorAudioAudition audition;
    private final Runnable changed;
    private DialogueImageSource mountedImages;
    private top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey mountedDocument;
    private final ProjectWorkspace workspace;
    private final ActionPreviewSession session;
    private ActionPreviewSession.Request request;
    private ActionPreviewSession.Prepared preparing, displayed;
    private DialogueImageSource pendingImages;
    private EditorReadyImages images;
    private EditorDialogueAudio audio;
    private Playback actions;
    private long revision, generation;
    private boolean playRequested, playing;
    private Integer restorePosition;
    private String failure = "";

    EditorActionPreview(ProjectWorkspace workspace, top.rookiestwo.maimai_dialogue_editor.client.EditorPreviewAssets assets,
                        EditorTimelinePreview timeline, EditorAudioAudition audition, PreviewMount mount, Runnable changed) {
        this.workspace = workspace; this.assets = assets; this.timeline = timeline;
        this.audition = audition; this.mount = mount; this.changed = changed;
        session = new ActionPreviewSession(request -> EditorContentPreparation.prepare(workspace,
                external -> ActionPreviewSession.prepare(request, external)), task -> Core.getUiHandler().post(task), this::prepared);
    }
    private boolean selected() {
        var key = workspace.resources().opened();
        return key != null && key.kind() == top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind.ACTION;
    }
    private boolean ready() { return mount.ready() && selected(); }
    void forgetDisplay() { mountedImages = null; mountedDocument = null; }
    private void clearView() { if (mount.active() && mountedDocument != null) mount.clear(); }
    private void show(DialogueScreenState state, DialogueUiActions actions, DialogueImageSource images) {
        if (!ready()) return;
        // 只在场景或图片 scope 改变时重建 Fragment；重播保持原挂载。
        if (mount.fragment() == null || mountedImages != images) {
            mount.clear();
            mount.show(actions, images.fork(), "editor-action-preview");
            mountedImages = images; mountedDocument = workspace.resources().opened();
        } else mount.fragment().render(state);
    }
    public boolean canPlay() { return selected() && workspace.actions().active(); }
    public boolean playing() { return playing || playRequested; }
    boolean canSeek() { return canPlay() && displayed != null && displayed == session.prepared() && pendingImages == null; }
    boolean acceptCanvasCommit(ProjectDraft before, top.rookiestwo.maimai_dialogue.presentation.action.SceneAction action) {
        if (!canSeek() || playing || playRequested || request == null || request.draft() != before) return false;
        var next = new ActionPreviewSession.Request(workspace.projectGeneration(), workspace.draft(),
                workspace.resources().opened(), workspace.actions().previewContext());
        if (!session.replaceAction(request, next, action)) return false;
        request = next; preparing = displayed = session.prepared(); restorePosition = null;
        return true;
    }
    void pauseForSeek() {
        boolean wasPlaying = playing || playRequested || audio != null;
        closeAudio(); playRequested = playing = false;
        if (wasPlaying) changed.run();
    }
    public String error() { return failure.isEmpty() ? session.error() : failure; }
    public List<String> targets() {
        var scene = session.prepared();
        return scene == null ? List.of("dialogue") : new ActionSceneContext(scene.scene().scene()).targets();
    }
    void synchronize() {
        var model = workspace.actions();
        var next = !selected() || workspace.draft() == null ? null
                : new ActionPreviewSession.Request(workspace.projectGeneration(), workspace.draft(), workspace.resources().opened(), model.previewContext());
        if (Objects.equals(next, request)) { prepared(); return; }
        boolean sameDocument = next != null && request != null && next.project() == request.project() && next.key().equals(request.key());
        restorePosition = sameDocument && next.context().equals(request.context()) ? timeline.model().position() : null;
        cancelPending(); closeAudio(); playRequested = playing = false;
        if (sameDocument && displayed != null) timeline.freezeFrame();
        else { actions = null; displayed = null; closeImages(); clearView(); timeline.clear(this); }
        request = next; preparing = null; failure = "";
        session.select(next);
    }
    private void prepared() {
        var next = session.prepared();
        if (request == null || !ready()) return;
        if (next == null) { playRequested = false; changed.run(); return; }
        if (preparing == next) return;
        preparing = next; cancelPending(); long expected = revision;
        pendingImages = assets.openImages(next.scene().images());
        var source = pendingImages;
        var ids = ScenePreviewSession.initialImageIds(next.scene().scene());
        // Only the initial frame and the selected action's destination variant need image handles.
        if (next.action() != null && next.action().variant().isPresent()) {
            String target = request.context().target(); String variant = next.action().variant().orElseThrow().variant();
            if (target.equals("background")) next.scene().scene().background().ifPresent(bg -> {
                var id = bg.variants().get(variant); if (id != null) ids.add(id);
            });
            else {
                var object = next.scene().scene().visualObjects().get(target);
                if (object != null && object.variants().containsKey(variant)) ids.add(object.variants().get(variant));
            }
        }
        Map<ResourceLocation, Image> loaded = new LinkedHashMap<>();
        int[] remaining = {ids.size()}; boolean[] failed = {false};
        if (ids.isEmpty()) publish(next, loaded, source);
        for (var id : ids) source.load(id, image -> {
            if (expected != revision) return;
            if (image == null) { failed[0] = true; failure = net.minecraft.client.resources.language.I18n.get("gui.maimai_dialogue_editor.scene.missing_image") + " " + id; }
            else loaded.put(id, image);
            if (--remaining[0] == 0) {
                if (!failed[0]) publish(next, loaded, source);
                else { source.close(); pendingImages = null; playRequested = false; changed.run(); }
            }
        });
    }
    private void publish(ActionPreviewSession.Prepared next, Map<ResourceLocation, Image> loaded, DialogueImageSource source) {
        if (!ready()) {
            // Keep the prepared data retryable when attachment/resume happens after the image callbacks.
            preparing = null;
            source.close(); pendingImages = null;
            return;
        }
        boolean play = playRequested;
        try {
            var ready = new EditorReadyImages(loaded); closeImages(); images = ready; displayed = next;
            playRequested = false; render(play);
        } catch (RuntimeException invalid) { failure = String.valueOf(invalid.getMessage()); playRequested = false; }
        finally { source.close(); pendingImages = null; }
        if (!play && restorePosition != null) timeline.seek(timeline.playback(), restorePosition);
        restorePosition = null;
        changed.run();
    }
    public void play() {
        if (!canPlay()) return;
        workspace.actions().endGesture(true); workspace.endEdit(); audition.stop();
        closeAudio(); failure = "";
        if (displayed == session.prepared() && displayed != null && pendingImages == null) render(true);
        else if (session.error().isEmpty()) playRequested = true;
        changed.run();
    }
    public void stop() {
        playRequested = false; closeAudio(); playing = false;
        if (displayed != null) render(false);
        changed.run();
    }
    private void render(boolean play) {
        if (displayed == null || images == null || !ready()) return;
        closeAudio(); playing = false;
        long token = ++generation;
        ScenePlayback playback;
        try { playback = play ? displayed.playback(request.context().target(), token) : displayed.initial(token); }
        catch (RuntimeException invalid) { failure = String.valueOf(invalid.getMessage()); return; }
        ScenePlayback timelinePlayback = null;
        try { timelinePlayback = play ? playback : displayed.playback(request.context().target(), token); }
        catch (RuntimeException invalid) { failure = String.valueOf(invalid.getMessage()); }
        timeline.bind(this, timelinePlayback, 1, play);
        var state = new DialogueScreenState(token, Optional.of(displayed.scene().scene()), Optional.of(displayed.scene().theme()),
                Optional.of(playback), PlaybackPhase.READY, !play, Optional.empty(), false, false, 0,
                Optional.of(net.minecraft.client.resources.language.I18n.get("gui.maimai_dialogue_editor.scene.preview_speaker")), Optional.of(net.minecraft.client.resources.language.I18n.get("gui.maimai_dialogue_editor.scene.preview_text")),
                Optional.empty(), List.of(), List.of(), false, false, TypewriterSound.SILENT);
        if (actions == null) actions = new Playback(state); else actions.state = state;
        playing = play;
        if (play) {
            long expected = revision;
            audio = new EditorDialogueAudio(displayed.scene().images(), task -> workspace.prepare(() -> { task.run(); return null; }),
                    detail -> Core.getUiHandler().post(() -> {
                        if (expected == revision && generation == token) { failure = detail; changed.run(); }
                    }), () -> {});
            audio.render(state, List.of());
        }
        show(state, actions, images);
    }
    private void cancelPending() { ++revision; if (pendingImages != null) pendingImages.close(); pendingImages = null; }
    private void closeAudio() { if (audio != null) audio.close(); audio = null; }
    private void closeImages() { if (images != null) images.close(); images = null; }
    void release() {
        cancelPending(); closeAudio(); closeImages(); actions = null; request = null; preparing = displayed = null;
        playRequested = playing = false; session.select(null); failure = "";
        restorePosition = null;
        timeline.clear(this);
    }
    void dispose() { release(); session.dispose(); }

    private final class Playback implements DialogueUiActions {
        private DialogueScreenState state;
        Playback(DialogueScreenState state) { this.state = state; }
        public DialogueScreenState viewState() { return state; }
        public void advance() {}
        public void skipToEnd() {}
        public void selectOption(DialogueOption option) {}
        public void completePlayback(long generation, long token) {}
        public void completeTextPlayback(long generation, long token) {}
        public void closeFromUi() {}
        public void onScreenDestroyed(DialogueScreenHandle screen) {}
        public void audioFrame(long generation, long token, int elapsed) {
            Core.getUiHandler().post(() -> {
                if (actions == this && playing && audio != null && state.generation() == generation)
                {
                    timeline.follow(EditorActionPreview.this, token, elapsed);
                    audio.frame(generation, token, elapsed);
                }
            });
        }
    }
}
