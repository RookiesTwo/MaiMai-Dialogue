package top.rookiestwo.maimai_dialogue_editor.client.ui.preview;

import top.rookiestwo.maimai_dialogue_editor.client.preview.EditorPreviewHost;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.ChoicePresenter;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.HorizontalScrollView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.widget.ImageView;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueImageSource;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.ui.layout.ResponsiveFrameLayout;

/** Type-specific preview: 16:9 dialogue, image-sized transparency canvas, or sound transport. */
public final class EditorPreviewView extends ResponsiveFrameLayout implements top.rookiestwo.maimai_dialogue_editor.client.preview.PreviewDisplay {
    private final EditorPreviewHost host;
    private final HorizontalScrollView toolbar;
    private final EditorPreviewViewport canvas;
    private final EditorPreviewSurface surface;
    private final View notice;
    private final TextView message;
    private final Button advance;
    private final Button restart;
    private final Button stop;
    private final Button[] themeExamples;
    private final TextView themeError;
    private final ImageView materialImage;
    private final EditorAudioPreviewView audio;
    private final EditorScenePreviewView scene;
    private final EditorActionCanvas actionCanvas;
    private final EditorActionPreviewControls actionControls;
    private EditorPreviewHost.Mode mode = EditorPreviewHost.Mode.EMPTY;
    private int imageWidth, imageHeight;
    private EditorPreviewHost.ImagePreview displayedImage;
    private DialogueImageSource imageSource;
    private DialogueImageSource pendingImageSource;
    private long imageRequest;
    private int toolbarHeight;
    private int viewportWidth;
    private int viewportHeight;
    private boolean refreshContentPending;

    public EditorPreviewView(Context context, EditorPreviewHost host, int containerId, ChoicePresenter choices) {
        super(context);
        this.host = host;
        setBackground(EditorWidgets.shape(EditorWidgets.PREVIEW, 0));
        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        advance = control(context, controls, "preview.advance", host::advance);
        restart = control(context, controls, "preview.restart", host::start);
        stop = control(context, controls, "preview.stop", host::stop);
        themeExamples = new Button[3];
        String[] examples = {"theme.example_text", "theme.example_options", "theme.example_error"};
        for (int index = 0; index < examples.length; index++) {
            int example = index;
            themeExamples[index] = control(context, controls, examples[index], () -> host.themeExample(example));
        }
        toolbar = new HorizontalScrollView(context);
        toolbar.setHorizontalScrollBarEnabled(false);
        toolbar.setBackground(EditorWidgets.shape(EditorWidgets.HEADER, 0));
        toolbar.addView(controls, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(toolbar);
        actionControls = new EditorActionPreviewControls(context, host, choices);
        addView(actionControls);

        canvas = new EditorPreviewViewport(context);
        surface = new EditorPreviewSurface(context, containerId);
        canvas.addView(surface, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        materialImage = new ImageView(context);
        materialImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        canvas.addView(materialImage, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        scene = new EditorScenePreviewView(context, host, surface);
        canvas.addView(scene, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        actionCanvas = new EditorActionCanvas(context, host, surface);
        canvas.addView(actionCanvas, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        themeError = EditorWidgets.compactParagraph(context, "");
        themeError.setTextColor(EditorWidgets.ERROR);
        canvas.addView(themeError, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        message = EditorWidgets.paragraph(context, "preview.idle");
        message.setTextIsSelectable(true);
        notice = EditorWidgets.formScroll(context, message);
        EditorWidgets.bindMetrics(notice, () -> notice.setPadding(dp(10), dp(56), dp(10), dp(10)));
        canvas.addView(notice, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(canvas);
        audio = new EditorAudioPreviewView(context, host.audio());
        addView(audio);
        refresh();
    }

    private Button control(Context context, LinearLayout controls, String key, Runnable action) {
        Button button = EditorWidgets.button(context, key, action);
        button.setTooltipText(EditorWidgets.tr(key));
        controls.addView(button);
        EditorWidgets.bindMetrics(button, () -> {
            button.setPadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0,
                    dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0);
            button.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(EditorWidgets.COMPACT_ROW_DP)));
        });
        return button;
    }

    @Override public View root() { return this; }

    @Override public void refresh() {
        var nextMode = host.mode();
        if (mode != nextMode) { mode = nextMode; requestLayout(); }
        boolean material = mode == EditorPreviewHost.Mode.IMAGE;
        boolean dialogue = mode == EditorPreviewHost.Mode.DIALOGUE;
        boolean theme = mode == EditorPreviewHost.Mode.THEME;
        boolean action = mode == EditorPreviewHost.Mode.ACTION;
        actionControls.setVisibility(action ? VISIBLE : GONE);
        if (action) actionControls.refresh();
        toolbar.setVisibility(dialogue || theme ? VISIBLE : GONE);
        for (var control : new Button[]{advance, restart, stop}) control.setVisibility(dialogue ? VISIBLE : GONE);
        for (int index = 0; index < themeExamples.length; index++) {
            themeExamples[index].setVisibility(theme ? VISIBLE : GONE);
            EditorWidgets.enabled(themeExamples[index], index != host.themeExample());
        }
        canvas.setVisibility(material || dialogue || theme || action || mode == EditorPreviewHost.Mode.SCENE ? VISIBLE : GONE);
        scene.setVisibility(mode == EditorPreviewHost.Mode.SCENE ? VISIBLE : GONE);
        if (mode == EditorPreviewHost.Mode.SCENE) scene.refresh(host.scenes()); else scene.release();
        actionCanvas.setVisibility(dialogue || action ? VISIBLE : GONE);
        actionCanvas.synchronize();
        if (theme) host.refreshTheme();
        String themeIssue = theme ? host.themeError() : action ? host.actionPreview().error() : "";
        themeError.setText(themeIssue.isEmpty() ? "" : (theme ? EditorWidgets.tr("theme.invalid") + " " : "") + themeIssue);
        themeError.setVisibility(themeIssue.isEmpty() ? GONE : VISIBLE);
        audio.setVisibility(mode == EditorPreviewHost.Mode.SOUND ? VISIBLE : GONE);
        audio.refresh();
        materialImage.setVisibility(material ? VISIBLE : GONE);
        surface.setVisibility(dialogue || theme || action || mode == EditorPreviewHost.Mode.SCENE ? VISIBLE : GONE);
        var nextImage = material ? host.imagePreview() : null;
        if (!java.util.Objects.equals(nextImage, displayedImage)) {
            var previous = displayedImage;
            displayedImage = nextImage;
            long request = ++imageRequest;
            if (pendingImageSource != null) { pendingImageSource.close(); pendingImageSource = null; }
            boolean sameImage = previous != null && nextImage != null
                    && previous.namespace().equals(nextImage.namespace()) && previous.path().equals(nextImage.path());
            if (!sameImage) {
                materialImage.setImage(null);
                imageWidth = imageHeight = 0; requestLayout();
                if (imageSource != null) { imageSource.close(); imageSource = null; }
            }
            if (nextImage != null) {
                pendingImageSource = host.openImages();
                pendingImageSource.load(ResourceLocation.fromNamespaceAndPath(nextImage.namespace(), nextImage.path()), image -> {
                    if (request == imageRequest) {
                        materialImage.setImage(image);
                        imageWidth = image == null ? 0 : image.getWidth();
                        imageHeight = image == null ? 0 : image.getHeight();
                        requestLayout();
                        if (imageSource != null) imageSource.close();
                        imageSource = pendingImageSource;
                        pendingImageSource = null;
                        if (materialImage.getDrawable() instanceof ImageDrawable drawable) drawable.setFilter(nextImage.linear());
                        refresh();
                    }
                });
            }
        }
        EditorWidgets.enabled(advance, host.canStart());
        canvas.setImageBounds(material, imageWidth, imageHeight);
        EditorWidgets.enabled(restart, host.canStart() && host.running());
        EditorWidgets.enabled(stop, host.running());
        // A pending image is just the canvas; asset failures remain available in the workbench status bar.
        notice.setVisibility(!dialogue || host.running() || host.loading() ? GONE : VISIBLE);
        message.setText(!dialogue ? "" : EditorWidgets.tr(host.message()) + (host.error().isEmpty() ? "" : "\n" + host.error()));
        message.setTextColor(EditorWidgets.MUTED);
    }

    @Override public void setReferenceHeight(int height) {
        surface.setReferenceHeight(height);
        scene.setReferenceHeight(height);
    }
    @Override public void finishSceneDrag(boolean commit) { scene.endDrag(commit); actionCanvas.finish(commit); }
    @Override public void refreshActionCanvas() { actionCanvas.synchronize(); }
    @Override public void requestSceneFrame(boolean immediate) { if (mode == EditorPreviewHost.Mode.SCENE) scene.requestFrame(immediate); }

    @Override public void refreshContentAfterLayout() {
        if (!isAttachedToWindow()) return;
        refreshContentPending = true;
        requestLayout();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        prepareViewport(widthSpec, heightSpec);
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        toolbarHeight = mode == EditorPreviewHost.Mode.DIALOGUE || mode == EditorPreviewHost.Mode.THEME
                ? Math.min(height, dp(EditorWidgets.COMPACT_ROW_DP)) : mode == EditorPreviewHost.Mode.ACTION ? Math.min(height, dp(52)) : 0;
        int availableHeight = Math.max(0, height - toolbarHeight);
        if (mode == EditorPreviewHost.Mode.IMAGE) {
            viewportWidth = width; viewportHeight = availableHeight;
        } else {
            viewportWidth = (int)Math.min(width, availableHeight * 16L / 9L);
            viewportHeight = (int)Math.min(availableHeight, viewportWidth * 9L / 16L);
        }
        EditorWidgets.measureExact(toolbar, width, toolbarHeight);
        EditorWidgets.measureExact(actionControls, width, toolbarHeight);
        EditorWidgets.measureExact(canvas, viewportWidth, viewportHeight);
        EditorWidgets.measureExact(audio, width, height);
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        toolbar.layout(0, 0, right - left, toolbarHeight);
        actionControls.layout(0, 0, right - left, toolbarHeight);
        int x = (right - left - viewportWidth) / 2;
        int y = toolbarHeight + (bottom - top - toolbarHeight - viewportHeight) / 2;
        canvas.layout(x, y, x + viewportWidth, y + viewportHeight);
        audio.layout(0, 0, right - left, bottom - top);
        restoreScrollPositions();
        if (refreshContentPending) {
            refreshContentPending = false;
            // The released pointer's final panel bounds have now reached the 16:9 viewport.
            host.refreshViewport(this);
        }
    }

    @Override protected void onViewportChanged(boolean densityChanged) {
        if (densityChanged) EditorWidgets.refreshMetrics(this);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Project restoration may finish before this View attaches. Retry from readiness, not a synthetic tree click.
        host.onViewReady();
    }

    @Override protected void onDetachedFromWindow() {
        ++imageRequest;
        materialImage.setImage(null); displayedImage = null;
        imageWidth = imageHeight = 0;
        if (imageSource != null) { imageSource.close(); imageSource = null; }
        if (pendingImageSource != null) { pendingImageSource.close(); pendingImageSource = null; }
        refreshContentPending = false;
        super.onDetachedFromWindow();
    }
}
