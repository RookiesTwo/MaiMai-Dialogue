package top.rookiestwo.maimai_dialogue_editor.client.preview;

import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.fragment.FragmentManager;
import icyllis.modernui.view.View;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.controller.DialogueScreenHandle;
import top.rookiestwo.maimai_dialogue.client.controller.DialogueUiActions;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;
import top.rookiestwo.maimai_dialogue.client.ui.screen.DialogueFragment;
import top.rookiestwo.maimai_dialogue.dialogue.branch.DialogueOption;
import top.rookiestwo.maimai_dialogue_editor.client.EditorPreviewAssets;
import top.rookiestwo.maimai_dialogue_editor.client.EditorContentPreparation;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialSnapshot;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueImageSource;
import top.rookiestwo.maimai_dialogue_editor.preview.AudioPreviewSession;
import top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewSession;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;

import java.util.Objects;

/** UI-thread owner of one embedded runtime Fragment. Client callbacks cross back through the UI handler. */
public final class EditorPreviewHost {
    public enum Mode { DIALOGUE, IMAGE, SOUND, SCENE, THEME, ACTION, EMPTY }
    private final Fragment owner;
    private final ProjectWorkspace workspace;
    private final EditorPreviewAssets assets;
    private final AudioPreviewSession audio;
    private final EditorDialoguePreview dialoguePreview;
    private final EditorAudioAudition audition;
    private final ScenePreviewSession scenes;
    private final EditorActionPreview actionPreview;
    private DialogueImageSource actionImages;
    private final int containerId = View.generateViewId();
    private PreviewDisplay view;
    private DialogueFragment fragment;
    private boolean showingIdle, disposed;
    private final EditorTimelinePreview timeline;
    public EditorTimelinePreview timeline() { return timeline; }
    public EditorDialoguePreview dialogue() { return dialoguePreview; }
    private SceneActions sceneActions;
    private ScenePreviewSession.Prepared displayedScene;
    private ResourceKey sceneDocument;
    private long sceneProject = -1;
    private long sceneGeneration;
    private boolean releasingView;
    private int displayedThemeExample = -1;
    private top.rookiestwo.maimai_dialogue.theme.ThemeDefinition displayedTheme;
    private boolean themeFramePending;
    private final Runnable themeFrame = () -> {
        themeFramePending = false;
        refreshTheme();
    };

    public EditorPreviewHost(Fragment owner, ProjectWorkspace workspace, EditorPreviewAssets assets, AudioPreviewSession.Backend audioBackend) {
        this.owner = owner;
        this.workspace = workspace;
        this.assets = assets;
        timeline = new EditorTimelinePreview(workspace, new EditorTimelinePreview.Surface() {
            @Override public PreviewDisplay display() { return view; }
            @Override public DialogueFragment fragment() { return fragment; }
            @Override public boolean active() { return !disposed; }
            @Override public boolean canSeek() {
                return fragment != null && !dialoguePreview.loading() && (mode() == Mode.ACTION ? actionPreview.canSeek() : dialoguePreview.canSeek());
            }
            @Override public void pauseForSeek() {
                dialoguePreview.closeAudio(); if (audition.active()) audition.stop();
                if (mode() == Mode.ACTION) actionPreview.pauseForSeek();
            }
            @Override public void replay() { if (mode() == Mode.ACTION) actionPreview.play(); else dialoguePreview.restartStep(); }
        });
        audio = new AudioPreviewSession(audioBackend, this::refresh);
        audition = new EditorAudioAudition(workspace, () -> {
            if (running() || loading()) stop();
            audio.stop();
        }, this::refresh);
        scenes = new ScenePreviewSession((draft, key) -> EditorContentPreparation.prepare(workspace,
                external -> ScenePreviewSession.prepare(draft, key, external)), task -> Core.getUiHandler().post(task), this::refresh);
        dialoguePreview = new EditorDialoguePreview(workspace, assets, timeline, audition, new EditorDialoguePreview.Surface() {
            @Override public boolean disposed() { return disposed; }
            @Override public boolean ready() { return viewReady(); }
            @Override public PreviewDisplay display() { return view; }
            @Override public DialogueFragment fragment() { return fragment; }
            @Override public void show(DialogueUiActions actions, DialogueImageSource images) {
                showingIdle = false;
                fragment = embeddedFragment(actions, images);
                owner.getChildFragmentManager().beginTransaction().replace(containerId, fragment, "editor-preview").commitNow();
            }
            @Override public void showIdle() { showIdleControls(); }
            @Override public void refresh() { EditorPreviewHost.this.refresh(); }
            @Override public void stop() { EditorPreviewHost.this.stop(); }
        });
        actionPreview = new EditorActionPreview(this);
    }

    public View createView(java.util.function.IntFunction<? extends PreviewDisplay> factory) {
        releasingView = false;
        view = factory.apply(containerId);
        workspace.scenes().setLiveListener(immediate -> { if (view != null) view.requestSceneFrame(immediate); });
        workspace.themes().setLiveListener(this::requestThemeFrame);
        return view.root();
    }

    public void setReferenceHeight(int height) {
        if (!disposed && view != null) view.setReferenceHeight(height);
    }

    public void finishViewportResize() {
        if (!disposed && view != null) view.refreshContentAfterLayout();
    }

    public void refreshViewport(PreviewDisplay sourceView) {
        if (!disposed && view == sourceView && fragment != null) fragment.refreshViewport();
    }

    public boolean canStart() {
        return mode() == Mode.ACTION ? actionPreview.canPlay() : dialoguePreview.canOperate();
    }

    public boolean running() { return dialoguePreview.running(); }
    public boolean loading() { return dialoguePreview.loading(); }
    public String message() { return dialoguePreview.message(); }
    public String error() { return dialoguePreview.error(); }
    public record ImagePreview(String namespace, String path, boolean linear, long revision) {}
    public Mode mode() {
        ResourceKey key = workspace.resources().opened();
        if (key == null) return Mode.EMPTY;
        return switch (key.kind()) {
            case DIALOGUE -> Mode.DIALOGUE;
            case IMAGE, VISUAL_ASSET -> Mode.IMAGE;
            case SOUND -> Mode.SOUND;
            case SCENE -> Mode.SCENE;
            case THEME -> Mode.THEME;
            case ACTION -> Mode.ACTION;
            default -> Mode.EMPTY;
        };
    }
    public AudioPreviewSession audio() { return audio; }
    public EditorActionPreview actionPreview() { return actionPreview; }
    public EditorAudioAudition audition() { return audition; }
    public ScenePreviewSession scenes() { return scenes; }
    public EditorPreviewAssets assets() { return assets; }
    public ProjectWorkspace workspace() { return workspace; }
    public void finishSceneDrag(boolean commit) { if (view != null) view.finishSceneDrag(commit); }
    boolean viewingMaterial() {
        ResourceKey key = workspace.resources().opened();
        return key != null && (key.kind() == ResourceKind.IMAGE || key.kind() == ResourceKind.VISUAL_ASSET);
    }
    public ImagePreview imagePreview() {
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
    public DialogueImageSource openImages() { return assets.openImages(); }

    public void synchronize() {
        // Do not consume navigation before mounting is possible. The attachment callback retries every preview mode.
        if (!viewReady()) return;
        audition.synchronize();
        if (mode() != Mode.THEME && sceneDocument != null && sceneDocument.kind() == ResourceKind.THEME) clearFragments();
        // Undo/redo may restore a different document cursor before the properties View is rebound.
        var document = workspace.content().snapshot();
        record SoundSelection(long project, ResourceKey key) {}
        if (mode() == Mode.SOUND && document.key() != null && document.key().equals(workspace.resources().opened())
                && document.data() != null && workspace.draft() != null) {
            String blob = top.rookiestwo.maimai_dialogue_editor.material.MaterialPack.string(document.data().get("blob"));
            audio.select(new SoundSelection(workspace.projectGeneration(), document.key()), workspace.draft().blob(blob));
        } else audio.select(null, null);
        if (workspace.scenes().dragPosition() == null)
            scenes.select(workspace.projectGeneration(), workspace.draft(), workspace.resources().opened());
        if (!dialoguePreview.synchronize(this::acceptCanvasCommit)) return;
        actionPreview.synchronize();
        if (!dialoguePreview.loading() && !running()) showIdleControls();
        if (view != null) view.refresh();
    }

    // 拖动结束时只更换采样数据，已挂载的 Fragment、图片和播放头保持原位。
    private void acceptCanvasCommit() {
        var commit = workspace.actions().canvasCommit();
        if (disposed || dialoguePreview.loading() || fragment == null || !timeline.matches(commit)) return;
        if (commit.context().standalone()) {
            if (!actionPreview.acceptCanvasCommit(commit.before(), commit.updated().calls().getFirst().action())) return;
        } else if (!dialoguePreview.acceptsCanvas(commit.before())) return;
        var calls = workspace.actions().calls();
        if (!timeline.replace(commit, commit.context().standalone() ? 1 : calls.size())) return;
        // Dialogue 会话保持暂停；显式播放／推进仍从最新草稿重新开始，避免使用旧定义。
        if (!commit.context().standalone()) { dialoguePreview.canvasCommitted(); }
        timeline.notifyChanged();
    }

    public void start() { if (mode() == Mode.ACTION) actionPreview.play(); else dialoguePreview.start(); }
    public void advance() { if (mode() == Mode.ACTION) actionPreview.play(); else dialoguePreview.advance(); }
    public void stop() {
        if (mode() == Mode.ACTION) { dialoguePreview.reset(); actionPreview.stop(); }
        else dialoguePreview.stop();
    }
    private void showIdleControls() {
        if (disposed || view == null || !view.root().isAttachedToWindow()) return;
        if (mode() == Mode.SCENE || mode() == Mode.THEME || mode() == Mode.ACTION) return;
        if (mode() != Mode.DIALOGUE) { clearFragments(); return; }
        FragmentManager manager = owner.getChildFragmentManager();
        if (manager.isDestroyed() || manager.isStateSaved()) return;
        if (showingIdle && fragment != null) return;
        clearFragments();
        fragment = embeddedFragment(dialoguePreview.idleActions(), DialogueImageSource.RESOURCES);
        manager.beginTransaction().replace(containerId, fragment, "editor-preview").commitNow();
        showingIdle = true;
    }

    private void clearFragments() {
        dialoguePreview.forgetDisplay();
        actionImages = null;
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

    void refresh() {
        if (view != null) view.refresh();
        dialoguePreview.notifySimulation();
        audition.notifyChanged();
        timeline.notifyChanged();
    }

    public DialogueFragment sceneFragment() { return sceneActions == null ? null : fragment; }

    boolean actionViewReady() {
        return viewReady() && mode() == Mode.ACTION;
    }
    private boolean viewReady() {
        return !disposed && !releasingView && view != null && view.root().isAttachedToWindow()
                && !owner.getChildFragmentManager().isDestroyed() && !owner.getChildFragmentManager().isStateSaved();
    }
    /** Every embedded mode must leave keyboard focus (and IME composition) with the editor's active field. */
    private static DialogueFragment embeddedFragment(DialogueUiActions actions, DialogueImageSource images) {
        return new DialogueFragment(actions, DialogueFragment.CornerControls.DISPLAY_ONLY, images, false);
    }
    void clearActionPreview() {
        if (!releasingView && sceneDocument != null && sceneDocument.kind() == ResourceKind.ACTION) clearFragments();
    }
    void showAction(DialogueScreenState state, DialogueUiActions actions, DialogueImageSource images) {
        if (!actionViewReady()) return;
        // Replay can reuse the mounted views; changing the scene/assets gets a fresh set of image handles.
        if (fragment == null || actionImages != images) {
            clearFragments();
            fragment = embeddedFragment(actions, images.fork());
            owner.getChildFragmentManager().beginTransaction().replace(containerId, fragment, "editor-action-preview").commitNow();
            actionImages = images; sceneDocument = workspace.resources().opened(); sceneProject = workspace.projectGeneration();
        } else fragment.render(state);
    }

    public void clearScenePreview() {
        if (sceneActions != null && sceneDocument != null && sceneDocument.kind() == ResourceKind.SCENE && !releasingView) clearFragments();
    }

    public int themeExample() { return workspace.themeExample(); }
    public void themeExample(int example) { workspace.themeExample(example); refreshTheme(); if (view != null) view.refresh(); }
    public String themeError() { return workspace.themes().error(); }
    private void requestThemeFrame(boolean immediate) {
        if (view == null || !view.root().isAttachedToWindow() || mode() != Mode.THEME) return;
        if (immediate) { view.root().removeCallbacks(themeFrame); themeFramePending = false; refreshTheme(); }
        else if (!themeFramePending) { themeFramePending = true; view.root().postOnAnimation(themeFrame); }
    }
    public void refreshTheme() {
        if (disposed || releasingView || mode() != Mode.THEME || view == null || !view.root().isAttachedToWindow()) return;
        var manager = owner.getChildFragmentManager();
        if (manager.isDestroyed() || manager.isStateSaved()) return;
        boolean sameDocument = sceneActions != null && sceneProject == workspace.projectGeneration()
                && Objects.equals(sceneDocument, workspace.resources().opened());
        if (!sameDocument) clearFragments();
        var theme = workspace.themes().preview();
        if (theme == null) return;
        if (fragment == null || displayedThemeExample != themeExample()) {
            var state = themeState(theme);
            if (fragment == null) {
                sceneActions = new SceneActions(state);
                fragment = embeddedFragment(sceneActions, assets.openImages(MaterialSnapshot.EMPTY));
                manager.beginTransaction().replace(containerId, fragment, "editor-theme-preview").commitNow();
            } else { sceneActions.state = state; fragment.render(state); }
            displayedThemeExample = themeExample();
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
        if (themeExample() == 1) for (int i = 1; i <= 8; i++) options.add(new DialogueOption(
                net.minecraft.client.resources.language.I18n.get("gui.maimai_dialogue_editor.theme.preview_option", i),
                top.rookiestwo.maimai_dialogue.dialogue.branch.OptionIcon.QUESTION,
                top.rookiestwo.maimai_dialogue.dialogue.branch.ReturnTarget.INSTANCE));
        return new DialogueScreenState(++sceneGeneration, java.util.Optional.of(scene), java.util.Optional.of(theme),
                java.util.Optional.of(new top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback(sceneGeneration, initial, initial, java.util.List.of(), 0, 0)),
                top.rookiestwo.maimai_dialogue.client.session.PlaybackPhase.READY, true, java.util.Optional.empty(), false, false, 0,
                java.util.Optional.of(net.minecraft.client.resources.language.I18n.get("gui.maimai_dialogue_editor.scene.preview_speaker")), java.util.Optional.of(net.minecraft.client.resources.language.I18n.get("gui.maimai_dialogue_editor.theme.preview_text")),
                themeExample() == 2 ? java.util.Optional.of(top.rookiestwo.maimai_dialogue.client.session.SessionMessage.translated(
                        "gui.maimai_dialogue_editor.theme.preview_error")) : java.util.Optional.empty(), java.util.List.of(), options, false, false);
    }

    public boolean showScene(ScenePreviewSession.Prepared prepared, DialogueImageSource images) {
        if (disposed || releasingView || mode() != Mode.SCENE || view == null || !view.root().isAttachedToWindow()) return false;
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
            fragment = embeddedFragment(sceneActions, images.fork());
            manager.beginTransaction().replace(containerId, fragment, "editor-scene-preview").commitNow();
        }
        displayedScene = prepared; sceneDocument = workspace.resources().opened(); sceneProject = workspace.projectGeneration();
        return true;
    }

    public boolean updateScene(ScenePreviewSession.Prepared prepared) {
        if (sceneActions == null || fragment == null || displayedScene == null || releasingView || disposed
                || sceneProject != workspace.projectGeneration() || !Objects.equals(sceneDocument, workspace.resources().opened())
                || !displayedScene.images().equals(prepared.images()) || !displayedScene.theme().equals(prepared.theme())
                || !top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewFrame.sameBindings(displayedScene.scene(), prepared.scene())) return false;
        displayedScene = prepared;
        sceneActions.state = staticSceneState(prepared, top.rookiestwo.maimai_dialogue.client.scene.SceneState.initial(prepared.scene()), sceneGeneration, 0);
        renderSceneFrame(top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewFrame.initial(prepared.scene()));
        return true;
    }

    public void renderSceneFrame(top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewFrame frame) {
        if (sceneActions == null || fragment == null || displayedScene == null) return;
        fragment.renderScenePreview(frame.state(), frame.layout(), frame.filter().orElse(null));
    }

    private static DialogueScreenState staticSceneState(ScenePreviewSession.Prepared prepared,
            top.rookiestwo.maimai_dialogue.client.scene.SceneState scene, long generation, long token) {
        return new DialogueScreenState(generation, java.util.Optional.of(prepared.scene()), java.util.Optional.of(prepared.theme()),
                java.util.Optional.of(new top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback(token, scene, scene, java.util.List.of(), 0, 0)),
                top.rookiestwo.maimai_dialogue.client.session.PlaybackPhase.READY, true, java.util.Optional.empty(), false, false, 0,
                java.util.Optional.of(net.minecraft.client.resources.language.I18n.get("gui.maimai_dialogue_editor.scene.preview_speaker")), java.util.Optional.of(net.minecraft.client.resources.language.I18n.get("gui.maimai_dialogue_editor.scene.preview_text")),
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

    public void releaseView() {
        workspace.actions().endGesture(true);
        workspace.audio().endGesture(true);
        if (disposed) audition.dispose(); else audition.releaseView();
        dialoguePreview.closeAudio();
        workspace.scenes().endNumberDrag(true);
        workspace.scenes().setLiveListener(immediate -> {});
        workspace.themes().endGesture(true);
        workspace.themes().setLiveListener(immediate -> {});
        if (view != null) view.root().removeCallbacks(themeFrame);
        themeFramePending = false;
        releasingView = true;
        actionPreview.release(); actionImages = null;
        audio.stop();
        finishSceneDrag(false);
        scenes.select(workspace.projectGeneration(), null, null);
        // FragmentManager destroys child Views before the parent callback; do not start nested transactions here.
        dialoguePreview.reset();
        fragment = null;
        sceneActions = null; displayedScene = null; sceneDocument = null; sceneProject = -1;
        showingIdle = false;
        view = null;
        timeline.releaseListeners();
        dialoguePreview.releaseListeners();
    }

    public void onViewReady() {
        var expectedView = view;
        if (expectedView != null) expectedView.root().post(() -> {
            if (view == expectedView && viewReady()) synchronize();
        });
    }

    public void dispose() {
        disposed = true;
        actionPreview.dispose();
        scenes.dispose();
        releaseView();
    }

}
