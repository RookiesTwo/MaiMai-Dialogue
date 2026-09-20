package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.fragment.FragmentManager;
import icyllis.modernui.view.View;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.bootstrap.ClientServices;
import top.rookiestwo.maimai_dialogue.client.config.ClientConfig;
import top.rookiestwo.maimai_dialogue.client.controller.DialogueScreenHandle;
import top.rookiestwo.maimai_dialogue.client.controller.DialogueUiActions;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;
import top.rookiestwo.maimai_dialogue.client.ui.screen.DialogueFragment;
import top.rookiestwo.maimai_dialogue.dialogue.branch.DialogueOption;
import top.rookiestwo.maimai_dialogue_editor.content.ProjectContentSnapshot;
import top.rookiestwo.maimai_dialogue_editor.client.EditorPreviewAssets;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialSnapshot;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueImageSource;
import top.rookiestwo.maimai_dialogue_editor.preview.EditorPreviewSession;
import top.rookiestwo.maimai_dialogue_editor.preview.AudioPreviewSession;
import top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewSession;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceTree;

import java.util.Objects;
import java.util.function.Consumer;

/** UI-thread owner of one embedded runtime Fragment. Client callbacks cross back through the UI handler. */
final class EditorPreviewHost {
    enum Mode { DIALOGUE, IMAGE, SOUND, SCENE, EMPTY }
    private final Fragment owner;
    private final ProjectWorkspace workspace;
    private final EditorPreviewAssets assets;
    private final AudioPreviewSession audio;
    private final ScenePreviewSession scenes;
    private final int containerId = View.generateViewId();
    private EditorPreviewView view;
    private EditorPreviewSession playback;
    private DialogueFragment fragment;
    private boolean showingIdle;
    private ProjectDraft source;
    private ResourceKey dialogue;
    private long revision;
    private boolean loading;
    private boolean advanceAfterLoad;
    private boolean disposed;
    private String message = "preview.idle";
    private String error = "";
    private Runnable changed = () -> {};
    private ProjectDraft observedDraft;
    private ResourceKey observedDocument;
    private ResourceTree.Node observedSelection;
    private long observedSelectionRevision = -1;
    private boolean publishingPosition;

    EditorPreviewHost(Fragment owner, ProjectWorkspace workspace, EditorPreviewAssets assets, AudioPreviewSession.Backend audioBackend) {
        this.owner = owner;
        this.workspace = workspace;
        this.assets = assets;
        audio = new AudioPreviewSession(audioBackend, this::refresh);
        scenes = new ScenePreviewSession((draft, key) -> {
            var future = new java.util.concurrent.CompletableFuture<ScenePreviewSession.Prepared>();
            Minecraft.getInstance().execute(() -> {
                try {
                    var external = ClientServices.get().content().current();
                    workspace.prepare(() -> {
                        try { return ScenePreviewSession.prepare(draft, key, external); }
                        catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
                    }).whenComplete((result, failure) -> {
                        if (failure == null) future.complete(result); else future.completeExceptionally(failure);
                    });
                } catch (RuntimeException failure) {
                    // The editor IO executor may already be shut down while this client callback was queued.
                    future.completeExceptionally(failure);
                }
            });
            return future;
        }, task -> Core.getUiHandler().post(task), this::refresh);
    }

    EditorPreviewView createView(Context context) {
        view = new EditorPreviewView(context, this, containerId);
        return view;
    }

    void setListener(Runnable listener) { changed = Objects.requireNonNull(listener); }

    void setReferenceHeight(int height) {
        if (!disposed && view != null) view.setReferenceHeight(height);
    }

    void finishViewportResize() {
        if (!disposed && view != null) view.refreshContentAfterLayout();
    }

    void refreshViewport(EditorPreviewView sourceView) {
        if (!disposed && view == sourceView && fragment != null) fragment.refreshViewport();
    }

    boolean canStart() {
        return canOperate();
    }

    private boolean canOperate() {
        ResourceKey opened = workspace.resources().opened();
        return !disposed && !workspace.busy() && workspace.page() == ProjectWorkspace.Page.NONE
                && workspace.resources().form() == top.rookiestwo.maimai_dialogue_editor.resource.ResourceWorkspace.Form.NONE
                && workspace.draft() != null && opened != null && opened.kind() == ResourceKind.DIALOGUE;
    }

    boolean running() { return playback != null && playback.running(); }
    boolean loading() { return loading; }
    String message() { return message; }
    String error() { return error; }
    record ImagePreview(String namespace, String path, boolean linear, long revision) {}
    Mode mode() {
        ResourceKey key = workspace.resources().opened();
        if (key == null) return Mode.EMPTY;
        return switch (key.kind()) {
            case DIALOGUE -> Mode.DIALOGUE;
            case IMAGE, VISUAL_ASSET -> Mode.IMAGE;
            case SOUND -> Mode.SOUND;
            case SCENE -> Mode.SCENE;
            default -> Mode.EMPTY;
        };
    }
    AudioPreviewSession audio() { return audio; }
    ScenePreviewSession scenes() { return scenes; }
    EditorPreviewAssets assets() { return assets; }
    boolean viewingMaterial() {
        ResourceKey key = workspace.resources().opened();
        return key != null && (key.kind() == ResourceKind.IMAGE || key.kind() == ResourceKind.VISUAL_ASSET);
    }
    ImagePreview imagePreview() {
        if (!viewingMaterial() || workspace.materials().previewNamespace().isEmpty()
                || workspace.draft() == null || !workspace.materials().previewNamespace().equals(workspace.draft().namespace())) return null;
        var state = workspace.content().snapshot();
        if (state.key() == null || !state.key().equals(workspace.resources().opened())) return null;
        String id;
        boolean linear = true;
        if (state.key().kind() == ResourceKind.IMAGE) id = state.key().id(workspace.draft().namespace()) + ".png";
        else {
            if (state.data() == null || !(state.data().get("variants") instanceof com.google.gson.JsonObject variants)) return null;
            id = top.rookiestwo.maimai_dialogue_editor.material.MaterialPack.string(variants.get(
                    workspace.materials().variant(state.key(), state.data())));
            linear = !"nearest".equals(top.rookiestwo.maimai_dialogue_editor.material.MaterialPack.string(state.data().get("sampling")));
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return null;
        return new ImagePreview(location.getNamespace(), location.getPath(), linear, workspace.materials().loadedRevision());
    }
    DialogueImageSource openImages() { return assets.openImages(); }

    void synchronize() {
        // Undo/redo may restore a different document cursor before the properties View is rebound.
        var document = workspace.content().snapshot();
        record SoundSelection(long project, ResourceKey key) {}
        if (mode() == Mode.SOUND && document.key() != null && document.key().equals(workspace.resources().opened())
                && document.data() != null && workspace.draft() != null) {
            String blob = top.rookiestwo.maimai_dialogue_editor.material.MaterialPack.string(document.data().get("blob"));
            audio.select(new SoundSelection(workspace.projectGeneration(), document.key()), workspace.draft().blob(blob));
        } else audio.select(null, null);
        var resources = workspace.resources();
        ProjectDraft draft = workspace.draft();
        ResourceKey opened = resources.opened();
        scenes.select(workspace.projectGeneration(), draft, opened);
        ResourceTree.Node selected = resources.selection();
        boolean draftChanged = draft != observedDraft;
        boolean selectionChanged = observedSelectionRevision != resources.selectionRevision()
                || !Objects.equals(observedSelection, selected) || !Objects.equals(observedDocument, opened);
        observedDraft = draft;
        observedDocument = opened;
        observedSelection = selected;
        observedSelectionRevision = resources.selectionRevision();
        if (publishingPosition) {
            if (view != null) view.refresh();
            return;
        }
        // Draft edits stop playback. Browsing a Step is a separate, explicit seek request.
        if (!draftChanged && selectionChanged && selected.isStep() && Objects.equals(selected.owner(), opened)) {
            startAt(selected.stepIndex());
        } else {
            if (source != null && (source != draft || !Objects.equals(dialogue, opened))) stop();
            if (!draftChanged && selectionChanged && selected.kind() == ResourceKind.DIALOGUE
                    && selected.type() == ResourceTree.Type.RESOURCE) stop();
        }
        if (view != null) view.refresh();
    }

    void start() {
        startAt(0);
    }

    private void startAt(int step) {
        // A new tree selection may supersede a pending start before the client snapshot arrives.
        if (!canStart() || view == null || !view.isAttachedToWindow()) return;
        workspace.endEdit();
        // Keep the displayed session and controls until the replacement is ready.
        // Its callbacks are suspended while loading, then discarded by session identity.
        source = workspace.draft();
        dialogue = workspace.resources().opened();
        ProjectDraft captured = source;
        ResourceKey capturedKey = dialogue;
        long expected = ++revision;
        loading = true;
        advanceAfterLoad = false;
        refresh();
        // Read the loaded resource snapshot on the client thread; never replace the global repository.
        Minecraft.getInstance().execute(() -> {
            var external = ClientServices.get().content().current();
            int interval = ClientConfig.get().defaultTypewriterIntervalMs();
            record Prepared(ProjectContentSnapshot content, MaterialSnapshot assets) {}
            workspace.prepare(() -> {
                try {
                    return new Prepared(new ProjectContentSnapshot(captured, external).prepare(
                            ResourceLocation.fromNamespaceAndPath(captured.namespace(), capturedKey.path())),
                            MaterialSnapshot.prepare(captured));
                } catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
            })
                    .whenComplete((content, preparationFailure) -> Core.getUiHandler().post(() -> {
                if (disposed || expected != revision || view == null) return;
                loading = false;
                if (captured != workspace.draft() || !Objects.equals(capturedKey, workspace.resources().opened())) {
                    stop();
                    return;
                }
                try {
                    if (preparationFailure != null) throw new java.util.concurrent.CompletionException(preparationFailure);
                    var prepared = new EditorPreviewSession(content.content(),
                            ResourceLocation.fromNamespaceAndPath(captured.namespace(), capturedKey.path()), interval, step);
                    if (playback != null) playback.stop();
                    playback = prepared;
                    if (advanceAfterLoad) playback.advance();
                    advanceAfterLoad = false;
                    if (running()) {
                        showingIdle = false;
                        fragment = new DialogueFragment(new PreviewActions(playback), DialogueFragment.CornerControls.DISPLAY_ONLY,
                                assets.openImages(content.assets()));
                        owner.getChildFragmentManager().beginTransaction().replace(containerId, fragment, "editor-preview").commitNow();
                    }
                    render();
                } catch (RuntimeException failure) {
                    advanceAfterLoad = false;
                    if (playback != null) playback.stop();
                    playback = null;
                    showIdleControls();
                    Throwable cause = failure;
                    while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null)
                        cause = cause.getCause();
                    error = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                    message = "preview.failed";
                    refresh();
                }
            }));
        });
    }

    void advance() {
        if (!canStart()) return;
        if (loading) {
            // Apply a click to the requested Step once ready, never to the old displayed session.
            advanceAfterLoad = true;
            return;
        }
        if (!running()) {
            var selected = workspace.resources().selection();
            startAt(selected.isStep() && selected.owner().equals(workspace.resources().opened()) ? selected.stepIndex() : 0);
            return;
        }
        playback.advance();
        render();
    }

    void stop() {
        reset();
        showIdleControls();
        refresh();
    }

    private void showIdleControls() {
        if (disposed || view == null || !view.isAttachedToWindow()) return;
        if (mode() != Mode.DIALOGUE) { clearFragments(); return; }
        FragmentManager manager = owner.getChildFragmentManager();
        if (manager.isDestroyed() || manager.isStateSaved()) return;
        if (showingIdle && fragment != null) return;
        clearFragments();
        fragment = new DialogueFragment(new PreviewActions(null), DialogueFragment.CornerControls.DISPLAY_ONLY);
        manager.beginTransaction().replace(containerId, fragment, "editor-preview").commitNow();
        showingIdle = true;
    }

    private void reset() {
        ++revision;
        loading = false;
        advanceAfterLoad = false;
        if (playback != null) playback.stop();
        playback = null;
        source = null;
        dialogue = null;
        error = "";
        message = "preview.idle";
    }

    private void clearFragments() {
        fragment = null;
        showingIdle = false;
        FragmentManager manager = owner.getChildFragmentManager();
        if (manager.isDestroyed() || manager.isStateSaved()) return;
        // The embedded history page uses the same child manager and must be released with playback.
        manager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        var children = manager.getFragments();
        if (!children.isEmpty()) {
            var transaction = manager.beginTransaction();
            children.forEach(transaction::remove);
            transaction.commitNow();
        }
    }

    private void render() {
        if (playback == null) return;
        followPosition();
        message = switch (playback.status()) {
            case RUNNING -> "preview.running";
            case FINISHED -> "preview.finished";
            case STOPPED -> "preview.idle";
            case FAILED -> "preview.failed";
        };
        error = playback.error();
        if (running() && fragment != null) fragment.render(playback.state());
        else {
            // Drop decoded definitions, history and simulated commands as soon as playback ends.
            playback = null;
            showIdleControls();
        }
        refresh();
    }

    private void followPosition() {
        var position = playback.position();
        if (position == null || source != workspace.draft() || !position.dialogueId().getNamespace().equals(source.namespace())) return;
        ResourceKey key = new ResourceKey(ResourceKind.DIALOGUE, position.dialogueId().getPath());
        if (!workspace.resources().catalog().contains(key)) return;
        dialogue = key;
        publishingPosition = true;
        try {
            workspace.followPreviewStep(key, position.end() ? -1 : position.stepIndex());
        } finally {
            publishingPosition = false;
        }
    }

    private void refresh() {
        if (view != null) view.refresh();
        changed.run();
    }

    void releaseView() {
        audio.stop();
        scenes.select(workspace.projectGeneration(), null, null);
        // FragmentManager destroys child Views before the parent callback; do not start nested transactions here.
        reset();
        fragment = null;
        showingIdle = false;
        view = null;
        changed = () -> {};
    }

    void onViewReady() {
        if (view != null) view.post(() -> {
            if (view != null && view.isAttachedToWindow()) synchronize();
            if (view != null && view.isAttachedToWindow() && !loading && !running()) {
                showIdleControls();
            }
        });
    }

    void dispose() {
        disposed = true;
        scenes.dispose();
        releaseView();
    }

    private final class PreviewActions implements DialogueUiActions {
        private final EditorPreviewSession session;
        PreviewActions(EditorPreviewSession session) { this.session = session; }

        @Override public DialogueScreenState viewState() {
            return session == null ? DialogueScreenState.empty(0) : session.state();
        }

        private void dispatch(Consumer<EditorPreviewSession> action) {
            if (session == null) return;
            Core.getUiHandler().post(() -> {
                // Old animation, text and destruction callbacks must never operate on a restarted preview.
                if (disposed || loading || playback != session || !session.running()) return;
                action.accept(session);
                render();
            });
        }

        @Override public void advance() { dispatch(EditorPreviewSession::advance); }
        @Override public void skipToEnd() { /* The preview's skip icon is decorative. */ }
        @Override public void selectOption(DialogueOption option) { dispatch(current -> current.selectOption(option)); }
        @Override public void completePlayback(long generation, long token) {
            dispatch(current -> current.completeScene(generation, token));
        }
        @Override public void completeTextPlayback(long generation, long token) {
            dispatch(current -> current.completeText(generation, token));
        }
        @Override public void closeFromUi() { dispatch(EditorPreviewSession::stop); }
        @Override public void onScreenDestroyed(DialogueScreenHandle screen) {
            dispatch(current -> { if (fragment == screen) current.stop(); });
        }
    }
}
