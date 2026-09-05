package top.rookiestwo.maimai_dialogue.client.session;

// phase 从完成状态推导，避免不同操作遗漏同步其中一个标记。
final class StepPlaybackState {
    private boolean sceneComplete = true;
    private boolean textComplete = true;
    private boolean skipped;

    void begin(boolean sceneComplete, boolean textComplete) {
        this.sceneComplete = sceneComplete;
        this.textComplete = textComplete;
        skipped = false;
    }

    PlaybackPhase phase() {
        return sceneComplete && textComplete ? PlaybackPhase.READY : PlaybackPhase.PLAYING;
    }

    boolean skipped() {
        return skipped;
    }

    boolean completeScene() {
        boolean changed = !sceneComplete;
        sceneComplete = true;
        return changed;
    }

    boolean completeText() {
        boolean changed = !textComplete;
        textComplete = true;
        return changed;
    }

    void skip() {
        sceneComplete = true;
        textComplete = true;
        skipped = true;
    }
}
