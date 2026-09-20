package top.rookiestwo.maimai_dialogue.client.ui.scene;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.FrameLayout;
import top.rookiestwo.maimai_dialogue.presentation.filter.CrtFilter;
import top.rookiestwo.maimai_dialogue.presentation.filter.SceneFilter;

final class SceneFilterView extends FrameLayout {
    SceneFilterView(Context context) {
        super(context);
        setClickable(false);
    }

    // 根据场景 Filter 构建对应的覆盖层组合。
    void apply(SceneFilter filter) {
        removeAllViews();
        if (filter instanceof CrtFilter crt) {
            addView(
                    new CrtOverlayView(getContext(), crt),
                    matchParentParams()
            );
        }
    }

    private static LayoutParams matchParentParams() {
        return new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        );
    }
}
