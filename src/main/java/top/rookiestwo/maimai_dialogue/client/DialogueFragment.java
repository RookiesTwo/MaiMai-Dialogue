package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.util.Objects;

public final class DialogueFragment extends Fragment implements ScreenCallback {
    private final ClientDialogueController controller;
    @Nullable
    private LinearLayout dialogueBox;

    public DialogueFragment(ClientDialogueController controller) {
        this.controller = controller;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable DataSet savedInstanceState
    ) {
        var context = Objects.requireNonNull(
                getContext(),
                "Fragment context"
        );
        FrameLayout root = new FrameLayout(context);
        root.setOnClickListener(view -> Minecraft.getInstance().execute(
                controller::advance
        ));

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int padding = box.dp(12);
        box.setPadding(padding, padding, padding, padding);

        ShapeDrawable background = new ShapeDrawable();
        background.setColor(0xCC101018);
        background.setCornerRadius(box.dp(8));
        box.setBackground(background);

        FrameLayout.LayoutParams boxParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        int margin = box.dp(20);
        boxParams.setMargins(margin, margin, margin, margin);
        root.addView(box, boxParams);

        dialogueBox = box;
        render(controller.viewState());
        return root;
    }

    public void render(DialogueViewState state) {
        LinearLayout box = dialogueBox;
        if (box == null) {
            return;
        }
        box.post(() -> {
            box.removeAllViews();

            state.speaker().ifPresent(speaker -> {
                TextView name = new TextView(box.getContext());
                name.setText(speaker);
                name.setTextSize(16);
                box.addView(name);
            });

            TextView text = new TextView(box.getContext());
            text.setText(state.text().orElse(""));
            text.setTextSize(16);
            box.addView(text);

            state.error().ifPresent(message -> {
                TextView error = new TextView(box.getContext());
                error.setText(message);
                error.setTextColor(0xFFFF8080);
                box.addView(error);
            });

            if (state.loadingOptions()) {
                TextView loading = new TextView(box.getContext());
                loading.setText("Loading options…");
                box.addView(loading);
            } else {
                for (var option : state.options()) {
                    Button button = new Button(box.getContext());
                    button.setText(option.text());
                    button.setEnabled(!state.requestingTarget());
                    button.setOnClickListener(view ->
                            Minecraft.getInstance().execute(
                                    () -> controller.selectOption(option)
                            )
                    );
                    box.addView(
                            button,
                            new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                    );
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        dialogueBox = null;
        Minecraft.getInstance().execute(
                () -> controller.onFragmentDestroyed(this)
        );
        super.onDestroy();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean hasDefaultBackground() {
        return false;
    }

    @Override
    public boolean shouldBlurBackground() {
        return false;
    }
}
