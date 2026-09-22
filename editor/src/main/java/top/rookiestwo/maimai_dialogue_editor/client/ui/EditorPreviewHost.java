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
import top.rookiestwo.maimai_dialogue_editor.client.EditorDialogueAudio;
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
    enum Mode { DIALOGUE, IMAGE, SOUND, SCENE, THEME, EMPTY }
    private final Fragment owner;
    private final ProjectWorkspace workspace;
    private final EditorPreviewAssets assets;
    private final AudioPreviewSession audio;
    private EditorDialogueAudio dialogueAudio;
    private EditorDialogueAudio audition;
    private long auditionRevision;
    private boolean auditionLoading;
    private ProjectDraft auditionDraft;
    private top.rookiestwo.maimai_dialogue_editor.document.AudioWorkspace.Target auditionTarget;
    private String auditionError = "";
    private Runnable audioChanged = () -> {};
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
    private SceneActions sceneActions;
    private ScenePreviewSession.Prepared displayedScene;
    private ResourceKey sceneDocument;
    private long sceneProject = -1;
    private long sceneGeneration;
    private boolean releasingView;
    private int themeExample;
    private int displayedThemeExample = -1;
    private top.rookiestwo.maimai_dialogue.theme.ThemeDefinition displayedTheme;
    private boolean themeFramePending;
    private final Runnable themeFrame = () -> {
        themeFramePending = false;
        refreshTheme();
    };

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
        releasingView = false;
        view = new EditorPreviewView(context, this, containerId);
        workspace.scenes().setLiveListener(immediate -> { if (view != null) view.requestSceneFrame(immediate); });
        workspace.themes().setLiveListener(this::requestThemeFrame);
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
            case THEME -> Mode.THEME;
            default -> Mode.EMPTY;
        };
    }
    AudioPreviewSession audio() { return audio; }
    void setAudioListener(Runnable listener) { audioChanged = listener; }
    boolean auditioning() { return auditionLoading || audition != null && audition.active(); }
    String auditionError() { return auditionError; }
    private EditorDialogueAudio audioScope(MaterialSnapshot materials, long expected, boolean sample) {
        return new EditorDialogueAudio(materials, task -> workspace.prepare(() -> { task.run(); return null; }),
                failure -> Core.getUiHandler().post(() -> {
                    if (disposed || (sample ? expected != auditionRevision : expected != revision)) return;
                    if (sample) auditionError = failure; else error = failure;
                    refresh();
                }), () -> Core.getUiHandler().post(this::refresh));
    }
    void audition() {
        var model = workspace.audio();
        if (!model.active()) return;
        workspace.endEdit();
        stopAudition();
        top.rookiestwo.maimai_dialogue.audio.BgmOperation bgm;
        top.rookiestwo.maimai_dialogue.audio.TypewriterSound typing;
        try {
            bgm = model.target().bgm() ? model.bgm().orElse(null) : null;
            typing = model.target().bgm() ? null : model.typing();
            if (bgm == null && typing == null || typing != null && !typing.enabled()) return;
        } catch (RuntimeException invalid) { auditionError = String.valueOf(invalid.getMessage()); refresh(); return; }
        if (running() || loading) stop();
        audio.stop();
        auditionTarget = model.target(); auditionDraft = workspace.draft();
        var captured = auditionDraft; long expected = ++auditionRevision;
        auditionLoading = true; auditionError = "";
        workspace.prepare(() -> {
            try { return MaterialSnapshot.prepare(captured); }
            catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
        }).whenComplete((materials, failure) -> Core.getUiHandler().post(() -> {
            if (disposed || expected != auditionRevision || captured != workspace.draft()) return;
            auditionLoading = false;
            if (failure != null) auditionError = String.valueOf(failure.getMessage());
            else {
                audition = audioScope(materials, expected, true);
                if (bgm != null) audition.audition(bgm); else audition.audition(typing);
            }
            refresh();
        }));
        refresh();
    }
    void stopAudition() {
        ++auditionRevision; auditionLoading = false;
        if (audition != null) audition.close();
        audition = null; auditionDraft = null; auditionTarget = null; auditionError = "";
        audioChanged.run();
    }
    private void closeDialogueAudio() {
        if (dialogueAudio != null) dialogueAudio.close();
        dialogueAudio = null;
    }
    ScenePreviewSession scenes() { return scenes; }
    EditorPreviewAssets assets() { return assets; }
    ProjectWorkspace workspace() { return workspace; }
    void finishSceneDrag(boolean commit) { if (view != null) view.finishSceneDrag(commit); }
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
        if (auditionDraft != null && (auditionDraft != workspace.draft()
                || !Objects.equals(auditionTarget, workspace.audio().target()))) stopAudition();
        if (mode() != Mode.THEME && sceneDocument != null && sceneDocument.kind() == ResourceKind.THEME) clearFragments();
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
        if (workspace.scenes().dragPosition() == null) scenes.select(workspace.projectGeneration(), draft, opened);
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
        stopAudition();
        closeDialogueAudio();
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
                        dialogueAudio = audioScope(content.assets(), expected, false);
                        showingIdle = false;
                        fragment = new DialogueFragment(new PreviewActions(playback), DialogueFragment.CornerControls.DISPLAY_ONLY,
                                assets.openImages(content.assets()));
                        owner.getChildFragmentManager().beginTransaction().replace(containerId, fragment, "editor-preview").commitNow();
                    }
                    render();
                } catch (RuntimeException failure) {
                    closeDialogueAudio();
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
        if (mode() == Mode.SCENE || mode() == Mode.THEME) return;
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
        closeDialogueAudio();
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
        displayedTheme = null; displayedThemeExample = -1;
        fragment = null;
        sceneActions = null; displayedScene = null; sceneDocument = null; sceneProject = -1;
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
        if (running() && fragment != null) {
            if (dialogueAudio != null) dialogueAudio.render(playback.state(), playback.drainBgm());
            fragment.render(playback.state());
        }
        else {
            closeDialogueAudio();
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
        audioChanged.run();
        changed.run();
    }

    DialogueFragment sceneFragment() { return sceneActions == null ? null : fragment; }

    void clearScenePreview() {
        if (sceneActions != null && sceneDocument != null && sceneDocument.kind() == ResourceKind.SCENE && !releasingView) clearFragments();
    }

    int themeExample() { return themeExample; }
    void themeExample(int example) { themeExample = example; refreshTheme(); if (view != null) view.refresh(); }
    String themeError() { return workspace.themes().error(); }
    private void requestThemeFrame(boolean immediate) {
        if (view == null || !view.isAttachedToWindow() || mode() != Mode.THEME) return;
        if (immediate) { view.removeCallbacks(themeFrame); themeFramePending = false; refreshTheme(); }
        else if (!themeFramePending) { themeFramePending = true; view.postOnAnimation(themeFrame); }
    }
    void refreshTheme() {
        if (disposed || releasingView || mode() != Mode.THEME || view == null || !view.isAttachedToWindow()) return;
        var manager = owner.getChildFragmentManager();
        if (manager.isDestroyed() || manager.isStateSaved()) return;
        boolean sameDocument = sceneActions != null && sceneProject == workspace.projectGeneration()
                && Objects.equals(sceneDocument, workspace.resources().opened());
        if (!sameDocument) clearFragments();
        var theme = workspace.themes().preview();
        if (theme == null) return;
        if (fragment == null || displayedThemeExample != themeExample) {
            var state = themeState(theme);
            if (fragment == null) {
                sceneActions = new SceneActions(state);
                fragment = new DialogueFragment(sceneActions, DialogueFragment.CornerControls.DISPLAY_ONLY,
                        assets.openImages(MaterialSnapshot.EMPTY), false);
                manager.beginTransaction().replace(containerId, fragment, "editor-theme-preview").commitNow();
            } else { sceneActions.state = state; fragment.render(state); }
            displayedThemeExample = themeExample;
            sceneProject = workspace.projectGeneration(); sceneDocument = workspace.resources().opened();
            displayedTheme = null;
        }
        if (!theme.equals(displayedTheme)) {
            fragment.renderThemePreview(theme); displayedTheme = theme;
        }
    }
    private DialogueScreenState themeState(top.rookiestwo.maimai_dialogue.theme.ThemeDefinition theme) {
        var scene = new top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition(
                top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition.DEFAULT_THEME_ID, java.util.Optional.empty(),
                new top.rookiestwo.maimai_dialogue.presentation.DialogueBoxLayout(.5f, .5f, .6f, .65f,
                        top.rookiestwo.maimai_dialogue.presentation.visual.VisualAnchor.CENTER), java.util.Map.of(), java.util.Optional.empty());
        var initial = top.rookiestwo.maimai_dialogue.client.scene.SceneState.initial(scene);
        var options = new java.util.ArrayList<DialogueOption>();
        if (themeExample == 1) for (int i = 1; i <= 8; i++) options.add(new DialogueOption(
                net.minecraft.client.resources.language.I18n.get("gui.maimai_dialogue_editor.theme.preview_option", i),
                top.rookiestwo.maimai_dialogue.dialogue.branch.OptionIcon.QUESTION,
                top.rookiestwo.maimai_dialogue.dialogue.branch.ReturnTarget.INSTANCE));
        return new DialogueScreenState(++sceneGeneration, java.util.Optional.of(scene), java.util.Optional.of(theme),
                java.util.Optional.of(new top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback(sceneGeneration, initial, initial, java.util.List.of(), 0, 0)),
                top.rookiestwo.maimai_dialogue.client.session.PlaybackPhase.READY, true, java.util.Optional.empty(), false, false, 0,
                java.util.Optional.of(EditorWidgets.tr("scene.preview_speaker")), java.util.Optional.of(EditorWidgets.tr("theme.preview_text")),
                themeExample == 2 ? java.util.Optional.of(top.rookiestwo.maimai_dialogue.client.session.SessionMessage.translated(
                        "gui.maimai_dialogue_editor.theme.preview_error")) : java.util.Optional.empty(), java.util.List.of(), options, false, false);
    }

    boolean showScene(ScenePreviewSession.Prepared prepared, DialogueImageSource images) {
        if (disposed || releasingView || mode() != Mode.SCENE || view == null || !view.isAttachedToWindow()) return false;
        var manager = owner.getChildFragmentManager();
        if (manager.isDestroyed() || manager.isStateSaved()) return false;
        var initial = top.rookiestwo.maimai_dialogue.client.scene.SceneState.initial(prepared.scene());
        var state = staticSceneState(prepared, initial, ++sceneGeneration, 0);
        boolean reuse = sceneActions != null && fragment != null && displayedScene != null
                && displayedScene.images().equals(prepared.images()) && sceneProject == workspace.projectGeneration()
                && ScenePreviewSession.initialImageIds(displayedScene.scene()).equals(ScenePreviewSession.initialImageIds(prepared.scene()))
                && Objects.equals(sceneDocument, workspace.resources().opened());
        if (reuse) { sceneActions.state = state; fragment.render(state); }
        else {
            clearFragments();
            sceneActions = new SceneActions(state);
            fragment = new DialogueFragment(sceneActions, DialogueFragment.CornerControls.DISPLAY_ONLY, images.fork(), false);
            manager.beginTransaction().replace(containerId, fragment, "editor-scene-preview").commitNow();
        }
        displayedScene = prepared; sceneDocument = workspace.resources().opened(); sceneProject = workspace.projectGeneration();
        return true;
    }

    boolean updateScene(ScenePreviewSession.Prepared prepared) {
        if (sceneActions == null || fragment == null || displayedScene == null || releasingView || disposed
                || sceneProject != workspace.projectGeneration() || !Objects.equals(sceneDocument, workspace.resources().opened())
                || !displayedScene.images().equals(prepared.images()) || !displayedScene.theme().equals(prepared.theme())
                || !top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewFrame.sameBindings(displayedScene.scene(), prepared.scene())) return false;
        displayedScene = prepared;
        sceneActions.state = staticSceneState(prepared, top.rookiestwo.maimai_dialogue.client.scene.SceneState.initial(prepared.scene()), sceneGeneration, 0);
        renderSceneFrame(top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewFrame.initial(prepared.scene()));
        return true;
    }

    void renderSceneFrame(top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewFrame frame) {
        if (sceneActions == null || fragment == null || displayedScene == null) return;
        fragment.renderScenePreview(frame.state(), frame.layout(), frame.filter().orElse(null));
    }

    private static DialogueScreenState staticSceneState(ScenePreviewSession.Prepared prepared,
            top.rookiestwo.maimai_dialogue.client.scene.SceneState scene, long generation, long token) {
        return new DialogueScreenState(generation, java.util.Optional.of(prepared.scene()), java.util.Optional.of(prepared.theme()),
                java.util.Optional.of(new top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback(token, scene, scene, java.util.List.of(), 0, 0)),
                top.rookiestwo.maimai_dialogue.client.session.PlaybackPhase.READY, true, java.util.Optional.empty(), false, false, 0,
                java.util.Optional.of(EditorWidgets.tr("scene.preview_speaker")), java.util.Optional.of(EditorWidgets.tr("scene.preview_text")),
                java.util.Optional.empty(), java.util.List.of(), java.util.List.of(), false, false);
    }

    /** A static, real Dialogue layout with no session, commands, progress or audio callbacks. */
    private static final class SceneActions implements DialogueUiActions {
        private DialogueScreenState state;
        SceneActions(DialogueScreenState state) { this.state = state; }
        @Override public DialogueScreenState viewState() { return state; }
        @Override public void advance() {}
        @Override public void skipToEnd() {}
        @Override public void selectOption(DialogueOption option) {}
        @Override public void completePlayback(long generation, long token) {}
        @Override public void completeTextPlayback(long generation, long token) {}
        @Override public void closeFromUi() {}
        @Override public void onScreenDestroyed(DialogueScreenHandle screen) {}
    }

    void releaseView() {
        workspace.audio().endGesture(true);
        stopAudition(); closeDialogueAudio(); audioChanged = () -> {};
        workspace.scenes().endNumberDrag(true);
        workspace.scenes().setLiveListener(immediate -> {});
        workspace.themes().endGesture(true);
        workspace.themes().setLiveListener(immediate -> {});
        if (view != null) view.removeCallbacks(themeFrame);
        themeFramePending = false;
        releasingView = true;
        audio.stop();
        finishSceneDrag(false);
        scenes.select(workspace.projectGeneration(), null, null);
        // FragmentManager destroys child Views before the parent callback; do not start nested transactions here.
        reset();
        fragment = null;
        sceneActions = null; displayedScene = null; sceneDocument = null; sceneProject = -1;
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
        private void audio(Consumer<EditorDialogueAudio> action) {
            Core.getUiHandler().post(() -> {
                if (!disposed && !loading && session != null && playback == session && session.running() && dialogueAudio != null)
                    action.accept(dialogueAudio);
            });
        }
        @Override public void audioFrame(long generation, long token, int elapsedMs) { audio(audio -> audio.frame(generation, token, elapsedMs)); }
        @Override public void textRevealed(long generation, long token, int end, boolean audible) { audio(audio -> audio.reveal(generation, token, end, audible)); }
        @Override public void closeFromUi() { dispatch(EditorPreviewSession::stop); }
        @Override public void onScreenDestroyed(DialogueScreenHandle screen) {
            dispatch(current -> { if (fragment == screen) current.stop(); });
        }
    }
}
