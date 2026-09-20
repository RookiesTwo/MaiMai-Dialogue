package top.rookiestwo.maimai_dialogue_editor.preview;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.resource.ClientContentSnapshot;
import top.rookiestwo.maimai_dialogue.content.resolve.VisualAssetResolver;
import top.rookiestwo.maimai_dialogue.presentation.*;
import top.rookiestwo.maimai_dialogue_editor.content.ProjectContentSnapshot;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialSnapshot;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.util.*;
import java.util.concurrent.*;

/** UI-thread state with serialized background preparation. Obsolete results never replace a newer Scene. */
public final class ScenePreviewSession {
    public record Prepared(Presentation presentation, MaterialSnapshot images) {}
    private record Request(long project, ProjectDraft draft, ResourceKey key) {}
    @FunctionalInterface public interface Backend { CompletableFuture<Prepared> prepare(ProjectDraft draft, ResourceKey key); }
    private final Backend backend;
    private final Executor ui;
    private final Runnable changed;
    private Request requested, pending;
    private Prepared prepared;
    private String error = "";
    private boolean disposed;

    public ScenePreviewSession(Backend backend, Executor ui, Runnable changed) {
        this.backend = backend; this.ui = ui; this.changed = changed;
    }
    public Prepared prepared() { return prepared; }
    public String error() { return error; }
    public void select(long project, ProjectDraft draft, ResourceKey key) {
        if (disposed) return;
        Request next = draft == null || key == null || key.kind() != ResourceKind.SCENE ? null : new Request(project, draft, key);
        if (requested == null && next == null || requested != null && next != null && requested.project == next.project
                && requested.draft == next.draft && requested.key.equals(next.key)) return;
        boolean sameScene = requested != null && next != null && requested.project == next.project && requested.key.equals(next.key);
        requested = next;
        if (!sameScene) { prepared = null; error = ""; }
        changed.run();
        if (pending == null) start();
    }
    private void start() {
        if (disposed || requested == null) return;
        Request request = requested; pending = request;
        CompletableFuture<Prepared> future;
        try { future = backend.prepare(request.draft, request.key); }
        catch (RuntimeException failure) { future = CompletableFuture.failedFuture(failure); }
        future.whenComplete((result, failure) -> ui.execute(() -> {
            if (disposed) return;
            pending = null;
            if (request == requested) {
                if (failure == null) { prepared = result; error = ""; }
                else {
                    Throwable cause = failure;
                    while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                    error = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                }
                changed.run();
            } else start();
        }));
    }
    public void dispose() { disposed = true; requested = null; pending = null; prepared = null; error = ""; }

    /** Runs entirely on the IO executor, using the same codecs and VisualAsset resolution as gameplay. */
    public static Prepared prepare(ProjectDraft draft, ResourceKey key, ClientContentSnapshot external) throws java.io.IOException {
        if (key.kind() != ResourceKind.SCENE) throw new IllegalArgumentException("Expected a Scene");
        var id = ResourceLocation.fromNamespaceAndPath(draft.namespace(), key.path());
        var content = new ProjectContentSnapshot(draft, external).prepare(ResourceKind.SCENE, id);
        var scene = content.scene(id).orElseThrow(() -> new IllegalArgumentException("Missing Scene: " + id));
        var presentation = new Presentation(Presentation.DEFAULT_THEME_ID, scene.background(), DialogueBoxLayout.DEFAULT,
                scene.visualObjects(), scene.filter());
        var resolved = VisualAssetResolver.resolve(presentation, content::visualAsset);
        if (!resolved.errors().isEmpty()) throw new IllegalArgumentException(String.join("\n", resolved.errors()));
        var images = MaterialSnapshot.prepare(draft);
        for (ResourceLocation image : imageIds(resolved.presentation())) {
            if (images.owns(image) && !images.images().containsKey(image)) throw new IllegalArgumentException("Missing image: " + image);
        }
        return new Prepared(resolved.presentation(), images);
    }
    public static Set<ResourceLocation> imageIds(Presentation presentation) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        presentation.background().ifPresent(background -> ids.addAll(background.variants().values()));
        presentation.visualObjects().values().forEach(object -> ids.addAll(object.variants().values()));
        return ids;
    }
}
