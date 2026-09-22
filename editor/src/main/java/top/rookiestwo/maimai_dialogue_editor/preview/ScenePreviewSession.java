package top.rookiestwo.maimai_dialogue_editor.preview;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.resource.ClientContentSnapshot;
import top.rookiestwo.maimai_dialogue.content.resolve.SceneResolver;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition;
import top.rookiestwo.maimai_dialogue_editor.content.ProjectContentSnapshot;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialSnapshot;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.util.*;
import java.util.concurrent.*;

/** UI-thread state with serialized background preparation. Obsolete results never replace a newer Scene. */
public final class ScenePreviewSession {
    public record Prepared(SceneDefinition scene, MaterialSnapshot images, ThemeDefinition theme) {
        public Prepared(SceneDefinition scene, MaterialSnapshot images) { this(scene, images, ThemeDefinition.DEFAULT); }
    }
    private record Request(long project, ProjectDraft draft, ResourceKey key) {}
    @FunctionalInterface public interface Backend { CompletableFuture<Prepared> prepare(ProjectDraft draft, ResourceKey key); }
    private final Backend backend;
    private final Executor ui;
    private final Runnable changed;
    private Request requested, pending, published;
    private Prepared prepared;
    private String error = "";
    private boolean disposed;

    public ScenePreviewSession(Backend backend, Executor ui, Runnable changed) {
        this.backend = backend; this.ui = ui; this.changed = changed;
    }
    public Prepared prepared() { return prepared; }
    public String error() { return error; }
    public boolean current() { return !disposed && prepared != null && published == requested && error.isEmpty(); }
    public void select(long project, ProjectDraft draft, ResourceKey key) {
        if (disposed) return;
        Request next = draft == null || key == null || key.kind() != ResourceKind.SCENE
                ? null : new Request(project, draft, key);
        if (requested == null && next == null || requested != null && next != null && requested.project == next.project
                && requested.draft == next.draft && requested.key.equals(next.key)) return;
        boolean sameScene = requested != null && next != null && requested.project == next.project && requested.key.equals(next.key);
        requested = next;
        if (!sameScene) { prepared = null; published = null; error = ""; }
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
                if (failure == null) { prepared = result; published = request; error = ""; }
                else {
                    Throwable cause = failure;
                    while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                    error = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                }
                changed.run();
            } else start();
        }));
    }
    public void dispose() { disposed = true; requested = null; pending = null; published = null; prepared = null; error = ""; }

    /** Runs entirely on the IO executor, using the same codecs and VisualAsset resolution as gameplay. */
    public static Prepared prepare(ProjectDraft draft, ResourceKey key, ClientContentSnapshot external) throws java.io.IOException {
        if (key.kind() != ResourceKind.SCENE) throw new IllegalArgumentException("Expected a Scene");
        var id = ResourceLocation.fromNamespaceAndPath(draft.namespace(), key.path());
        return prepare(draft, id, external);
    }
    public static Prepared prepare(ProjectDraft draft, ResourceLocation id, ClientContentSnapshot external) throws java.io.IOException {
        var content = new ProjectContentSnapshot(draft, external).prepare(ResourceKind.SCENE, id);
        var source = content.scene(id).orElseThrow(() -> new IllegalArgumentException("Missing Scene: " + id));
        var resolved = SceneResolver.resolve(source, content::theme, content::visualAsset);
        var errors = new ArrayList<>(resolved.visualErrors());
        if (resolved.missingTheme()) errors.add("Missing Theme: " + resolved.source().theme());
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        var scene = resolved.scene();
        var images = MaterialSnapshot.prepare(draft);
        for (ResourceLocation image : imageIds(scene)) {
            if (images.owns(image) && !images.images().containsKey(image)) throw new IllegalArgumentException("Missing image: " + image);
        }
        return new Prepared(scene, images, resolved.theme());
    }
    public static Set<ResourceLocation> imageIds(SceneDefinition scene) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        scene.background().ifPresent(background -> ids.addAll(background.variants().values()));
        scene.visualObjects().values().forEach(object -> ids.addAll(object.variants().values()));
        return ids;
    }

    /** Static authoring only needs the initial variants; other variants stay lazily loaded. */
    public static Set<ResourceLocation> initialImageIds(SceneDefinition scene) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        scene.background().ifPresent(background -> ids.add(background.initialImage()));
        scene.visualObjects().values().forEach(object -> ids.add(object.initialImage()));
        return ids;
    }
}
