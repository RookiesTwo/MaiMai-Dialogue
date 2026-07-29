package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.R;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueBoxLayout;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueOption;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;

import javax.annotation.Nullable;
import java.util.Objects;

public final class DialogueFragment extends Fragment implements ScreenCallback {
    private static final int BOX_BACKGROUND = 0xCC08080C;
    private static final int BOX_BORDER = 0xE6FFFFFF;
    private static final int DIVIDER_COLOR = 0x80FFFFFF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int ERROR_COLOR = 0xFFFF8080;
    private static final int OPTION_BACKGROUND = 0x38000000;
    private static final int OPTION_HOVER_BACKGROUND = 0x78000000;
    private static final int OPTION_PRESSED_BACKGROUND = 0xA0000000;
    private static final int OPTION_BORDER = 0x70FFFFFF;
    private static final int OPTION_HOVER_BORDER = 0xFFFFFFFF;

    private final ClientDialogueController controller;

    @Nullable
    private DialogueRootLayout rootLayout;
    @Nullable
    private DialogueSceneLayer sceneLayer;
    private long renderedGeneration = Long.MIN_VALUE;
    @Nullable
    private LinearLayout headerSection;
    @Nullable
    private TextView speakerName;
    @Nullable
    private Button expandButton;
    @Nullable
    private View headerDivider;
    @Nullable
    private TextView dialogueText;
    @Nullable
    private TextView errorText;
    @Nullable
    private View optionsDivider;
    @Nullable
    private LinearLayout optionsSection;
    @Nullable
    private TextView loadingText;
    @Nullable
    private LinearLayout optionList;

    public DialogueFragment(ClientDialogueController controller) {
        this.controller = controller;
    }

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

        LinearLayout dialogueBox = new LinearLayout(context);
        dialogueBox.setOrientation(LinearLayout.VERTICAL);
        dialogueBox.setBackground(createShape(
                dialogueBox,
                BOX_BACKGROUND,
                BOX_BORDER,
                2
        ));

        createHeader(dialogueBox);
        createContent(dialogueBox);
        createOptions(dialogueBox);

        DialogueSceneLayer scene = new DialogueSceneLayer(context);
        DialogueRootLayout root = new DialogueRootLayout(
                context,
                scene,
                dialogueBox
        );
        root.setOnClickListener(view -> Minecraft.getInstance().execute(
                controller::advance
        ));
        rootLayout = root;
        sceneLayer = scene;

        render(controller.viewState());
        return root;
    }

    private void createHeader(LinearLayout dialogueBox) {
        LinearLayout header = new LinearLayout(dialogueBox.getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int horizontalPadding = header.dp(12);
        int verticalPadding = header.dp(7);
        header.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
        );

        TextView name = new TextView(header.getContext());
        name.setTextColor(TEXT_COLOR);
        name.setTextSize(15);
        name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.addView(
                name,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        Button expand = new Button(header.getContext());
        expand.setText("Expand");
        expand.setTextColor(TEXT_COLOR);
        expand.setTextSize(13);
        expand.setVisibility(View.GONE);
        header.addView(
                expand,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        View divider = createDivider(dialogueBox);
        dialogueBox.addView(
                header,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        dialogueBox.addView(divider);

        headerSection = header;
        speakerName = name;
        expandButton = expand;
        headerDivider = divider;
    }

    private void createContent(LinearLayout dialogueBox) {
        LinearLayout content = new LinearLayout(dialogueBox.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = content.dp(12);
        int verticalPadding = content.dp(10);
        content.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
        );

        TextView text = new TextView(content.getContext());
        text.setTextColor(TEXT_COLOR);
        text.setTextSize(16);
        text.setGravity(Gravity.START);
        content.addView(
                text,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        TextView error = new TextView(content.getContext());
        error.setTextColor(ERROR_COLOR);
        error.setTextSize(13);
        error.setGravity(Gravity.START);
        error.setVisibility(View.GONE);
        content.addView(
                error,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        dialogueBox.addView(
                content,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        dialogueText = text;
        errorText = error;
    }

    private void createOptions(LinearLayout dialogueBox) {
        View divider = createDivider(dialogueBox);

        LinearLayout section = new LinearLayout(dialogueBox.getContext());
        section.setOrientation(LinearLayout.VERTICAL);
        int padding = section.dp(6);
        section.setPadding(padding, padding, padding, padding);

        TextView loading = new TextView(section.getContext());
        loading.setText("Loading options…");
        loading.setTextColor(TEXT_COLOR);
        loading.setTextSize(14);
        int loadingPadding = loading.dp(6);
        loading.setPadding(
                loadingPadding,
                loadingPadding,
                loadingPadding,
                loadingPadding
        );
        loading.setVisibility(View.GONE);
        section.addView(
                loading,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        LinearLayout list = new LinearLayout(section.getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        section.addView(
                list,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        divider.setVisibility(View.GONE);
        section.setVisibility(View.GONE);
        dialogueBox.addView(divider);
        dialogueBox.addView(
                section,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        optionsDivider = divider;
        optionsSection = section;
        loadingText = loading;
        optionList = list;
    }

    public void render(DialogueViewState state) {
        TextView name = speakerName;
        Button expand = expandButton;
        LinearLayout header = headerSection;
        View firstDivider = headerDivider;
        TextView text = dialogueText;
        TextView error = errorText;
        View thirdDivider = optionsDivider;
        LinearLayout thirdSection = optionsSection;
        TextView loading = loadingText;
        LinearLayout list = optionList;
        DialogueRootLayout root = rootLayout;
        DialogueSceneLayer scene = sceneLayer;
        if (name == null
                || expand == null
                || header == null
                || firstDivider == null
                || text == null
                || error == null
                || thirdDivider == null
                || thirdSection == null
                || loading == null
                || list == null
                || root == null
                || scene == null) {
            return;
        }

        header.post(() -> {
            if (state.generation() != renderedGeneration) {
                renderedGeneration = state.generation();
                Presentation presentation = state.presentation()
                        .orElse(null);
                if (presentation == null) {
                    scene.clearScene();
                    root.setDialogueBoxLayout(DialogueBoxLayout.DEFAULT);
                } else {
                    scene.apply(presentation);
                    root.setDialogueBoxLayout(presentation.dialogueBox());
                }
            }

            boolean hasSpeaker = state.speaker().isPresent();
            name.setText(state.speaker().orElse(""));
            name.setVisibility(hasSpeaker ? View.VISIBLE : View.GONE);

            boolean showHeader = hasSpeaker
                    || expand.getVisibility() == View.VISIBLE;
            header.setVisibility(showHeader ? View.VISIBLE : View.GONE);
            firstDivider.setVisibility(showHeader ? View.VISIBLE : View.GONE);

            text.setText(state.text().orElse(""));

            boolean hasError = state.error().isPresent();
            error.setText(state.error().orElse(""));
            error.setVisibility(hasError ? View.VISIBLE : View.GONE);

            boolean showOptions = state.loadingOptions()
                    || !state.options().isEmpty();
            thirdDivider.setVisibility(
                    showOptions ? View.VISIBLE : View.GONE
            );
            thirdSection.setVisibility(
                    showOptions ? View.VISIBLE : View.GONE
            );
            loading.setVisibility(
                    state.loadingOptions() ? View.VISIBLE : View.GONE
            );

            list.removeAllViews();
            for (DialogueOption option : state.options()) {
                list.addView(
                        createOptionButton(list, option, state.requestingTarget()),
                        optionLayoutParams(list)
                );
            }
        });
    }

    private Button createOptionButton(
            LinearLayout parent,
            DialogueOption option,
            boolean requestingTarget
    ) {
        Button button = new Button(parent.getContext());
        button.setText(optionLabel(option));
        button.setTextColor(TEXT_COLOR);
        button.setTextSize(15);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        int horizontalPadding = button.dp(12);
        int verticalPadding = button.dp(8);
        button.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
        );
        button.setBackground(createOptionBackground(button));
        button.setEnabled(!requestingTarget);
        button.setOnClickListener(view ->
                Minecraft.getInstance().execute(
                        () -> controller.selectOption(option)
                )
        );
        return button;
    }

    private static LinearLayout.LayoutParams optionLayoutParams(View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int margin = view.dp(2);
        params.setMargins(0, margin, 0, margin);
        return params;
    }

    private static String optionLabel(DialogueOption option) {
        String prefix = switch (option.icon()) {
            case NONE -> "";
            case QUESTION -> "?  ";
            case EXCLAMATION -> "!  ";
            case DIALOGUE -> "›  ";
        };
        return prefix + option.text();
    }

    private static StateListDrawable createOptionBackground(View view) {
        StateListDrawable background = new StateListDrawable();
        background.addState(
                new int[]{R.attr.state_pressed},
                createShape(
                        view,
                        OPTION_PRESSED_BACKGROUND,
                        OPTION_HOVER_BORDER,
                        1
                )
        );
        background.addState(
                new int[]{R.attr.state_hovered},
                createShape(
                        view,
                        OPTION_HOVER_BACKGROUND,
                        OPTION_HOVER_BORDER,
                        1
                )
        );
        background.addState(
                StateSet.WILD_CARD,
                createShape(
                        view,
                        OPTION_BACKGROUND,
                        OPTION_BORDER,
                        1
                )
        );
        return background;
    }

    private static ShapeDrawable createShape(
            View view,
            int color,
            int strokeColor,
            int cornerRadiusDp
    ) {
        ShapeDrawable shape = new ShapeDrawable();
        shape.setColor(color);
        shape.setCornerRadius(view.dp(cornerRadiusDp));
        shape.setStroke(view.dp(1), strokeColor);
        return shape;
    }

    private static View createDivider(ViewGroup parent) {
        View divider = new View(parent.getContext());
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(DIVIDER_COLOR);
        divider.setBackground(background);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                parent.dp(1)
        ));
        return divider;
    }

    @Override
    public void onDestroy() {
        DialogueSceneLayer scene = sceneLayer;
        if (scene != null) {
            scene.clearScene();
        }
        rootLayout = null;
        sceneLayer = null;
        renderedGeneration = Long.MIN_VALUE;
        headerSection = null;
        speakerName = null;
        expandButton = null;
        headerDivider = null;
        dialogueText = null;
        errorText = null;
        optionsDivider = null;
        optionsSection = null;
        loadingText = null;
        optionList = null;
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
