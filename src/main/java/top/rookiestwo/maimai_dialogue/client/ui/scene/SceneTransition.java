package top.rookiestwo.maimai_dialogue.client.ui.scene;

import icyllis.modernui.widget.FrameLayout;

// 只负责新旧场景的持有、交叉淡化和释放，图片混合公式保持不变。
final class SceneTransition {
    private final FrameLayout host;
    private SceneContentView currentScene;
    private SceneContentView outgoingScene;
    private float outgoingCoverageOpacity;
    private boolean sceneTransitionPending;

    SceneTransition(FrameLayout host) {
        this.host = host;
    }

    SceneContentView current() {
        return currentScene;
    }

    boolean pending() {
        return sceneTransitionPending;
    }

    void begin(SceneContentView next, float coverage) {
        outgoingScene = currentScene;
        outgoingCoverageOpacity = coverage;
        currentScene = next;
        sceneTransitionPending = true;
    }

    void reset() {
        currentScene = null;
        outgoingScene = null;
        outgoingCoverageOpacity = 0.0F;
        sceneTransitionPending = false;
    }

    void apply(float progress) {
        SceneContentView incoming = currentScene;
        SceneContentView outgoing = outgoingScene;
        if (incoming == null && outgoing == null) {
            return;
        }
        float clamped = Math.clamp(progress, 0.0F, 1.0F);
        float outgoingFactor = 1.0F - clamped;
        float outgoingAlpha =
                outgoingCoverageOpacity * outgoingFactor;
        float denominator = 1.0F - outgoingAlpha;
        float incomingFactor = denominator <= 0.0001F
                ? 0.0F
                : Math.clamp(clamped / denominator, 0.0F, 1.0F);

        if (outgoing != null) {
            outgoing.setAlpha(outgoingFactor);
        }
        if (incoming != null) {
            incoming.setAlpha(incomingFactor);
        }

        if (clamped >= 0.9999F) {
            finish();
        }
    }

    void finish() {
        if (outgoingScene != null) {
            outgoingScene.releaseImages();
            host.removeView(outgoingScene);
            outgoingScene = null;
        }
        if (currentScene != null) {
            currentScene.setAlpha(1.0F);
        }
        outgoingCoverageOpacity = 0.0F;
        sceneTransitionPending = false;
    }

    void releaseImages() {
        for (int index = 0; index < host.getChildCount(); index++) {
            if (host.getChildAt(index) instanceof SceneContentView scene) {
                scene.releaseImages();
            }
        }
    }
}
