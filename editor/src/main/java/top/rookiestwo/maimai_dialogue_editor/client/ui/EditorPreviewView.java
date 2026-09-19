package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.HorizontalScrollView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.widget.ImageView;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import top.rookiestwo.maimai_dialogue.client.ui.layout.ResponsiveFrameLayout;

/** A centered, fitted 16:9 viewport hosts the runtime dialogue Fragment. */
final class EditorPreviewView extends ResponsiveFrameLayout {
    private final EditorPreviewHost host;
    private final HorizontalScrollView toolbar;
    private final EditorPreviewViewport canvas;
    private final EditorPreviewSurface surface;
    private final View notice;
    private final TextView message;
    private final Button advance;
    private final Button restart;
    private final Button stop;
    private final Button refreshMaterials;
    private final TextView materialStatus;
    private final ImageView materialImage;
    private EditorPreviewHost.ImagePreview displayedImage;
    private boolean hasImage;
    private int toolbarHeight;
    private int viewportWidth;
    private int viewportHeight;
    private boolean refreshContentPending;

    EditorPreviewView(Context context, EditorPreviewHost host, int containerId) {
        super(context);
        this.host = host;
        setBackground(EditorWidgets.shape(EditorWidgets.PREVIEW, 0));
        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        advance = control(context, controls, "preview.advance", host::advance);
        restart = control(context, controls, "preview.restart", host::start);
        stop = control(context, controls, "preview.stop", host::stop);
        refreshMaterials = control(context, controls, "material.refresh", host::refreshMaterials);
        materialStatus = EditorWidgets.label(context, "material.not_loaded", 12, EditorWidgets.MUTED);
        controls.addView(materialStatus);
        toolbar = new HorizontalScrollView(context);
        toolbar.setHorizontalScrollBarEnabled(false);
        toolbar.setBackground(EditorWidgets.shape(EditorWidgets.HEADER, 0));
        toolbar.addView(controls, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(toolbar);

        canvas = new EditorPreviewViewport(context);
        surface = new EditorPreviewSurface(context, containerId);
        canvas.addView(surface, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        materialImage = new ImageView(context);
        materialImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        canvas.addView(materialImage, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        message = EditorWidgets.paragraph(context, "preview.idle");
        message.setTextIsSelectable(true);
        notice = EditorWidgets.formScroll(context, message);
        EditorWidgets.bindMetrics(notice, () -> notice.setPadding(dp(10), dp(56), dp(10), dp(10)));
        canvas.addView(notice, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(canvas);
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

    void refresh() {
        EditorWidgets.enabled(refreshMaterials, host.canRefreshMaterials());
        materialStatus.setText(EditorWidgets.tr(host.materialStatus()));
        materialStatus.setTooltipText(host.materialError().isEmpty() ? materialStatus.getText() : host.materialError());
        boolean material = host.viewingMaterial();
        materialImage.setVisibility(material ? VISIBLE : GONE);
        surface.setVisibility(material ? GONE : VISIBLE);
        var nextImage = material ? host.imagePreview() : null;
        if (!java.util.Objects.equals(nextImage, displayedImage)) {
            displayedImage = nextImage; materialImage.setImage(null); hasImage = false;
            if (nextImage != null) {
                @SuppressWarnings("deprecation") Image image = Image.create(nextImage.namespace(), nextImage.path());
                materialImage.setImage(image); hasImage = image != null;
                if (materialImage.getDrawable() instanceof ImageDrawable drawable) drawable.setFilter(nextImage.linear());
            }
        }
        EditorWidgets.enabled(advance, host.canStart());
        EditorWidgets.enabled(restart, host.canStart() && host.running());
        EditorWidgets.enabled(stop, host.running());
        notice.setVisibility(material ? (hasImage ? GONE : VISIBLE) : host.running() || host.loading() ? GONE : VISIBLE);
        message.setText(material ? EditorWidgets.tr("material.preview_empty")
                : EditorWidgets.tr(host.message()) + (host.error().isEmpty() ? "" : "\n" + host.error()));
    }

    void setReferenceHeight(int height) {
        surface.setReferenceHeight(height);
    }

    void refreshContentAfterLayout() {
        if (!isAttachedToWindow()) return;
        refreshContentPending = true;
        requestLayout();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        prepareViewport(widthSpec, heightSpec);
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        toolbarHeight = Math.min(height, dp(EditorWidgets.COMPACT_ROW_DP));
        int availableHeight = Math.max(0, height - toolbarHeight);
        viewportWidth = (int) Math.min(width, availableHeight * 16L / 9L);
        viewportHeight = (int) Math.min(availableHeight, viewportWidth * 9L / 16L);
        EditorPanel.measureExact(toolbar, width, toolbarHeight);
        EditorPanel.measureExact(canvas, viewportWidth, viewportHeight);
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        toolbar.layout(0, 0, right - left, toolbarHeight);
        int x = (right - left - viewportWidth) / 2;
        int y = toolbarHeight + (bottom - top - toolbarHeight - viewportHeight) / 2;
        canvas.layout(x, y, x + viewportWidth, y + viewportHeight);
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

    @Override protected void onDetachedFromWindow() {
        materialImage.setImage(null); displayedImage = null; hasImage = false;
        refreshContentPending = false;
        super.onDetachedFromWindow();
    }
}
