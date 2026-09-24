package top.rookiestwo.maimai_dialogue_editor.client.preview;

import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import net.minecraft.client.Minecraft;
import top.rookiestwo.maimai_dialogue.client.controller.DialogueScreenHandle;
import top.rookiestwo.maimai_dialogue.client.controller.DialogueUiActions;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;
import top.rookiestwo.maimai_dialogue.client.ui.screen.DialogueFragment;
import top.rookiestwo.maimai_dialogue.dialogue.branch.DialogueOption;
import top.rookiestwo.maimai_dialogue_editor.client.EditorPreviewAssets;
import top.rookiestwo.maimai_dialogue_editor.client.EditorContentPreparation;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialSnapshot;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueImageSource;
import top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewSession;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;

import java.util.Objects;

// Scene 与 Theme 共用静态渲染，会话准备、挂载复用和主题采样由此对象协调。
public final class EditorStaticPreview {
    private final ProjectWorkspace workspace;
    private final EditorPreviewAssets assets;
    private final PreviewMount mount;
    private final ScenePreviewSession session;
    private EditorScenePreview scene;
    private SceneActions sceneActions;
    private ScenePreviewSession.Prepared displayedScene;
    private ResourceKey sceneDocument;
    private long sceneProject = -1;
    private long sceneGeneration;
    private int displayedThemeExample = -1;
    private top.rookiestwo.maimai_dialogue.theme.ThemeDefinition displayedTheme;
    private boolean themeFramePending;
    private final Runnable themeFrame = () -> {
        themeFramePending = false;
        refreshTheme();
    };

    EditorStaticPreview(ProjectWorkspace workspace, EditorPreviewAssets assets, PreviewMount mount, Runnable changed) {
        this.workspace = workspace; this.assets = assets; this.mount = mount;
        session = new ScenePreviewSession((draft, key) -> EditorContentPreparation.prepare(workspace,
                external -> ScenePreviewSession.prepare(draft, key, external)), task -> Core.getUiHandler().post(task), changed);
    }
    private boolean selected(ResourceKind kind) {
        var key = workspace.resources().opened(); return key != null && key.kind() == kind;
    }
    public EditorScenePreview createScenePreview() {
        if (scene != null) { scene.release(true); scene.setListener(() -> {}); }
        scene = new EditorScenePreview(workspace, assets, this);
        return scene;
    }
    ScenePreviewSession session() { return session; }
    DialogueFragment sceneFragment() { return sceneActions == null ? null : mount.fragment(); }
    void synchronize() {
        if (!selected(ResourceKind.THEME) && sceneDocument != null && sceneDocument.kind() == ResourceKind.THEME) mount.clear();
    }
    void select() {
        if (workspace.scenes().dragPosition() == null)
            session.select(workspace.projectGeneration(), workspace.draft(), workspace.resources().opened());
    }
    void clear() {
        displayedTheme = null; displayedThemeExample = -1;
        sceneActions = null; displayedScene = null; sceneDocument = null; sceneProject = -1;
    }
    void stopFrames() {
        if (mount.display() != null) mount.display().root().removeCallbacks(themeFrame);
        themeFramePending = false;
    }
    void releaseView() {
        session.select(workspace.projectGeneration(), null, null);
        if (scene != null) { scene.release(true); scene.setListener(() -> {}); }
    }
    void dispose() { session.dispose(); if (scene != null) scene.release(true); }
    void clearScenePreview() {
        if (sceneActions != null && sceneDocument != null && sceneDocument.kind() == ResourceKind.SCENE && mount.active()) mount.clear();
    }

    public int themeExample() { return workspace.themeExample(); }
    public void themeExample(int example) { workspace.themeExample(example); refreshTheme(); if (mount.display() != null) mount.display().refresh(); }
    public String themeError() { return workspace.themes().error(); }
    void requestThemeFrame(boolean immediate) {
        if (mount.display() == null || !mount.display().root().isAttachedToWindow() || !selected(ResourceKind.THEME)) return;
        if (immediate) { mount.display().root().removeCallbacks(themeFrame); themeFramePending = false; refreshTheme(); }
        else if (!themeFramePending) { themeFramePending = true; mount.display().root().postOnAnimation(themeFrame); }
    }
    public void refreshTheme() {
        if (!mount.ready() || !selected(ResourceKind.THEME)) return;
        boolean sameDocument = sceneActions != null && sceneProject == workspace.projectGeneration()
                && Objects.equals(sceneDocument, workspace.resources().opened());
        if (!sameDocument) mount.clear();
        var theme = workspace.themes().preview();
        if (theme == null) return;
        if (mount.fragment() == null || displayedThemeExample != themeExample()) {
            var state = themeState(theme);
            if (mount.fragment() == null) {
                sceneActions = new SceneActions(state);
                mount.show(sceneActions, assets.openImages(MaterialSnapshot.EMPTY), "editor-theme-preview");
            } else { sceneActions.state = state; mount.fragment().render(state); }
            displayedThemeExample = themeExample();
            sceneProject = workspace.projectGeneration(); sceneDocument = workspace.resources().opened();
            displayedTheme = null;
        }
        if (!theme.equals(displayedTheme)) {
            mount.fragment().renderThemePreview(theme); displayedTheme = theme;
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

    boolean showScene(ScenePreviewSession.Prepared prepared, DialogueImageSource images) {
        if (!mount.ready() || !selected(ResourceKind.SCENE)) return false;
        var initial = top.rookiestwo.maimai_dialogue.client.scene.SceneState.initial(prepared.scene());
        var state = staticSceneState(prepared, initial, ++sceneGeneration, 0);
        boolean reuse = sceneActions != null && mount.fragment() != null && displayedScene != null
                && displayedScene.images().equals(prepared.images()) && sceneProject == workspace.projectGeneration()
                && ScenePreviewSession.initialImageIds(displayedScene.scene()).equals(ScenePreviewSession.initialImageIds(prepared.scene()))
                && Objects.equals(sceneDocument, workspace.resources().opened());
        if (reuse) { sceneActions.state = state; mount.fragment().render(state); }
        else {
            mount.clear();
            sceneActions = new SceneActions(state);
            mount.show(sceneActions, images.fork(), "editor-scene-preview");
        }
        displayedScene = prepared; sceneDocument = workspace.resources().opened(); sceneProject = workspace.projectGeneration();
        return true;
    }

    boolean updateScene(ScenePreviewSession.Prepared prepared) {
        if (sceneActions == null || mount.fragment() == null || displayedScene == null || !mount.active()
                || sceneProject != workspace.projectGeneration() || !Objects.equals(sceneDocument, workspace.resources().opened())
                || !displayedScene.images().equals(prepared.images()) || !displayedScene.theme().equals(prepared.theme())
                || !top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewFrame.sameBindings(displayedScene.scene(), prepared.scene())) return false;
        displayedScene = prepared;
        sceneActions.state = staticSceneState(prepared, top.rookiestwo.maimai_dialogue.client.scene.SceneState.initial(prepared.scene()), sceneGeneration, 0);
        renderSceneFrame(top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewFrame.initial(prepared.scene()));
        return true;
    }

    void renderSceneFrame(top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewFrame frame) {
        if (sceneActions == null || mount.fragment() == null || displayedScene == null) return;
        mount.fragment().renderScenePreview(frame.state(), frame.layout(), frame.filter().orElse(null));
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

}
