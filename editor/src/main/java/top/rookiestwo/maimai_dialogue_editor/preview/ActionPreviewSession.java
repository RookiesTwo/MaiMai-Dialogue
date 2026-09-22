package top.rookiestwo.maimai_dialogue_editor.preview;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.resource.ClientContentSnapshot;
import top.rookiestwo.maimai_dialogue.client.scene.*;
import top.rookiestwo.maimai_dialogue.presentation.action.*;
import top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition;
import top.rookiestwo.maimai_dialogue_editor.document.ActionFields;
import top.rookiestwo.maimai_dialogue_editor.document.ActionWorkspace.PreviewContext;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialSnapshot;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Serialized preparation and isolated playback for one standalone Action, with no Dialogue session. */
public final class ActionPreviewSession {
    public record Request(long project, ProjectDraft draft, ResourceKey key, PreviewContext context) {}
    public record Prepared(ScenePreviewSession.Prepared scene, SceneAction action, String error) {
        public ScenePlayback playback(String target, long token) {
            if (action == null) throw new IllegalArgumentException(error);
            var result = new SceneRuntime(scene.scene(), 1, token).prepare(List.of(new SceneActionCall(
                    action.audioOnly() ? "" : target, 0, new ActionSpec.Inline(action))));
            if (!result.errors().isEmpty()) throw new IllegalArgumentException(String.join("\n", result.errors()));
            return result.playback();
        }
        public ScenePlayback initial(long token) {
            var state = SceneState.initial(scene.scene());
            return new ScenePlayback(token, state, state, List.of(), 0, 0);
        }
    }
    @FunctionalInterface public interface Backend { CompletableFuture<Prepared> prepare(Request request); }
    private final Backend backend;
    private final Executor ui;
    private final Runnable changed;
    private Request requested, pending;
    private Prepared prepared;
    private String error = "";
    private boolean disposed;
    public ActionPreviewSession(Backend backend, Executor ui, Runnable changed) {
        this.backend = backend; this.ui = ui; this.changed = changed;
    }
    public Prepared prepared() { return prepared; }
    public String error() { return error; }
    public void select(Request next) {
        if (disposed || Objects.equals(requested, next)) return;
        requested = next; prepared = null; error = "";
        if (pending == null) start();
    }
    private void start() {
        if (disposed || requested == null) return;
        Request request = requested; pending = request;
        CompletableFuture<Prepared> future;
        try { future = backend.prepare(request); }
        catch (RuntimeException failure) { future = CompletableFuture.failedFuture(failure); }
        future.whenComplete((value, failure) -> ui.execute(() -> {
            if (disposed) return;
            pending = null;
            if (request == requested) {
                if (failure == null) { prepared = value; error = value.error(); }
                else {
                    Throwable cause = failure;
                    while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                    error = String.valueOf(cause.getMessage());
                }
                changed.run();
            } else start();
        }));
    }
    public void dispose() { disposed = true; requested = pending = null; prepared = null; }

    public static Prepared prepare(Request request, ClientContentSnapshot external) throws IOException {
        var draft = request.draft();
        ScenePreviewSession.Prepared scene = request.context().scene().isBlank()
                ? new ScenePreviewSession.Prepared(SceneDefinition.DEFAULT, MaterialSnapshot.prepare(draft))
                : ScenePreviewSession.prepare(draft, ResourceLocation.parse(request.context().scene()), external);
        draft.load(request.key());
        var data = draft.resource(request.key());
        var errors = ActionFields.errors(data);
        if (!errors.isEmpty()) return new Prepared(scene, null, String.join("\n", errors.values()));
        try { return new Prepared(scene, SceneAction.CODEC.parse(JsonOps.INSTANCE, data).getOrThrow(), ""); }
        catch (RuntimeException invalid) { return new Prepared(scene, null, String.valueOf(invalid.getMessage())); }
    }
}
