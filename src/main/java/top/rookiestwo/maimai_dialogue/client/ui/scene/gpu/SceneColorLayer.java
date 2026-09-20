package top.rookiestwo.maimai_dialogue.client.ui.scene.gpu;

import icyllis.arc3d.core.*;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.granite.GraniteImage;
import icyllis.arc3d.granite.GraniteSurface;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import top.rookiestwo.maimai_dialogue.presentation.filter.ColorAdjustFilter;
import java.util.function.Consumer;

/** UI-thread-owned scene surfaces. The filtered image stays in the normal Canvas draw order. */
public final class SceneColorLayer implements AutoCloseable {
    private GraniteSurface source, output;
    private GraniteImage result;
    private final icyllis.arc3d.sketch.Matrix transform = new icyllis.arc3d.sketch.Matrix();
    private int width, height;

    public static boolean isNeutral(ColorAdjustFilter value) {
        return value == null || value.brightness() == 0 && value.contrast() == 0 && value.saturation() == 0
                && value.tint().map(color -> color.argb() >>> 24 == 0).orElse(true);
    }

    public void draw(Canvas destination, int width, int height, ColorAdjustFilter settings, Consumer<Canvas> drawContent) {
        if (!(destination instanceof ArcCanvas arc)) throw new IllegalStateException("Scene color adjustment requires ArcCanvas");
        if (width <= 0 || height <= 0) return;
        arc.getCanvas().getLocalToDevice(transform);
        int pixelWidth = Math.max(1, (int) Math.ceil(width * Math.hypot(transform.getScaleX(), transform.getShearY())));
        int pixelHeight = Math.max(1, (int) Math.ceil(height * Math.hypot(transform.getShearX(), transform.getScaleY())));
        // Editor previews can use a larger logical design size. Allocate only their actual screen resolution.
        ensureSurfaces(pixelWidth, pixelHeight);
        var canvas = source.getCanvas();
        canvas.clear(0);
        int save = canvas.save();
        try {
            canvas.scale(pixelWidth / (float) width, pixelHeight / (float) height);
            drawContent.accept(new ArcCanvas(canvas));
        }
        finally { canvas.restoreToCount(save); }

        // Flush the scene and the output's initial clear BEFORE inserting our processing task.
        // The parent UI draw is recorded afterwards, so it samples this frame's completed result.
        source.flush(); output.flush();
        Core.requireUiRecordingContext().addTask(new SceneColorTask(source.getBackingTarget(), output.getBackingTarget(), settings));
        arc.getCanvas().drawImageRect(result, new Rect2f(0, 0, pixelWidth, pixelHeight), new Rect2f(0, 0, width, height),
                SamplingOptions.NEAREST, null, icyllis.arc3d.sketch.Canvas.SRC_RECT_CONSTRAINT_FAST);
    }

    private void ensureSurfaces(int requestedWidth, int requestedHeight) {
        if (source != null && width == requestedWidth && height == requestedHeight) return;
        close();
        var context = Core.requireUiRecordingContext();
        var info = ImageInfo.make(requestedWidth, requestedHeight, ColorInfo.CT_RGBA_8888, ColorInfo.AT_PREMUL, ColorSpaces.SRGB);
        source = GraniteSurface.makeRenderTarget(context, info, false, Engine.SurfaceOrigin.kLowerLeft, "MaiMaiSceneSource");
        output = GraniteSurface.makeRenderTarget(context, info, false, Engine.SurfaceOrigin.kLowerLeft, "MaiMaiSceneFiltered");
        if (source == null || output == null) { close(); throw new IllegalStateException("Cannot allocate scene color surfaces"); }
        result = new GraniteImage(context, RefCnt.create(output.getReadSurfaceView()), info.colorType(), info.alphaType(), info.colorSpace());
        width = requestedWidth; height = requestedHeight;
    }

    @Override public void close() {
        result = RefCnt.move(result);
        source = RefCnt.move(source); output = RefCnt.move(output);
        width = height = 0;
    }
}
