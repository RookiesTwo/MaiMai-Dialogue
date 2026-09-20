package top.rookiestwo.maimai_dialogue.client.ui.scene.probe;

import top.rookiestwo.maimai_dialogue.client.ui.scene.gpu.SceneColorRenderer;

/** Controls for the diagnostic screen, which now exercises the production DialogueSceneView. */
public final class GpuColorProbe {
    public record Settings(float brightness, float contrast, float saturation, boolean enabled, boolean animate) { }
    public static final class Session {
        public Settings settings = new Settings(-3, 8, -45, true, false);
    }
    private GpuColorProbe() { }
    public static Session open() { SceneColorRenderer.diagnostics(true); return new Session(); }
    public static void close(Session session) { SceneColorRenderer.diagnostics(false); }
}
