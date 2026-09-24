package top.rookiestwo.maimai_dialogue_editor.client.ui;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.ChoicePresenter;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

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
import top.rookiestwo.maimai_dialogue.client.resource.ClientContentSnapshot;
import top.rookiestwo.maimai_dialogue.client.session.DialogueScreenState;
import top.rookiestwo.maimai_dialogue.client.ui.screen.DialogueFragment;
import top.rookiestwo.maimai_dialogue.dialogue.branch.DialogueOption;
import top.rookiestwo.maimai_dialogue_editor.content.ProjectContentSnapshot;
import top.rookiestwo.maimai_dialogue_editor.client.EditorPreviewAssets;
import top.rookiestwo.maimai_dialogue_editor.client.EditorDialogueAudio;
import top.rookiestwo.maimai_dialogue_editor.client.EditorContentPreparation;
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
public final class EditorPreviewHost {
    enum Mode { DIALOGUE, IMAGE, SOUND, SCENE, THEME, ACTION, EMPTY }
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
    private final EditorActionPreview actionPreview;
    private DialogueImageSource actionImages;
    private final int containerId = View.generateViewId();
    private EditorPreviewView view;
    private EditorPreviewSession playback;
    private DialogueFragment fragment;
    private PreviewActions previewActions;
    private long dialogueGeneration;
    private record PreparedDialogue(long project, ProjectDraft draft, ResourceKey key, ClientContentSnapshot external,
                                    ProjectContentSnapshot content, MaterialSnapshot assets) {}
    private PreparedDialogue preparedDialogue;
    private boolean showingIdle;
    private ProjectDraft source;
    private ResourceKey dialogue;
    private long revision;
    private boolean loading;
    private boolean advanceAfterLoad;
    private boolean skipAfterLoad;
    private DialogueOption optionAfterLoad;
    private boolean staleDialogueSession;
    private top.rookiestwo.maimai_dialogue.client.session.PlaybackPhase advancePhaseAfterLoad;
    private boolean disposed;
    private String message = "preview.idle";
    private String error = "";
    private final top.rookiestwo.maimai_dialogue_editor.preview.ActionTimeline timeline = new top.rookiestwo.maimai_dialogue_editor.preview.ActionTimeline();
    private Runnable timelineChanged = () -> {};
    private record TimelineBinding(long project, ProjectDraft draft, top.rookiestwo.maimai_dialogue_editor.document.ActionWorkspace.Context context) {}
    private TimelineBinding timelineBinding;
    private boolean timelineFramePending;
    private top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback canvasBase, canvasFrame;
    private top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback sampledPlayback;
    private int sampledPosition;
    private final Runnable timelineFrame = () -> {
        timelineFramePending = false;
        if (!disposed && timeline.manual() && canSeekTimeline()) {
            freezeTimelineFrame();
            if (view != null) view.refreshActionCanvas();
        }
    };
    private TimelineBinding currentTimelineBinding() {
        return new TimelineBinding(workspace.projectGeneration(), workspace.draft(), workspace.actions().context());
    }
    top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback timelinePlayback() {
        return Objects.equals(timelineBinding, currentTimelineBinding()) ? timeline.playback() : null;
    }
    top.rookiestwo.maimai_dialogue_editor.preview.ActionTimeline timeline() { return timeline; }
    void setTimelineListener(Runnable listener) { timelineChanged = listener; }
    private final java.util.Set<Runnable> timelineObservers = new java.util.LinkedHashSet<>();
    private View timelineHoverOwner;
    private boolean playheadHovered;
    public void addTimelineObserver(Runnable listener) { timelineObservers.add(listener); }
    public void removeTimelineObserver(Runnable listener) { timelineObservers.remove(listener); }
    private void notifyTimelineChanged() {
        timelineChanged.run(); java.util.List.copyOf(timelineObservers).forEach(Runnable::run);
    }
    boolean playheadHovered() { return playheadHovered; }
    void playheadHover(View owner, boolean hovered) {
        if (!hovered && timelineHoverOwner != owner) return;
        timelineHoverOwner = hovered ? owner : null;
        if (playheadHovered == hovered) return;
        playheadHovered = hovered;
        // Hover exit may run while rows are being removed; never rebuild the timeline recursively.
        var expectedView = view;
        if (expectedView != null) expectedView.post(() -> {
            if (!disposed && view == expectedView) notifyTimelineChanged();
        });
    }

    void bindTimeline(Object owner, top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback scene, int calls, boolean playing) {
        canvasBase = canvasFrame = null;
        sampledPlayback = null;
        timelineBinding = currentTimelineBinding();
        timeline.bind(owner, scene, calls, playing); notifyTimelineChanged();
    }
    void followTimeline(Object owner, long token, int elapsed) {
        if (timeline.follow(owner, token, elapsed)) notifyTimelineChanged();
    }
    void clearTimeline(Object owner) {
        timeline.clear(owner);
        if (canvasBase != timeline.playback()) canvasBase = canvasFrame = null;
        if (sampledPlayback != timeline.playback()) sampledPlayback = null;
        notifyTimelineChanged();
    }
    void freezeTimelineFrame() {
        if (fragment != null && timeline.playback() != null) {
            fragment.renderScenePlaybackPreview(
                canvasBase == timeline.playback() && canvasFrame != null ? canvasFrame : timeline.playback(), timeline.position());
            sampledPlayback = timeline.playback(); sampledPosition = timeline.position();
        }
    }
    boolean timelineCanvasReady() {
        return timeline.manual() && sampledPlayback == timelinePlayback() && sampledPosition == timeline.position();
    }
    DialogueFragment timelineFragment() { return canSeekTimeline() ? fragment : null; }
    void renderCanvasFrame(top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback expected,
                           top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback frame, boolean immediate) {
        if (expected != timeline.playback()) return;
        canvasBase = expected; canvasFrame = frame;
        if (immediate) {
            if (view != null) view.removeCallbacks(timelineFrame);
            timelineFramePending = false; freezeTimelineFrame();
        } else if (!timelineFramePending && view != null) {
            timelineFramePending = true; view.postOnAnimation(timelineFrame);
        }
    }
    boolean canSeekTimeline() {
        return timelinePlayback() != null && fragment != null && !loading && (mode() == Mode.ACTION ? actionPreview.canSeek()
                : canOperate() && source == workspace.draft() && workspace.resources().selection().isStep());
    }
    void seekTimeline(top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback expected, int elapsed) {
        if (!canSeekTimeline() || !timeline.seek(expected, elapsed)) return;
        canvasBase = canvasFrame = null;
        closeDialogueAudio(); if (auditioning()) stopAudition();
        if (mode() == Mode.ACTION) actionPreview.pauseForSeek();
        if (!timelineFramePending && view != null) { timelineFramePending = true; view.postOnAnimation(timelineFrame); }
        notifyTimelineChanged();
    }
    void replayTimeline() { if (mode() == Mode.ACTION) actionPreview.play(); else restartStep(); }
    private ProjectDraft observedDraft;
    private long observedProject = -1;
    private ResourceKey observedDocument;
    private ResourceTree.Node observedSelection;
    private long observedSelectionRevision = -1;
    private boolean publishingPosition;
    private top.rookiestwo.maimai_dialogue_editor.preview.PreviewScenario observedSimulation;
    private java.util.List<EditorPreviewSession.SimulationResult> simulationResults = java.util.List.of();
    private Runnable simulationChanged = () -> {};
    public void setSimulationListener(Runnable listener) { simulationChanged = listener; }
    public java.util.List<EditorPreviewSession.SimulationResult> simulationResults() { return simulationResults; }
    public boolean canSimulateSkip() { return canOperate() && !loading && running() && playback.state().canSkipToEnd(); }
    public void simulateSkip() {
        if (!canSimulateSkip()) return;
        if (staleDialogueSession) { restartStep(); skipAfterLoad = true; return; }
        playback.skipToEnd(); render();
    }
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
        audio = new AudioPreviewSession(audioBackend, this::refresh);
        scenes = new ScenePreviewSession((draft, key) -> EditorContentPreparation.prepare(workspace,
                external -> ScenePreviewSession.prepare(draft, key, external)), task -> Core.getUiHandler().post(task), this::refresh);
        actionPreview = new EditorActionPreview(this);
    }

    EditorPreviewView createView(Context context, ChoicePresenter choices) {
        releasingView = false;
        view = new EditorPreviewView(context, this, containerId, choices);
        workspace.scenes().setLiveListener(immediate -> { if (view != null) view.requestSceneFrame(immediate); });
        workspace.themes().setLiveListener(this::requestThemeFrame);
        return view;
    }

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
        return mode() == Mode.ACTION ? actionPreview.canPlay() : canOperate();
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
            case ACTION -> Mode.ACTION;
            default -> Mode.EMPTY;
        };
    }
    AudioPreviewSession audio() { return audio; }
    EditorActionPreview actionPreview() { return actionPreview; }
    public void setAudioListener(Runnable listener) { audioChanged = listener; }
    public boolean auditioning() { return auditionLoading || audition != null && audition.active(); }
    public String auditionError() { return auditionError; }
    private EditorDialogueAudio audioScope(MaterialSnapshot materials, long expected, boolean sample) {
        return new EditorDialogueAudio(materials, task -> workspace.prepare(() -> { task.run(); return null; }),
                failure -> Core.getUiHandler().post(() -> {
                    if (disposed || (sample ? expected != auditionRevision : expected != revision)) return;
                    if (sample) auditionError = failure; else error = failure;
                    refresh();
                }), () -> Core.getUiHandler().post(this::refresh));
    }
    public void audition() {
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
    public void stopAudition() {
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
        // Do not consume navigation before mounting is possible. The attachment callback retries every preview mode.
        if (!viewReady()) return;
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
        boolean projectChanged = observedProject != workspace.projectGeneration();
        boolean simulationUpdated = !Objects.equals(observedSimulation, workspace.simulation());
        observedSimulation = workspace.simulation();
        if (projectChanged || !Objects.equals(observedDocument, opened)) simulationResults = java.util.List.of();
        observedProject = workspace.projectGeneration();
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
        if (draftChanged && !projectChanged && !selectionChanged) acceptCanvasCommit();
        // Restoring a Step enters it just like a click; sampling at zero would freeze its entrance effects invisible.
        // Only edits to an already open document preserve the manual timeline position.
        if ((projectChanged || simulationUpdated || !draftChanged && selectionChanged) && selected.isStep() && Objects.equals(selected.owner(), opened)) {
            startAt(selected.stepIndex());
        } else {
            if (selected.isStep() && Objects.equals(selected.owner(), opened) && source != draft && canOperate()) {
                startAt(selected.stepIndex(), false, projectChanged ? 0 : timeline.manual() ? timeline.position() : Integer.MAX_VALUE);
            } else if (source != null && (source != draft || !Objects.equals(dialogue, opened))) stop();
            if (!draftChanged && selectionChanged && selected.kind() == ResourceKind.DIALOGUE
                    && selected.type() == ResourceTree.Type.RESOURCE) stop();
        }
        actionPreview.synchronize();
        if (!loading && !running()) showIdleControls();
        if (view != null) view.refresh();
    }

    // 拖动结束时只更换采样数据，已挂载的 Fragment、图片和播放头保持原位。
    private void acceptCanvasCommit() {
        var commit = workspace.actions().canvasCommit();
        if (commit == null || disposed || loading || fragment == null || timelineBinding == null || !timeline.manual()
                || timelineBinding.project() != workspace.projectGeneration() || timelineBinding.draft() != commit.before()
                || !Objects.equals(commit.context(), workspace.actions().context())
                || commit.index() != workspace.actions().selected() || commit.original() != timeline.playback()
                || canvasBase != commit.original() || canvasFrame != commit.updated()) return;
        if (commit.context().standalone()) {
            if (!actionPreview.acceptCanvasCommit(commit.before(), commit.updated().calls().getFirst().action())) return;
        } else if (!running() || source != commit.before()) return;
        var calls = workspace.actions().calls();
        if (!timeline.replace(commit.original(), commit.updated(), commit.context().standalone() ? 1 : calls.size())) return;
        timelineBinding = currentTimelineBinding();
        canvasBase = canvasFrame = null;
        sampledPlayback = timeline.playback(); sampledPosition = timeline.position();
        // Dialogue 会话保持暂停；显式播放／推进仍从最新草稿重新开始，避免使用旧定义。
        if (!commit.context().standalone()) { source = workspace.draft(); staleDialogueSession = true; }
        notifyTimelineChanged();
    }

    void start() {
        if (mode() == Mode.ACTION) { actionPreview.play(); return; }
        startAt(0);
    }
    void restartStep() {
        var selected = workspace.resources().selection();
        if (selected.isStep()) startAt(selected.stepIndex());
    }

    private void startAt(int step) {
        startAt(step, true, 0);
    }
    private void startAt(int step, boolean playNow, int seekTime) {
        // A new tree selection may supersede a pending start before the client snapshot arrives.
        if (!canOperate() || !viewReady()) return;
        // Automatic draft refresh is part of the current edit, not a new user operation.
        if (playNow) workspace.endEdit();
        stopAudition();
        closeDialogueAudio();
        freezeTimelineFrame();
        // Keep the displayed session and controls until the replacement is ready.
        // Its callbacks are suspended while loading, then discarded by session identity.
        source = workspace.draft();
        dialogue = workspace.resources().opened();
        ProjectDraft captured = source;
        var capturedSimulation = workspace.simulation();
        ResourceKey capturedKey = dialogue;
        long capturedProject = workspace.projectGeneration();
        var cached = preparedDialogue;
        long expected = ++revision;
        loading = true;
        advanceAfterLoad = false;
        skipAfterLoad = false;
        optionAfterLoad = null;
        advancePhaseAfterLoad = null;
        refresh();
        // Read the loaded resource snapshot on the client thread; never replace the global repository.
        Minecraft.getInstance().execute(() -> {
            var external = ClientServices.get().content().current();
            int interval = ClientConfig.get().defaultTypewriterIntervalMs();
            var preparation = cached != null && cached.project() == capturedProject && cached.draft() == captured
                    && cached.key().equals(capturedKey) && cached.external() == external
                    ? java.util.concurrent.CompletableFuture.completedFuture(cached) : workspace.prepare(() -> {
                try {
                    return new PreparedDialogue(capturedProject, captured, capturedKey, external,
                            new ProjectContentSnapshot(captured, external).prepare(
                            ResourceLocation.fromNamespaceAndPath(captured.namespace(), capturedKey.path())),
                            MaterialSnapshot.prepare(captured));
                } catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
            });
            preparation.whenComplete((content, preparationFailure) -> Core.getUiHandler().post(() -> {
                if (disposed || expected != revision || view == null) return;
                if (!viewReady()) {
                    // A saved/detached Fragment must be prepared again once it can mount, not marked as displayed.
                    reset();
                    observedProject = -1;
                    return;
                }
                loading = false;
                if (capturedProject != workspace.projectGeneration() || captured != workspace.draft()
                        || !capturedSimulation.equals(workspace.simulation()) || !Objects.equals(capturedKey, workspace.resources().opened())) {
                    stop();
                    return;
                }
                try {
                    if (preparationFailure != null) throw new java.util.concurrent.CompletionException(preparationFailure);
                    var prepared = new EditorPreviewSession(content.content(),
                            ResourceLocation.fromNamespaceAndPath(captured.namespace(), capturedKey.path()), interval, step,
                            capturedSimulation, ++dialogueGeneration);
                    if (playback != null) playback.stop();
                    playback = prepared;
                    staleDialogueSession = false;
                    if (!playNow) playback.prepareForEditing();
                    if (optionAfterLoad != null) playback.selectOptionAfterRefresh(optionAfterLoad);
                    optionAfterLoad = null;
                    if (skipAfterLoad) playback.skipToEnd();
                    skipAfterLoad = false;
                    if (advanceAfterLoad) {
                        if (advancePhaseAfterLoad == null) playback.advance();
                        else playback.advanceAfterRefresh(advancePhaseAfterLoad);
                    }
                    advanceAfterLoad = false;
                    advancePhaseAfterLoad = null;
                    if (running()) {
                        if (playNow) dialogueAudio = audioScope(content.assets(), expected, false);
                        showingIdle = false;
                        // Keep the mounted scene and its image handles when only playback data changed.
                        boolean reuse = fragment != null && previewActions != null && preparedDialogue != null
                                && preparedDialogue.project() == capturedProject && preparedDialogue.external() == external
                                && preparedDialogue.assets().equals(content.assets());
                        if (reuse) previewActions.session = playback;
                        else {
                            previewActions = new PreviewActions(playback);
                            fragment = embeddedFragment(previewActions, assets.openImages(content.assets()));
                            owner.getChildFragmentManager().beginTransaction().replace(containerId, fragment, "editor-preview").commitNow();
                        }
                        preparedDialogue = content;
                    }
                    render();
                    if (!playNow && running()) {
                        seekTimeline(timeline.playback(), seekTime);
                        freezeTimelineFrame();
                    }
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
        if (mode() == Mode.ACTION) { actionPreview.play(); return; }
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
        if (staleDialogueSession) {
            var phase = playback.state().playbackPhase();
            startAt(workspace.resources().selection().stepIndex());
            advanceAfterLoad = true; advancePhaseAfterLoad = phase;
            return;
        }
        playback.advance();
        render();
    }

    void stop() {
        if (mode() == Mode.ACTION) {
            reset(); actionPreview.stop(); return;
        }
        if (timeline.manual()) { restartStep(); return; }
        reset();
        showIdleControls();
        refresh();
    }

    private void showIdleControls() {
        if (disposed || view == null || !view.isAttachedToWindow()) return;
        if (mode() == Mode.SCENE || mode() == Mode.THEME || mode() == Mode.ACTION) return;
        if (mode() != Mode.DIALOGUE) { clearFragments(); return; }
        FragmentManager manager = owner.getChildFragmentManager();
        if (manager.isDestroyed() || manager.isStateSaved()) return;
        if (showingIdle && fragment != null) return;
        clearFragments();
        fragment = embeddedFragment(new PreviewActions(null), DialogueImageSource.RESOURCES);
        manager.beginTransaction().replace(containerId, fragment, "editor-preview").commitNow();
        showingIdle = true;
    }

    private void reset() {
        if (view != null) view.removeCallbacks(timelineFrame);
        timelineFramePending = false;
        canvasBase = canvasFrame = null;
        sampledPlayback = null;
        timeline.clear(); notifyTimelineChanged();
        closeDialogueAudio();
        ++revision;
        loading = false;
        advanceAfterLoad = false;
        staleDialogueSession = false; advancePhaseAfterLoad = null;
        skipAfterLoad = false;
        optionAfterLoad = null;
        if (playback != null) playback.stop();
        playback = null;
        previewActions = null;
        preparedDialogue = null;
        source = null;
        dialogue = null;
        error = "";
        message = "preview.idle";
    }

    private void clearFragments() {
        previewActions = null;
        preparedDialogue = null;
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

    private void render() {
        if (playback == null) return;
        dialogueGeneration = Math.max(dialogueGeneration, playback.state().generation());
        simulationResults = playback.simulationResults();
        followPosition();
        message = switch (playback.status()) {
            case RUNNING -> "preview.running";
            case FINISHED -> "preview.finished";
            case STOPPED -> "preview.idle";
            case FAILED -> "preview.failed";
        };
        error = playback.error();
        if (running() && fragment != null) {
            var calls = workspace.actions().calls();
            bindTimeline(playback, playback.state().scenePlayback().orElse(null), calls == null ? 0 : calls.size(), true);
            if (dialogueAudio != null) dialogueAudio.render(playback.state(), playback.drainBgm());
            if (!timeline.manual()) fragment.render(playback.state());
        }
        else {
            timeline.clear(); notifyTimelineChanged();
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

    void refresh() {
        if (view != null) view.refresh();
        simulationChanged.run();
        audioChanged.run();
        notifyTimelineChanged();
    }

    DialogueFragment sceneFragment() { return sceneActions == null ? null : fragment; }

    boolean actionViewReady() {
        return viewReady() && mode() == Mode.ACTION;
    }
    private boolean viewReady() {
        return !disposed && !releasingView && view != null && view.isAttachedToWindow()
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

    void clearScenePreview() {
        if (sceneActions != null && sceneDocument != null && sceneDocument.kind() == ResourceKind.SCENE && !releasingView) clearFragments();
    }

    int themeExample() { return workspace.themeExample(); }
    void themeExample(int example) { workspace.themeExample(example); refreshTheme(); if (view != null) view.refresh(); }
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
                java.util.Optional.of(EditorWidgets.tr("scene.preview_speaker")), java.util.Optional.of(EditorWidgets.tr("theme.preview_text")),
                themeExample() == 2 ? java.util.Optional.of(top.rookiestwo.maimai_dialogue.client.session.SessionMessage.translated(
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
            fragment = embeddedFragment(sceneActions, images.fork());
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
        workspace.actions().endGesture(true);
        workspace.audio().endGesture(true);
        stopAudition(); closeDialogueAudio(); audioChanged = () -> {};
        workspace.scenes().endNumberDrag(true);
        workspace.scenes().setLiveListener(immediate -> {});
        workspace.themes().endGesture(true);
        workspace.themes().setLiveListener(immediate -> {});
        if (view != null) view.removeCallbacks(themeFrame);
        themeFramePending = false;
        releasingView = true;
        actionPreview.release(); actionImages = null;
        audio.stop();
        finishSceneDrag(false);
        scenes.select(workspace.projectGeneration(), null, null);
        // FragmentManager destroys child Views before the parent callback; do not start nested transactions here.
        reset();
        fragment = null;
        sceneActions = null; displayedScene = null; sceneDocument = null; sceneProject = -1;
        showingIdle = false;
        observedProject = -1;
        view = null;
        timelineChanged = () -> {};
        timelineObservers.clear(); timelineHoverOwner = null; playheadHovered = false;
        simulationChanged = () -> {};
    }

    void onViewReady() {
        var expectedView = view;
        if (expectedView != null) expectedView.post(() -> {
            if (view == expectedView && viewReady()) synchronize();
        });
    }

    void dispose() {
        disposed = true;
        actionPreview.dispose();
        scenes.dispose();
        releaseView();
    }

    private final class PreviewActions implements DialogueUiActions {
        private volatile EditorPreviewSession session;
        PreviewActions(EditorPreviewSession session) { this.session = session; }

        @Override public DialogueScreenState viewState() {
            return session == null ? DialogueScreenState.empty(0) : session.state();
        }

        private void dispatch(Consumer<EditorPreviewSession> action) {
            var target = session;
            if (target == null) return;
            Core.getUiHandler().post(() -> {
                // Old animation, text and destruction callbacks must never operate on a restarted preview.
                if (disposed || loading || timeline.manual() || playback != target || !target.running()) return;
                action.accept(target);
                render();
            });
        }

        @Override public void advance() { dispatch(EditorPreviewSession::advance); }
        @Override public void skipToEnd() { /* The preview's skip icon is decorative. */ }
        @Override public void selectOption(DialogueOption option) {
            var target = session;
            Core.getUiHandler().post(() -> {
                if (disposed || loading || target == null || playback != target || !target.running()
                        || !canOperate() || source != workspace.draft() || !target.state().options().contains(option)) return;
                if (timeline.manual() || staleDialogueSession) {
                    // 显式点击才重新进入可播放会话，始终用最新草稿校验选项和命令。
                    startAt(workspace.resources().selection().stepIndex()); optionAfterLoad = option;
                } else { target.selectOption(option); render(); }
            });
        }
        @Override public void completePlayback(long generation, long token) {
            dispatch(current -> current.completeScene(generation, token));
        }
        @Override public void completeTextPlayback(long generation, long token) {
            dispatch(current -> current.completeText(generation, token));
        }
        private void audio(Consumer<EditorDialogueAudio> action) {
            var target = session;
            Core.getUiHandler().post(() -> {
                if (!disposed && !loading && !timeline.manual() && target != null && playback == target && target.running() && dialogueAudio != null)
                    action.accept(dialogueAudio);
            });
        }
        @Override public void audioFrame(long generation, long token, int elapsedMs) {
            var target = session;
            Core.getUiHandler().post(() -> {
                if (!disposed && !loading && playback == target && target != null && target.state().generation() == generation)
                    followTimeline(target, token, elapsedMs);
            });
            audio(audio -> audio.frame(generation, token, elapsedMs));
        }
        @Override public void textRevealed(long generation, long token, int end, boolean audible) { audio(audio -> audio.reveal(generation, token, end, audible)); }
        @Override public void closeFromUi() { dispatch(EditorPreviewSession::stop); }
        @Override public void onScreenDestroyed(DialogueScreenHandle screen) {
            dispatch(current -> { if (fragment == screen) current.stop(); });
        }
    }
}
