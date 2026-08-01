package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import top.rookiestwo.maimai_dialogue.dialogue.ColorAdjustFilter;
import top.rookiestwo.maimai_dialogue.dialogue.CrtFilter;
import top.rookiestwo.maimai_dialogue.dialogue.SceneFilter;

final class SceneFilterView extends FrameLayout {
    SceneFilterView(Context context) {
        super(context);
        setClickable(false);
    }

    // 根据场景 Filter 构建对应的覆盖层组合。
    void apply(SceneFilter filter) {
        removeAllViews();
        if (filter instanceof ColorAdjustFilter colorAdjust) {
            addColorAdjustOverlays(colorAdjust);
        } else if (filter instanceof CrtFilter crt) {
            addView(
                    new CrtOverlayView(getContext(), crt),
                    matchParentParams()
            );
        }
    }

    private void addColorAdjustOverlays(ColorAdjustFilter filter) {
        float neutralWash = Math.max(0.0F, 1.0F - filter.saturation())
                * 0.18F;
        neutralWash += Math.max(0.0F, 1.0F - filter.contrast())
                * 0.18F;
        addColorOverlay(0xFF808080, neutralWash);
        filter.tint().ifPresent(tint -> {
            int argb = tint.argb();
            addColorOverlay(
                    argb | 0xFF000000,
                    ((argb >>> 24) & 0xFF) / 255.0F
            );
        });
        float brightness = filter.brightness();
        addColorOverlay(
                brightness < 0.0F ? 0xFF000000 : 0xFFFFFFFF,
                Math.abs(brightness)
        );
        if (filter.contrast() > 1.0F) {
            addColorOverlay(
                    0xFF000000,
                    (filter.contrast() - 1.0F) * 0.08F
            );
        }
    }

    private void addColorOverlay(int rgb, float alpha) {
        int alphaByte = Math.round(Math.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        if (alphaByte == 0) {
            return;
        }
        View overlay = new View(getContext());
        overlay.setBackground(new ColorDrawable(
                (alphaByte << 24) | (rgb & 0x00FFFFFF)
        ));
        overlay.setClickable(false);
        addView(overlay, matchParentParams());
    }

    private static LayoutParams matchParentParams() {
        return new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        );
    }
}
