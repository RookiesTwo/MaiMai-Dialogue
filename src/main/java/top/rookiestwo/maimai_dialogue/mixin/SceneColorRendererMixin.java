package top.rookiestwo.maimai_dialogue.mixin;

import icyllis.modernui.mc.UIManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.rookiestwo.maimai_dialogue.client.ui.scene.gpu.SceneColorRenderer;

/** Integrate the scene GPU tasks with the transformable Minecraft renderer's lifecycle. */
@Mixin(value = UIManager.class, remap = false)
public abstract class SceneColorRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void maimai$beginSceneFrame(CallbackInfo ci) { SceneColorRenderer.beginFrame(); }

    @Inject(method = "destroy", at = @At("HEAD"))
    private static void maimai$releaseSceneProgram(CallbackInfo ci) { SceneColorRenderer.shutdown(); }
}
