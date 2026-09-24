package top.rookiestwo.maimai_dialogue_editor.client.ui.preview;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import top.rookiestwo.maimai_dialogue_editor.preview.AudioPreviewSession;
import java.util.Locale;

/** Transport controls for a selected sound file. The position is intentionally read-only. */
final class EditorAudioPreviewView extends FrameLayout {
    private final AudioPreviewSession audio;
    private final LinearLayout content;
    private final Button play, pause, stop;
    private final TextView time, error;
    private final Progress progress;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!isAttachedToWindow()) return;
            if (getVisibility() == VISIBLE) audio.tick();
            postDelayed(this, 100);
        }
    };

    EditorAudioPreviewView(Context context, AudioPreviewSession audio) {
        super(context); this.audio = audio;
        content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout buttons = new LinearLayout(context); buttons.setGravity(Gravity.CENTER);
        play = EditorWidgets.button(context, "audio.play", audio::play);
        pause = EditorWidgets.button(context, "audio.pause", audio::pause);
        stop = EditorWidgets.button(context, "audio.stop", audio::stopAndNotify);
        buttons.addView(play); buttons.addView(pause); buttons.addView(stop); content.addView(buttons);
        progress = new Progress(context); content.addView(progress);
        time = EditorWidgets.paragraph(context, ""); time.setGravity(Gravity.CENTER); content.addView(time);
        error = EditorWidgets.paragraph(context, ""); content.addView(error);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        EditorWidgets.bindMetrics(content, () -> {
            content.setPadding(dp(12), dp(8), dp(12), dp(8));
            progress.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(10)));
            for (Button button : new Button[]{play, pause, stop}) {
                button.setPadding(dp(12), 0, dp(12), 0);
                button.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(30)));
            }
        });
        refresh();
    }
    void refresh() {
        var state = audio.state();
        EditorWidgets.enabled(play, audio.available() && state != AudioPreviewSession.State.LOADING && state != AudioPreviewSession.State.PLAYING);
        EditorWidgets.enabled(pause, state == AudioPreviewSession.State.PLAYING);
        EditorWidgets.enabled(stop, state == AudioPreviewSession.State.LOADING || state == AudioPreviewSession.State.PLAYING
                || state == AudioPreviewSession.State.PAUSED || state == AudioPreviewSession.State.FINISHED);
        time.setText(format(audio.position()) + " / " + (audio.duration() > 0 ? format(audio.duration()) : "--:--"));
        error.setText(audio.error()); error.setVisibility(audio.error().isEmpty() ? GONE : VISIBLE);
        progress.fraction = audio.duration() > 0 ? (float)(audio.position() / audio.duration()) : 0;
        progress.invalidate();
    }
    private static String format(double seconds) {
        long whole = Math.max(0, (long)seconds);
        return String.format(Locale.ROOT, "%d:%02d", whole / 60, whole % 60);
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        content.getLayoutParams().width = Math.min(dp(380), MeasureSpec.getSize(widthSpec));
        super.onMeasure(widthSpec, heightSpec);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(ticker); }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(ticker); audio.stop(); super.onDetachedFromWindow();
    }
    private static final class Progress extends View {
        private final Paint paint = new Paint();
        float fraction;
        Progress(Context context) { super(context); setWillNotDraw(false); }
        @Override protected void onDraw(Canvas canvas) {
            int top = getHeight() / 3, bottom = getHeight() - top;
            paint.setColor(EditorWidgets.HEADER); canvas.drawRect(0, top, getWidth(), bottom, paint);
            paint.setColor(EditorWidgets.ACCENT); canvas.drawRect(0, top, getWidth() * Math.clamp(fraction, 0, 1), bottom, paint);
        }
    }
}
