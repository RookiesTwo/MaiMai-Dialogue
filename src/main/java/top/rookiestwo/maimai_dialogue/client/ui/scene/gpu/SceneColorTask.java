package top.rookiestwo.maimai_dialogue.client.ui.scene.gpu;

import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.engine.CommandBuffer;
import icyllis.arc3d.engine.ImageProxy;
import icyllis.arc3d.engine.ImmediateContext;
import icyllis.arc3d.granite.RecordingContext;
import icyllis.arc3d.granite.task.Task;
import icyllis.arc3d.opengl.GLCommandBuffer;
import icyllis.arc3d.opengl.GLDevice;
import icyllis.arc3d.opengl.GLTexture;
import top.rookiestwo.maimai_dialogue.presentation.filter.SceneFilter;

/** Ordered GPU filtering between the scene draw and its parent UI draw; owns all proxies until submitted. */
final class SceneColorTask extends Task {
    private ImageProxy source, output, bloomA, bloomB;
    private final SceneFilter settings;
    private final float time;

    SceneColorTask(ImageProxy source, ImageProxy output, ImageProxy bloomA, ImageProxy bloomB, SceneFilter settings, float time) {
        this.source = RefCnt.create(source); this.output = RefCnt.create(output); this.settings = settings;
        this.bloomA = RefCnt.create(bloomA); this.bloomB = RefCnt.create(bloomB); this.time = time;
    }

    @Override public int prepare(RecordingContext context) {
        return source.instantiateIfNonLazy(context.getResourceProvider()) && output.instantiateIfNonLazy(context.getResourceProvider())
                && (bloomA == null || bloomA.instantiateIfNonLazy(context.getResourceProvider()))
                && (bloomB == null || bloomB.instantiateIfNonLazy(context.getResourceProvider()))
                ? RESULT_SUCCESS : RESULT_FAILURE;
    }

    @Override public int execute(ImmediateContext context, CommandBuffer commandBuffer) {
        if (!(commandBuffer instanceof GLCommandBuffer) || !(context.getDevice() instanceof GLDevice device)) return RESULT_FAILURE;
        device.flushRenderCalls();
        if (!(source.getImage() instanceof GLTexture src) || !(output.getImage() instanceof GLTexture dst)) return RESULT_FAILURE;
        SceneColorRenderer.draw(src, dst, bloomA == null ? null : (GLTexture) bloomA.getImage(),
                bloomB == null ? null : (GLTexture) bloomB.getImage(), settings, time);
        // Keep the backing images alive until the GPU command buffer has completed.
        commandBuffer.trackCommandBufferResource(source.refImage());
        commandBuffer.trackCommandBufferResource(output.refImage());
        if (bloomA != null) commandBuffer.trackCommandBufferResource(bloomA.refImage());
        if (bloomB != null) commandBuffer.trackCommandBufferResource(bloomB.refImage());
        return RESULT_SUCCESS;
    }

    @Override protected void deallocate() {
        source = RefCnt.move(source); output = RefCnt.move(output);
        bloomA = RefCnt.move(bloomA); bloomB = RefCnt.move(bloomB);
    }
}
