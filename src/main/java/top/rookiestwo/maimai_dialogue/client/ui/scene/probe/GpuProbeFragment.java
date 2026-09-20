package top.rookiestwo.maimai_dialogue.client.ui.scene.probe;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.bootstrap.ClientServices;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueSceneView;
import top.rookiestwo.maimai_dialogue.client.ui.scene.gpu.SceneColorRenderer;
import top.rookiestwo.maimai_dialogue.client.scene.SceneState;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.content.resolve.VisualAssetResolver;
import top.rookiestwo.maimai_dialogue.presentation.Presentation;
import top.rookiestwo.maimai_dialogue.presentation.DialogueBoxLayout;
import top.rookiestwo.maimai_dialogue.presentation.filter.ColorAdjustFilter;
import top.rookiestwo.maimai_dialogue.presentation.filter.CrtFilter;

/** A disposable diagnostic screen, deliberately separate from the editor's production viewport. */
public final class GpuProbeFragment extends Fragment implements ScreenCallback {
    private GpuColorProbe.Session session;
    private DialogueSceneView scene;
    private final long startNanos = System.nanoTime();
    private TextView timing;
    private final boolean crtMode;
    private final SeekBar[] sliders;
    private float[] crtDefaults;
    private float[] crtValues;

    public GpuProbeFragment() { this(false); }
    public GpuProbeFragment(boolean crtMode) {
        this.crtMode = crtMode;
        sliders = new SeekBar[crtMode ? 8 : 3];
    }
    private final Runnable updateTiming = new Runnable() {
        @Override public void run() {
            if (timing == null || session == null) return;
            applySettings();
            timing.setText(SceneColorRenderer.samples == 0 ? tr("waiting")
                    : String.format(Locale.ROOT, "%s %.3f ms | %s %d", tr("gpu_time"), SceneColorRenderer.gpuMicros / 1000, tr("samples"), SceneColorRenderer.samples));
            timing.postDelayed(this, session.settings.animate() ? 16 : 100);
        }
    };

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        Context context = requireContext();
        session = GpuColorProbe.open();
        if (!crtMode && "1".equals(System.getenv("MAIMAI_GPU_PROBE"))) {
            var s = session.settings;
            session.settings = new GpuColorProbe.Settings(s.brightness(), s.contrast(), s.saturation(), true, true);
        }
        var root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL);
        var toolbar = new LinearLayout(context); background(toolbar);
        var title = label(context, tr(crtMode ? "crt_title" : "title"));
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(context, 36), 1));
        var enabled = button(context, tr("bypass"));
        enabled.setOnClickListener(view -> {
            var s = session.settings;
            session.settings = new GpuColorProbe.Settings(s.brightness(), s.contrast(), s.saturation(), !s.enabled(), s.animate());
            enabled.setText(tr(s.enabled() ? "enable" : "bypass"));
        });
        toolbar.addView(enabled);
        var animate = button(context, tr(session.settings.animate() ? "stop_animation" : "animate"));
        animate.setOnClickListener(view -> {
            var s = session.settings;
            session.settings = new GpuColorProbe.Settings(s.brightness(), s.contrast(), s.saturation(), s.enabled(), !s.animate());
            animate.setText(tr(s.animate() ? "animate" : "stop_animation"));
        });
        if (!crtMode) toolbar.addView(animate);
        var reset = button(context, tr("reset"));
        reset.setOnClickListener(view -> {
            session.settings = new GpuColorProbe.Settings(0, 0, 0, true, false);
            if (crtMode) {
                crtValues = crtDefaults.clone();
                for (int i = 0; i < sliders.length; i++) sliders[i].setProgress(Math.round(crtValues[i] * 1000));
            } else for (var slider : sliders) slider.setProgress(100);
            enabled.setText(tr("bypass")); animate.setText(tr("animate"));
        });
        toolbar.addView(reset);
        var close = button(context, "×"); close.setOnClickListener(view -> close()); toolbar.addView(close);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        var viewport = new FrameLayout(context);
        viewport.addView(new ProbeArea(context), new FrameLayout.LayoutParams(-1, -1));
        var overlay = label(context, tr("overlay")); background(overlay);
        var overlayParams = new FrameLayout.LayoutParams(-2, dp(context, 32), Gravity.CENTER);
        viewport.addView(overlay, overlayParams);
        root.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
        var controls = new LinearLayout(context); controls.setOrientation(LinearLayout.VERTICAL); background(controls);
        String[] names = crtMode
                ? new String[]{"curvature", "scanlines", "mask", "aberration", "vignette", "noise", "flicker", "bloom"}
                : new String[]{"brightness", "contrast", "saturation"};
        for (int i = 0; i < names.length; i++) {
            int index = i;
            var row = new LinearLayout(context);
            float initial = crtMode ? crtValues[i] : i == 0 ? session.settings.brightness() : i == 1 ? session.settings.contrast() : session.settings.saturation();
            var name = label(context, tr(names[i]) + " " + initial); row.addView(name, new LinearLayout.LayoutParams(dp(context, 160), -1));
            var slider = new SeekBar(context); sliders[i] = slider;
            slider.setMax(crtMode ? i == 3 ? 4000 : 1000 : 200);
            slider.setProgress(crtMode ? Math.round(initial * 1000) : 100 + Math.round(initial));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    float value = crtMode ? progress / 1000f : progress - 100;
                    name.setText(tr(names[index]) + " " + value);
                    if (!fromUser) return;
                    if (crtMode) { crtValues[index] = value; applySettings(); return; }
                    var s = session.settings;
                    session.settings = new GpuColorProbe.Settings(index == 0 ? value : s.brightness(),
                            index == 1 ? value : s.contrast(), index == 2 ? value : s.saturation(), s.enabled(), s.animate());
                }
                @Override public void onStartTrackingTouch(SeekBar bar) { }
                @Override public void onStopTrackingTouch(SeekBar bar) { }
            });
            row.addView(slider, new LinearLayout.LayoutParams(0, -1, 1));
            controls.addView(row, new LinearLayout.LayoutParams(-1, dp(context, crtMode ? 24 : 32)));
        }
        timing = label(context, tr("waiting")); controls.addView(timing, new LinearLayout.LayoutParams(-1, dp(context, 28)));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        timing.post(updateTiming);
        return root;
    }

    private void applySettings() {
        if (scene == null) return;
        var settings = session.settings;
        if (crtMode) {
            scene.setSceneFilter(settings.enabled() ? new CrtFilter(crtValues[0], crtValues[1], crtValues[2], crtValues[3],
                    crtValues[4], crtValues[5], crtValues[6], crtValues[7]) : null);
            return;
        }
        float saturation = settings.animate() ? (float) (100 * Math.sin((System.nanoTime() - startNanos) / 1_000_000_000.0)) : settings.saturation();
        scene.setColorAdjustment(settings.enabled()
                ? new ColorAdjustFilter(settings.brightness(), settings.contrast(), saturation, Optional.empty()) : null);
    }

    private final class ProbeArea extends FrameLayout {
        private final Paint paint = new Paint();
        ProbeArea(Context context) {
            super(context); setWillNotDraw(false);
            scene = new DialogueSceneView(context);
            var content = ClientServices.get().content().current();
            var definition = content.scenes().find(ResourceLocation.fromNamespaceAndPath("maimai_dialogue", crtMode ? "debug/crt" : "debug/root")).orElseThrow();
            if (crtMode) {
                var crt = (CrtFilter) definition.filter().orElseThrow();
                crtDefaults = new float[]{crt.curvature(), crt.scanlineStrength(), crt.maskStrength(), crt.chromaticAberration(),
                        crt.vignette(), crt.noise(), crt.flicker(), crt.bloom()};
                crtValues = crtDefaults.clone();
            }
            var presentation = new Presentation(Presentation.DEFAULT_THEME_ID, definition.background(), DialogueBoxLayout.DEFAULT,
                    definition.visualObjects(), definition.filter());
            presentation = VisualAssetResolver.resolve(presentation, content.visualAssets()::find).presentation();
            scene.apply(presentation);
            var state = SceneState.initial(presentation);
            scene.renderPlayback(new ScenePlayback(0, state, state, List.of(), 0, 0), true, () -> {});
            addView(scene, new LayoutParams(-1, -1));
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            int width = Math.min(getMeasuredWidth(), Math.round(getMeasuredHeight() * 16f / 9));
            int height = Math.round(width * 9f / 16);
            scene.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int x = (getWidth() - scene.getMeasuredWidth()) / 2, y = (getHeight() - scene.getMeasuredHeight()) / 2;
            scene.layout(x, y, x + scene.getMeasuredWidth(), y + scene.getMeasuredHeight());
        }
        @Override protected void onDraw(@NonNull Canvas canvas) {
            int size = Math.max(1, dp(16));
            for (int y = scene.getTop(), row = 0; y < scene.getBottom(); y += size, row++) {
                for (int x = scene.getLeft(), col = 0; x < scene.getRight(); x += size, col++) {
                    paint.setColor((row + col) % 2 == 0 ? 0xffffffff : 0xffdcdfe3);
                    canvas.drawRect(x, y, Math.min(x + size, scene.getRight()), Math.min(y + size, scene.getBottom()), paint);
                }
            }
        }
    }

    private static TextView label(Context context, String text) {
        var view = new TextView(context); view.setText(text); view.setTextSize(14); view.setTextColor(0xff263447);
        view.setGravity(Gravity.CENTER_VERTICAL); view.setPadding(dp(context, 8), 0, dp(context, 8), 0); return view;
    }
    private static Button button(Context context, String text) {
        var button = new Button(context); button.setText(text); button.setTextColor(0xff008cff); background(button); return button;
    }
    private static void background(View view) {
        var drawable = new ShapeDrawable(); drawable.setColor(0xfff1f3f5); drawable.setCornerRadius(0); view.setBackground(drawable);
    }
    private static int dp(Context context, int value) { return Math.round(context.getResources().getDisplayMetrics().density * value); }
    private static String tr(String key) { return I18n.get("gui.maimai_dialogue.gpu_probe." + key); }
    private void close() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof icyllis.modernui.mc.MuiScreen screen && screen.getFragment() == this)
                minecraft.setScreen(screen.getPreviousScreen());
        });
    }
    @Override public void onDestroyView() {
        if (timing != null) timing.removeCallbacks(updateTiming);
        timing = null;
        if (scene != null) { scene.clearScene(); scene = null; }
        if (session != null) GpuColorProbe.close(session);
        super.onDestroyView();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean hasDefaultBackground() { return false; }
    @Override public boolean shouldBlurBackground() { return false; }
}
