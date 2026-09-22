package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.audio.TypewriterSound;
import top.rookiestwo.maimai_dialogue.client.bootstrap.ClientServices;
import top.rookiestwo.maimai_dialogue.client.controller.*;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.client.session.*;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueImageSource;
import top.rookiestwo.maimai_dialogue.dialogue.branch.DialogueOption;
import top.rookiestwo.maimai_dialogue_editor.client.EditorDialogueAudio;
import top.rookiestwo.maimai_dialogue_editor.preview.*;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import java.util.*;
import java.util.concurrent.*;

/** UI-thread lifecycle of a standalone action preview. All asynchronous completions are request-scoped. */
final class EditorActionPreview {
    private final EditorPreviewHost host;
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
    private String failure = "";

    EditorActionPreview(EditorPreviewHost host) {
        this.host = host; workspace = host.workspace();
        session = new ActionPreviewSession(request -> {
            var future = new CompletableFuture<ActionPreviewSession.Prepared>();
            Minecraft.getInstance().execute(() -> {
                try {
                    var external = ClientServices.get().content().current();
                    workspace.prepare(() -> {
                        try { return ActionPreviewSession.prepare(request, external); }
                        catch (java.io.IOException failure) { throw new CompletionException(failure); }
                    }).whenComplete((value, failure) -> {
                        if (failure == null) future.complete(value); else future.completeExceptionally(failure);
                    });
                } catch (RuntimeException failure) { future.completeExceptionally(failure); }
            });
            return future;
        }, task -> Core.getUiHandler().post(task), this::prepared);
    }
    boolean canPlay() { return host.mode() == EditorPreviewHost.Mode.ACTION && workspace.actions().active(); }
    boolean playing() { return playing || playRequested; }
    String error() { return failure.isEmpty() ? session.error() : failure; }
    List<String> targets() {
        var scene = session.prepared();
        return scene == null ? List.of("dialogue") : new ActionSceneContext(scene.scene().scene()).targets();
    }
    void synchronize() {
        var model = workspace.actions();
        var next = host.mode() != EditorPreviewHost.Mode.ACTION || workspace.draft() == null ? null
                : new ActionPreviewSession.Request(workspace.projectGeneration(), workspace.draft(), workspace.resources().opened(), model.previewContext());
        if (Objects.equals(next, request)) { prepared(); return; }
        boolean sameDocument = next != null && request != null && next.project() == request.project() && next.key().equals(request.key());
        cancelPending(); closeAudio(); playRequested = playing = false;
        if (sameDocument && displayed != null) render(false); // Halt the old animation while preserving a valid scene during preparation.
        else { actions = null; displayed = null; closeImages(); host.clearActionPreview(); }
        request = next; preparing = null; failure = "";
        session.select(next);
    }
    private void prepared() {
        var next = session.prepared();
        if (request == null || !host.actionViewReady()) return;
        if (next == null) { playRequested = false; host.refresh(); return; }
        if (preparing == next) return;
        preparing = next; cancelPending(); long expected = revision;
        pendingImages = host.assets().openImages(next.scene().images());
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
            if (image == null) { failed[0] = true; failure = EditorWidgets.tr("scene.missing_image") + " " + id; }
            else loaded.put(id, image);
            if (--remaining[0] == 0) {
                if (!failed[0]) publish(next, loaded, source);
                else { source.close(); pendingImages = null; playRequested = false; host.refresh(); }
            }
        });
    }
    private void publish(ActionPreviewSession.Prepared next, Map<ResourceLocation, Image> loaded, DialogueImageSource source) {
        try {
            var ready = new EditorReadyImages(loaded); closeImages(); images = ready; displayed = next;
            boolean play = playRequested; playRequested = false; render(play);
        } catch (RuntimeException invalid) { failure = String.valueOf(invalid.getMessage()); playRequested = false; }
        finally { source.close(); pendingImages = null; }
        host.refresh();
    }
    void play() {
        if (!canPlay()) return;
        workspace.actions().endGesture(true); workspace.endEdit(); host.stopAudition();
        closeAudio(); failure = "";
        if (displayed == session.prepared() && displayed != null && pendingImages == null) render(true);
        else if (session.error().isEmpty()) playRequested = true;
        host.refresh();
    }
    void stop() {
        playRequested = false; closeAudio(); playing = false;
        if (displayed != null) render(false);
        host.refresh();
    }
    private void render(boolean play) {
        if (displayed == null || images == null || !host.actionViewReady()) return;
        closeAudio(); playing = false;
        long token = ++generation;
        ScenePlayback playback;
        try { playback = play ? displayed.playback(request.context().target(), token) : displayed.initial(token); }
        catch (RuntimeException invalid) { failure = String.valueOf(invalid.getMessage()); return; }
        var state = new DialogueScreenState(token, Optional.of(displayed.scene().scene()), Optional.of(displayed.scene().theme()),
                Optional.of(playback), PlaybackPhase.READY, !play, Optional.empty(), false, false, 0,
                Optional.of(EditorWidgets.tr("scene.preview_speaker")), Optional.of(EditorWidgets.tr("scene.preview_text")),
                Optional.empty(), List.of(), List.of(), false, false, TypewriterSound.SILENT);
        if (actions == null) actions = new Playback(state); else actions.state = state;
        playing = play;
        if (play) {
            long expected = revision;
            audio = new EditorDialogueAudio(displayed.scene().images(), task -> workspace.prepare(() -> { task.run(); return null; }),
                    detail -> Core.getUiHandler().post(() -> {
                        if (expected == revision && generation == token) { failure = detail; host.refresh(); }
                    }), () -> {});
            audio.render(state, List.of());
        }
        host.showAction(state, actions, images);
    }
    private void cancelPending() { ++revision; if (pendingImages != null) pendingImages.close(); pendingImages = null; }
    private void closeAudio() { if (audio != null) audio.close(); audio = null; }
    private void closeImages() { if (images != null) images.close(); images = null; }
    void release() {
        cancelPending(); closeAudio(); closeImages(); actions = null; request = null; preparing = displayed = null;
        playRequested = playing = false; session.select(null); failure = "";
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
                    audio.frame(generation, token, elapsed);
            });
        }
    }
}
