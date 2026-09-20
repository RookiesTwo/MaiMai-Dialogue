package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.widget.*;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.scene.*;
import top.rookiestwo.maimai_dialogue.client.ui.scene.*;
import top.rookiestwo.maimai_dialogue_editor.client.EditorPreviewAssets;
import top.rookiestwo.maimai_dialogue_editor.preview.ScenePreviewSession;
import java.util.*;

/** Static authoring viewport rendered by the gameplay Scene View. No playback controls or side effects. */
final class EditorScenePreviewView extends FrameLayout {
    private final EditorPreviewAssets assets;
    private final TextView error;
    private EditorPreviewSurface surface;
    private DialogueSceneView renderer;
    private DialogueImageSource pendingImages;
    private ScenePreviewSession.Prepared requested;
    private String loadError = "";
    private long revision;
    private int referenceHeight = 1;

    EditorScenePreviewView(Context context, EditorPreviewAssets assets) {
        super(context); this.assets = assets;
        error = EditorWidgets.paragraph(context, ""); error.setTextColor(EditorWidgets.ERROR);
        error.setMaxLines(4); error.setVisibility(GONE);
        EditorWidgets.bindMetrics(error, () -> error.setPadding(dp(8), dp(6), dp(8), dp(6)));
        addView(error, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
    void refresh(ScenePreviewSession session) {
        var next = session.prepared();
        if (next != requested) {
            requested = next; loadError = ""; long expected = ++revision;
            if (pendingImages != null) { pendingImages.close(); pendingImages = null; }
            if (next == null) clearRendered();
            else {
                var images = assets.openImages(next.images()); pendingImages = images;
                var ids = new java.util.LinkedHashSet<ResourceLocation>();
                next.presentation().background().ifPresent(background -> ids.add(background.initialImage()));
                next.presentation().visualObjects().values().forEach(object -> ids.add(object.initialImage()));
                Map<ResourceLocation, Image> loaded = new LinkedHashMap<>();
                int[] remaining = {ids.size()}; boolean[] failed = {false};
                if (ids.isEmpty()) publish(next, images, loaded);
                for (ResourceLocation id : ids) images.load(id, image -> {
                    if (expected != revision) return;
                    if (image == null) { failed[0] = true; loadError = EditorWidgets.tr("scene.missing_image") + " " + id; }
                    else loaded.put(id, image);
                    if (--remaining[0] == 0) {
                        if (!failed[0]) publish(next, images, loaded);
                        else { images.close(); pendingImages = null; showError(session.error()); }
                    }
                });
            }
        }
        showError(session.error());
    }
    private void publish(ScenePreviewSession.Prepared prepared, DialogueImageSource images, Map<ResourceLocation, Image> loaded) {
        // Keep preloaded handles alive even when this Scene exceeds the shared image-cache budget.
        try (var ready = new ReadyImages(loaded)) {
            DialogueSceneView next = new DialogueSceneView(getContext(), ready);
            try {
                next.apply(prepared.presentation());
                var initial = SceneState.initial(prepared.presentation());
                next.renderPlayback(new ScenePlayback(0, initial, initial, List.of(), 0, 0), true, () -> {});
            } catch (RuntimeException failure) {
                next.clearScene(); loadError = String.valueOf(failure.getMessage()); showError(""); return;
            }
            clearRendered(); renderer = next;
            surface = new EditorPreviewSurface(getContext(), renderer); surface.setReferenceHeight(referenceHeight);
            addView(surface, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } finally { images.close(); pendingImages = null; }
    }
    private static final class ReadyImages implements DialogueImageSource {
        private final Map<ResourceLocation, Image> images = new LinkedHashMap<>();
        ReadyImages(Map<ResourceLocation, Image> source) { source.forEach((id, image) -> images.put(id, image.clone())); }
        @Override public DialogueImageSource fork() { return new ReadyImages(images); }
        @Override public void load(ResourceLocation id, java.util.function.Consumer<Image> ready) { ready.accept(images.get(id)); }
        @Override public void close() { images.values().forEach(Image::close); images.clear(); }
    }
    private void showError(String preparationError) {
        String message = preparationError.isEmpty() ? loadError : preparationError;
        error.setText(message); error.setTooltipText(message); error.setVisibility(message.isEmpty() ? GONE : VISIBLE);
    }
    void setReferenceHeight(int height) { referenceHeight = height; if (surface != null) surface.setReferenceHeight(height); }
    private void clearRendered() {
        if (renderer != null) renderer.clearScene(); renderer = null;
        if (surface != null) removeView(surface); surface = null;
    }
    void release() {
        ++revision; requested = null; clearRendered(); loadError = "";
        if (pendingImages != null) { pendingImages.close(); pendingImages = null; }
    }
    @Override protected void onDetachedFromWindow() { release(); super.onDetachedFromWindow(); }
}
