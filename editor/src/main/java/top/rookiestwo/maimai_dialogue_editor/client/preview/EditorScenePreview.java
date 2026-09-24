package top.rookiestwo.maimai_dialogue_editor.client.preview;

import icyllis.modernui.graphics.Image;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.scene.SceneState;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueImageSource;
import top.rookiestwo.maimai_dialogue.client.ui.screen.DialogueFragment;
import top.rookiestwo.maimai_dialogue_editor.client.EditorPreviewAssets;
import top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewSession;
import top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewFrame;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;
import java.util.*;

// Scene 的图片准备与已显示采样；加载替换期间保留原画面，View 只负责坐标与指针。
public final class EditorScenePreview {
    private final ProjectWorkspace workspace;
    private final EditorPreviewAssets assets;
    private final EditorStaticPreview presentation;
    private Runnable changed = () -> {};
    private DialogueImageSource pendingImages;
    private ScenePreviewSession.Prepared requested, displayed;
    private ScenePreviewSession session;
    private ScenePreviewFrame renderedFrame, baseFrame;
    private String loadError = "";
    private long revision;

    EditorScenePreview(ProjectWorkspace workspace, EditorPreviewAssets assets, EditorStaticPreview presentation) {
        this.workspace = workspace; this.assets = assets; this.presentation = presentation;
    }
    public void setListener(Runnable listener) { changed = listener; }
    public String error() { return session != null && !session.error().isEmpty() ? session.error() : loadError; }
    public DialogueFragment fragment() { return presentation.sceneFragment(); }
    public void refresh() {
        this.session = presentation.session();
        var next = session.prepared();
        if (next != requested) {
            requested = next; loadError = ""; long expected = ++revision;
            if (pendingImages != null) { pendingImages.close(); pendingImages = null; }
            if (next == null) clearRendered(false);
            else if (presentation.updateScene(next)) {
                displayed = next; baseFrame = renderedFrame = ScenePreviewFrame.initial(next.scene()); changed.run();
            } else {
                var images = assets.openImages(next.images()); pendingImages = images;
                var ids = ScenePreviewSession.initialImageIds(next.scene());
                Map<ResourceLocation, Image> loaded = new LinkedHashMap<>();
                int[] remaining = {ids.size()}; boolean[] failed = {false};
                if (ids.isEmpty()) publish(next, images, loaded);
                for (ResourceLocation id : ids) images.load(id, image -> {
                    if (expected != revision) return;
                    if (image == null) { failed[0] = true; loadError = net.minecraft.client.resources.language.I18n.get("gui.maimai_dialogue_editor.scene.missing_image") + " " + id; }
                    else loaded.put(id, image);
                    if (--remaining[0] == 0) {
                        if (!failed[0]) publish(next, images, loaded);
                        else { images.close(); pendingImages = null; changed.run(); }
                    }
                });
            }
        }
        changed.run();
        updatePosition();
    }
    private void publish(ScenePreviewSession.Prepared prepared, DialogueImageSource images, Map<ResourceLocation, Image> loaded) {
        // The Fragment owns a fork of preloaded handles, so a replacement never clears the old scene mid-load.
        try (var ready = new EditorReadyImages(loaded)) {
            if (presentation.showScene(prepared, ready)) {
                displayed = prepared; baseFrame = renderedFrame = ScenePreviewFrame.initial(prepared.scene()); changed.run();
            } else requested = null;
        } catch (RuntimeException failure) {
            loadError = String.valueOf(failure.getMessage()); changed.run();
        } finally { images.close(); pendingImages = null; }
    }
    public boolean canInteract() {
        return workspace.scenes().active() && workspace.windowFocused() && session != null && session.current()
                && displayed == session.prepared() && presentation.sceneFragment() != null && loadError.isEmpty();
    }
    public void updatePosition() {
        if (presentation.sceneFragment() == null || displayed == null) return;
        ScenePreviewFrame frame = baseFrame;
        var position = workspace.scenes().dragPosition();
        var number = workspace.scenes().numberPreview();
        // After release, retain the final position while its freshly committed snapshot is prepared.
        if (session != null && !session.current() && renderedFrame != null) {
            if (session.error().isEmpty()) return;
        }
        SceneState state = frame.state();
        if (position != null) {
            var object = state.objects().get(position.objectId());
            if (object != null) state = state.with(position.objectId(), object.withAnimated(position.x(), position.y(),
                    object.scale(), object.opacity(), object.variant(), object.visible()).withAxisScale(position.scaleX(), position.scaleY()));
        }
        frame = frame.withState(state);
        if (number != null) frame = frame.withNumber(number);
        if (!frame.equals(renderedFrame)) {
            renderedFrame = frame;
            presentation.renderSceneFrame(frame);
        }
    }
    private void clearRendered(boolean detaching) {
        if (!detaching) presentation.clearScenePreview();
        displayed = null; baseFrame = renderedFrame = null;
    }
    public void release(boolean detaching) {
        ++revision; requested = null; clearRendered(detaching); loadError = "";
        if (pendingImages != null) { pendingImages.close(); pendingImages = null; }
    }
}