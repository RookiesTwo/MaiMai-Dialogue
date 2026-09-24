package top.rookiestwo.maimai_dialogue_editor.client.preview;

import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.fragment.FragmentManager;
import icyllis.modernui.view.View;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.controller.DialogueUiActions;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;
import top.rookiestwo.maimai_dialogue.client.ui.screen.DialogueFragment;
import top.rookiestwo.maimai_dialogue_editor.client.EditorPreviewAssets;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueImageSource;
import top.rookiestwo.maimai_dialogue_editor.preview.AudioPreviewSession;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;


/** UI-thread owner of one embedded runtime Fragment. Client callbacks cross back through the UI handler. */
public final class EditorPreviewHost {
    public enum Mode { DIALOGUE, IMAGE, SOUND, SCENE, THEME, ACTION, EMPTY }
    private final Fragment owner;
    private final ProjectWorkspace workspace;
    private final EditorPreviewAssets assets;
    private final AudioPreviewSession audio;
    private final EditorDialoguePreview dialoguePreview;
    private final EditorAudioAudition audition;
    private final EditorStaticPreview staticPreview;
    private final EditorActionPreview actionPreview;
    private final int containerId = View.generateViewId();
    private PreviewDisplay view;
    private DialogueFragment fragment;
    private boolean showingIdle, disposed;
    private final EditorTimelinePreview timeline;
    public EditorTimelinePreview timeline() { return timeline; }
    public EditorDialoguePreview dialogue() { return dialoguePreview; }
    private boolean releasingView;

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
        var mount = new PreviewMount() {
            @Override public boolean active() { return !disposed && !releasingView; }
            @Override public boolean ready() { return viewReady(); }
            @Override public PreviewDisplay display() { return view; }
            @Override public DialogueFragment fragment() { return fragment; }
            @Override public void clear() { clearFragments(); }
            @Override public void show(DialogueUiActions actions, DialogueImageSource images, String tag) {
                mountPreview(actions, images, tag);
            }
        };
        staticPreview = new EditorStaticPreview(workspace, assets, mount, this::refresh);
        dialoguePreview = new EditorDialoguePreview(workspace, assets, timeline, audition, new EditorDialoguePreview.Surface() {
            @Override public boolean disposed() { return disposed; }
            @Override public boolean ready() { return viewReady(); }
            @Override public PreviewDisplay display() { return view; }
            @Override public DialogueFragment fragment() { return fragment; }
            @Override public void show(DialogueUiActions actions, DialogueImageSource images) {
                showingIdle = false;
                mountPreview(actions, images, "editor-preview");
            }
            @Override public void showIdle() { showIdleControls(); }
            @Override public void refresh() { EditorPreviewHost.this.refresh(); }
            @Override public void stop() { EditorPreviewHost.this.stop(); }
        });
        actionPreview = new EditorActionPreview(workspace, assets, timeline, audition, mount, this::refresh);
    }

    public View createView(java.util.function.IntFunction<? extends PreviewDisplay> factory) {
        releasingView = false;
        view = factory.apply(containerId);
        workspace.scenes().setLiveListener(immediate -> { if (view != null) view.requestSceneFrame(immediate); });
        workspace.themes().setLiveListener(staticPreview::requestThemeFrame);
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
    public EditorStaticPreview staticPreview() { return staticPreview; }
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
        staticPreview.synchronize();
        // Undo/redo may restore a different document cursor before the properties View is rebound.
        var document = workspace.content().snapshot();
        record SoundSelection(long project, ResourceKey key) {}
        if (mode() == Mode.SOUND && document.key() != null && document.key().equals(workspace.resources().opened())
                && document.data() != null && workspace.draft() != null) {
            String blob = top.rookiestwo.maimai_dialogue_editor.material.MaterialPack.string(document.data().get("blob"));
            audio.select(new SoundSelection(workspace.projectGeneration(), document.key()), workspace.draft().blob(blob));
        } else audio.select(null, null);
        staticPreview.select();
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
        mountPreview(dialoguePreview.idleActions(), DialogueImageSource.RESOURCES, "editor-preview");
        showingIdle = true;
    }

    private void clearFragments() {
        dialoguePreview.forgetDisplay();
        actionPreview.forgetDisplay();
        staticPreview.clear();
        fragment = null;
        staticPreview.clear();
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


    private boolean viewReady() {
        return !disposed && !releasingView && view != null && view.root().isAttachedToWindow()
                && !owner.getChildFragmentManager().isDestroyed() && !owner.getChildFragmentManager().isStateSaved();
    }
    /** Every embedded mode must leave keyboard focus (and IME composition) with the editor's active field. */
    private static DialogueFragment embeddedFragment(DialogueUiActions actions, DialogueImageSource images) {
        return new DialogueFragment(actions, DialogueFragment.CornerControls.DISPLAY_ONLY, images, false);
    }
    private void mountPreview(DialogueUiActions actions, DialogueImageSource images, String tag) {
        fragment = embeddedFragment(actions, images);
        owner.getChildFragmentManager().beginTransaction().replace(containerId, fragment, tag).commitNow();
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
        staticPreview.stopFrames();
        releasingView = true;
        actionPreview.release(); actionPreview.forgetDisplay();
        audio.stop();
        finishSceneDrag(false);
        staticPreview.releaseView();
        // FragmentManager destroys child Views before the parent callback; do not start nested transactions here.
        dialoguePreview.reset();
        fragment = null;
        staticPreview.clear();
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
        staticPreview.dispose();
        releaseView();
    }

}
