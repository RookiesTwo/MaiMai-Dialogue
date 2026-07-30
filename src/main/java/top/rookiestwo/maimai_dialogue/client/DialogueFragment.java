package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.R;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.markflow.Markflow;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueBoxLayout;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueOption;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;
import top.rookiestwo.maimai_dialogue.theme.ThemeControls;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeOption;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public final class DialogueFragment extends Fragment implements ScreenCallback {
    private final ClientDialogueController controller;

    private ThemeDefinition currentTheme = ThemeDefinition.DEFAULT;
    @Nullable
    private DialogueRootLayout rootLayout;
    @Nullable
    private DialogueSceneLayer sceneLayer;
    private long renderedGeneration = Long.MIN_VALUE;
    @Nullable
    private LinearLayout dialogueBox;
    @Nullable
    private LinearLayout headerSection;
    @Nullable
    private TextView speakerName;
    @Nullable
    private Button expandButton;
    @Nullable
    private Button historyButton;
    @Nullable
    private View headerDivider;
    @Nullable
    private LinearLayout contentSection;
    @Nullable
    private TextView dialogueText;
    @Nullable
    private DialogueTextPlayer textPlayer;
    @Nullable
    private TextView errorText;
    @Nullable
    private View optionsDivider;
    @Nullable
    private LinearLayout optionsSection;
    @Nullable
    private TextView loadingText;
    @Nullable
    private OptionScrollView optionScroll;
    @Nullable
    private LinearLayout optionList;
    @Nullable
    private FrameLayout historyOverlay;
    @Nullable
    private LinearLayout historyPanel;
    @Nullable
    private TextView historyTitle;
    @Nullable
    private Button historyCloseButton;
    @Nullable
    private View historyHeaderDivider;
    @Nullable
    private ScrollView historyScroll;
    @Nullable
    private LinearLayout historyList;
    @Nullable
    private Markflow historyMarkflow;
    private int renderedHistorySize = -1;

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
                currentTheme.box().background().argb(),
                currentTheme.box().border().argb(),
                currentTheme.box().cornerRadiusDp(),
                currentTheme.box().borderWidthDp()
        ));

        createHeader(dialogueBox);
        createContent(dialogueBox);
        createOptions(dialogueBox);
        Button historyEntry = createHistoryEntryButton(context);
        FrameLayout historyOverlay = createHistoryOverlay(context);

        DialogueSceneLayer scene = new DialogueSceneLayer(context);
        DialogueRootLayout root = new DialogueRootLayout(
                context,
                scene,
                dialogueBox,
                historyEntry,
                historyOverlay
        );
        root.setOnClickListener(view -> advanceFromUi());
        root.setAdvanceAction(this::advanceFromUi);
        scene.setDialogueOpacityConsumer(root::setDialogueOpacity);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        rootLayout = root;
        sceneLayer = scene;
        this.dialogueBox = dialogueBox;

        render(controller.viewState());
        return root;
    }

    private void createHeader(LinearLayout dialogueBox) {
        LinearLayout header = new LinearLayout(dialogueBox.getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int horizontalPadding = header.dp(
                currentTheme.spacing().headerHorizontalDp()
        );
        int verticalPadding = header.dp(
                currentTheme.spacing().headerVerticalDp()
        );
        header.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
        );

        TextView name = new TextView(header.getContext());
        name.setTextColor(currentTheme.text().primary().argb());
        name.setTextSize(currentTheme.text().speakerSizeSp());
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
        expand.setTextColor(currentTheme.text().primary().argb());
        expand.setTextSize(currentTheme.text().auxiliarySizeSp());
        expand.setVisibility(View.GONE);
        expand.setOnClickListener(view -> toggleOptionsExpanded());
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

    private Button createHistoryEntryButton(
            icyllis.modernui.core.Context context
    ) {
        Button history = new Button(context);
        history.setText("History");
        history.setOnClickListener(view -> setHistoryOpen(true));
        historyButton = history;
        return history;
    }

    private void createContent(LinearLayout dialogueBox) {
        LinearLayout content = new LinearLayout(dialogueBox.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = content.dp(
                currentTheme.spacing().contentHorizontalDp()
        );
        int verticalPadding = content.dp(
                currentTheme.spacing().contentVerticalDp()
        );
        content.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
        );

        TextView text = new TextView(content.getContext());
        text.setTextColor(currentTheme.text().primary().argb());
        text.setTextSize(currentTheme.text().dialogueSizeSp());
        text.setGravity(Gravity.START);
        content.addView(
                text,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        TextView error = new TextView(content.getContext());
        error.setTextColor(currentTheme.text().error().argb());
        error.setTextSize(currentTheme.text().auxiliarySizeSp());
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

        contentSection = content;
        dialogueText = text;
        textPlayer = new DialogueTextPlayer(text);
        errorText = error;
    }

    private void createOptions(LinearLayout dialogueBox) {
        View divider = createDivider(dialogueBox);

        LinearLayout section = new LinearLayout(dialogueBox.getContext());
        section.setOrientation(LinearLayout.VERTICAL);
        int padding = section.dp(
                currentTheme.spacing().optionsPaddingDp()
        );
        section.setPadding(padding, padding, padding, padding);

        TextView loading = new TextView(section.getContext());
        loading.setText("Loading options…");
        loading.setTextColor(currentTheme.text().primary().argb());
        loading.setTextSize(currentTheme.text().auxiliarySizeSp());
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

        OptionScrollView scroll = new OptionScrollView(section.getContext());
        scroll.setOptionLimits(
                currentTheme.spacing().optionsCollapsedLimit(),
                currentTheme.spacing().optionsExpandedLimit()
        );

        LinearLayout list = new LinearLayout(scroll.getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(
                list,
                new OptionScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        section.addView(
                scroll,
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
        optionScroll = scroll;
        optionList = list;
    }

    private FrameLayout createHistoryOverlay(
            icyllis.modernui.core.Context context
    ) {
        FrameLayout overlay = new FrameLayout(context);
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);
        overlay.setOnClickListener(view -> setHistoryOpen(false));
        ShapeDrawable scrim = new ShapeDrawable();
        scrim.setColor(0x4D000000);
        overlay.setBackground(scrim);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText("History");
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        titleRow.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        Button close = new Button(context);
        close.setText("Close");
        close.setOnClickListener(view -> setHistoryOpen(false));
        LinearLayout.LayoutParams closeParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        closeParams.rightMargin = close.dp(
                currentTheme.spacing().headerHorizontalDp()
        );
        titleRow.addView(
                close,
                closeParams
        );
        panel.addView(
                titleRow,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        View titleDivider = createDivider(panel);
        panel.addView(titleDivider);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(
                list,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        panel.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        int margin = overlay.dp(24);
        panelParams.setMargins(margin, margin, margin, margin);
        panelParams.gravity = Gravity.CENTER;
        overlay.addView(panel, panelParams);

        this.historyOverlay = overlay;
        historyPanel = panel;
        historyTitle = title;
        historyCloseButton = close;
        historyHeaderDivider = titleDivider;
        historyScroll = scroll;
        historyList = list;
        historyMarkflow = Markflow.create(context);
        return overlay;
    }

    public void render(DialogueViewState state) {
        TextView name = speakerName;
        Button expand = expandButton;
        LinearLayout header = headerSection;
        View firstDivider = headerDivider;
        TextView text = dialogueText;
        DialogueTextPlayer player = textPlayer;
        TextView error = errorText;
        View thirdDivider = optionsDivider;
        LinearLayout thirdSection = optionsSection;
        TextView loading = loadingText;
        OptionScrollView scroll = optionScroll;
        LinearLayout list = optionList;
        DialogueRootLayout root = rootLayout;
        DialogueSceneLayer scene = sceneLayer;
        if (name == null
                || expand == null
                || header == null
                || firstDivider == null
                || text == null
                || player == null
                || error == null
                || thirdDivider == null
                || thirdSection == null
                || loading == null
                || scroll == null
                || list == null
                || root == null
                || scene == null) {
            return;
        }

        header.post(() -> {
            if (state.generation() != renderedGeneration) {
                renderedGeneration = state.generation();
                player.clear();
                currentTheme = state.theme().orElse(ThemeDefinition.DEFAULT);
                renderedHistorySize = -1;
                applyTheme();
                scroll.setExpanded(false);
                scroll.scrollTo(0, 0);
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
            state.scenePlayback().ifPresent(playback ->
                    scene.renderPlayback(
                            playback,
                            state.playbackSkipped(),
                            () -> Minecraft.getInstance().execute(
                                    () -> controller.completePlayback(
                                            state.generation(),
                                            playback.token()
                                    )
                            )
                    )
            );

            boolean hasSpeaker = state.speaker().isPresent();
            name.setText(state.speaker().orElse(""));
            name.setVisibility(hasSpeaker ? View.VISIBLE : View.GONE);

            boolean showHeader = hasSpeaker
                    || expand.getVisibility() == View.VISIBLE;
            header.setVisibility(showHeader ? View.VISIBLE : View.GONE);
            firstDivider.setVisibility(showHeader ? View.VISIBLE : View.GONE);

            long textToken = state.scenePlayback()
                    .map(ScenePlayback::token)
                    .orElse(Long.MIN_VALUE);
            player.render(
                    textToken,
                    state.text().orElse(""),
                    state.playbackSkipped(),
                    () -> Minecraft.getInstance().execute(
                            () -> controller.completeTextPlayback(
                                    state.generation(),
                                    textToken
                            )
                    )
            );

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
                        optionLayoutParams(list, currentTheme.option())
                );
            }
            scroll.post(this::updateExpandVisibility);
            renderHistory(state.history());
        });
    }

    private void advanceFromUi() {
        FrameLayout overlay = historyOverlay;
        if (overlay != null && overlay.getVisibility() == View.VISIBLE) {
            return;
        }
        Minecraft.getInstance().execute(controller::advance);
    }

    private void setHistoryOpen(boolean open) {
        FrameLayout overlay = historyOverlay;
        ScrollView scroll = historyScroll;
        if (overlay == null || scroll == null) {
            return;
        }
        overlay.setVisibility(open ? View.VISIBLE : View.GONE);
        Button history = historyButton;
        if (history != null) {
            history.setVisibility(open ? View.GONE : View.VISIBLE);
        }
        if (open) {
            overlay.requestFocus();
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        } else {
            DialogueRootLayout root = rootLayout;
            if (root != null) {
                root.requestFocus();
            }
        }
    }

    private void renderHistory(List<HistoryEntry> entries) {
        if (entries.size() == renderedHistorySize) {
            return;
        }
        LinearLayout list = historyList;
        Markflow markflow = historyMarkflow;
        if (list == null || markflow == null) {
            return;
        }
        list.removeAllViews();
        for (int index = 0; index < entries.size(); index++) {
            HistoryEntry entry = entries.get(index);
            list.addView(
                    createHistoryEntry(list, markflow, entry),
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );
            if (index + 1 < entries.size()) {
                View divider = createDivider(list);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1
                ));
                setDividerTheme(
                        divider,
                        currentTheme.box().divider().argb()
                );
                list.addView(divider);
            }
        }
        renderedHistorySize = entries.size();
        FrameLayout overlay = historyOverlay;
        ScrollView scroll = historyScroll;
        if (overlay != null
                && scroll != null
                && overlay.getVisibility() == View.VISIBLE) {
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private LinearLayout createHistoryEntry(
            LinearLayout parent,
            Markflow markflow,
            HistoryEntry entry
    ) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        int horizontal = row.dp(
                currentTheme.spacing().contentHorizontalDp()
        );
        int vertical = row.dp(
                currentTheme.spacing().contentVerticalDp()
        );
        row.setPadding(horizontal, vertical, horizontal, vertical);

        TextView name = new TextView(row.getContext());
        name.setText(entry.speaker().orElse(""));
        name.setTextColor(currentTheme.text().primary().argb());
        name.setTextSize(currentTheme.text().speakerSizeSp());
        name.setGravity(Gravity.START | Gravity.TOP);
        row.addView(
                name,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        TextView content = new TextView(row.getContext());
        content.setGravity(Gravity.START | Gravity.TOP);
        if (entry.type() == HistoryEntry.Type.DIALOGUE) {
            content.setTextColor(currentTheme.text().primary().argb());
            content.setTextSize(currentTheme.text().dialogueSizeSp());
            markflow.setMarkdown(content, entry.content());
        } else {
            content.setText(styledIconLabel("›  ", entry.content()));
            content.setTextColor(
                    currentTheme.option().hoverBorder().argb()
            );
            content.setTextSize(currentTheme.text().optionSizeSp());
        }
        row.addView(
                content,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        4
                )
        );
        return row;
    }

    private void toggleOptionsExpanded() {
        OptionScrollView scroll = optionScroll;
        if (scroll == null) {
            return;
        }
        scroll.setExpanded(!scroll.isExpanded());
        updateExpandVisibility();
    }

    private void updateExpandVisibility() {
        OptionScrollView scroll = optionScroll;
        Button expand = expandButton;
        if (scroll == null || expand == null) {
            return;
        }
        boolean show = scroll.isExpandable();
        if (!show && scroll.isExpanded()) {
            scroll.setExpanded(false);
        }
        expand.setText(scroll.isExpanded() ? "Collapse" : "Expand");
        expand.setVisibility(show ? View.VISIBLE : View.GONE);
        updateHeaderVisibility();
    }

    private void updateHeaderVisibility() {
        LinearLayout header = headerSection;
        TextView name = speakerName;
        Button expand = expandButton;
        View divider = headerDivider;
        if (header == null
                || name == null
                || expand == null
                || divider == null) {
            return;
        }
        boolean show = name.getVisibility() == View.VISIBLE
                || expand.getVisibility() == View.VISIBLE;
        header.setVisibility(show ? View.VISIBLE : View.GONE);
        divider.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private Button createOptionButton(
            LinearLayout parent,
            DialogueOption option,
            boolean requestingTarget
    ) {
        Button button = new Button(parent.getContext());
        button.setText(optionLabel(option));
        ThemeOption optionTheme = currentTheme.option();
        button.setTextColor(currentTheme.text().primary().argb());
        button.setTextSize(currentTheme.text().optionSizeSp());
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        int horizontalPadding = button.dp(
                optionTheme.horizontalPaddingDp()
        );
        int verticalPadding = button.dp(
                optionTheme.verticalPaddingDp()
        );
        button.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
        );
        button.setBackground(createOptionBackground(button, optionTheme));
        button.setEnabled(!requestingTarget);
        button.setOnClickListener(view ->
                Minecraft.getInstance().execute(
                        () -> controller.selectOption(option)
                )
        );
        return button;
    }

    private static LinearLayout.LayoutParams optionLayoutParams(
            View view,
            ThemeOption theme
    ) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int margin = view.dp(theme.spacingDp());
        params.setMargins(0, margin, 0, margin);
        return params;
    }

    private CharSequence optionLabel(DialogueOption option) {
        String prefix = switch (option.icon()) {
            case NONE -> "";
            case QUESTION -> "?  ";
            case EXCLAMATION -> "!  ";
            case DIALOGUE -> "›  ";
        };
        return styledIconLabel(prefix, option.text());
    }

    private CharSequence styledIconLabel(String prefix, String text) {
        if (prefix.isEmpty()) {
            return text;
        }
        SpannableString label = new SpannableString(prefix + text);
        label.setSpan(
                new ForegroundColorSpan(
                        currentTheme.controls().icon().argb()
                ),
                0,
                prefix.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return label;
    }

    private static StateListDrawable createOptionBackground(
            View view,
            ThemeOption theme
    ) {
        StateListDrawable background = new StateListDrawable();
        background.addState(
                new int[]{R.attr.state_pressed},
                createShape(
                        view,
                        theme.pressedBackground().argb(),
                        theme.hoverBorder().argb(),
                        theme.cornerRadiusDp(),
                        1
                )
        );
        background.addState(
                new int[]{R.attr.state_hovered},
                createShape(
                        view,
                        theme.hoverBackground().argb(),
                        theme.hoverBorder().argb(),
                        theme.cornerRadiusDp(),
                        1
                )
        );
        background.addState(
                StateSet.WILD_CARD,
                createShape(
                        view,
                        theme.background().argb(),
                        theme.border().argb(),
                        theme.cornerRadiusDp(),
                        1
                )
        );
        return background;
    }

    private static ShapeDrawable createShape(
            View view,
            int color,
            int strokeColor,
            int cornerRadiusDp,
            int strokeWidthDp
    ) {
        ShapeDrawable shape = new ShapeDrawable();
        shape.setColor(color);
        shape.setCornerRadius(view.dp(cornerRadiusDp));
        shape.setStroke(view.dp(strokeWidthDp), strokeColor);
        return shape;
    }

    private static View createDivider(ViewGroup parent) {
        View divider = new View(parent.getContext());
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(
                ThemeDefinition.DEFAULT.box().divider().argb()
        );
        divider.setBackground(background);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                parent.dp(1)
        ));
        return divider;
    }

    private void applyTheme() {
        LinearLayout box = dialogueBox;
        LinearLayout header = headerSection;
        LinearLayout content = contentSection;
        LinearLayout options = optionsSection;
        TextView name = speakerName;
        Button expand = expandButton;
        Button history = historyButton;
        TextView text = dialogueText;
        TextView error = errorText;
        TextView loading = loadingText;
        if (box == null
                || header == null
                || content == null
                || options == null
                || name == null
                || expand == null
                || history == null
                || text == null
                || error == null
                || loading == null) {
            return;
        }

        var boxTheme = currentTheme.box();
        var textTheme = currentTheme.text();
        var spacing = currentTheme.spacing();
        box.setBackground(createShape(
                box,
                boxTheme.background().argb(),
                boxTheme.border().argb(),
                boxTheme.cornerRadiusDp(),
                boxTheme.borderWidthDp()
        ));
        setDividerTheme(headerDivider, boxTheme.divider().argb());
        setDividerTheme(optionsDivider, boxTheme.divider().argb());
        setDividerTheme(historyHeaderDivider, boxTheme.divider().argb());

        header.setPadding(
                header.dp(spacing.headerHorizontalDp()),
                header.dp(spacing.headerVerticalDp()),
                header.dp(spacing.headerHorizontalDp()),
                header.dp(spacing.headerVerticalDp())
        );
        content.setPadding(
                content.dp(spacing.contentHorizontalDp()),
                content.dp(spacing.contentVerticalDp()),
                content.dp(spacing.contentHorizontalDp()),
                content.dp(spacing.contentVerticalDp())
        );
        int optionsPadding = options.dp(spacing.optionsPaddingDp());
        options.setPadding(
                optionsPadding,
                optionsPadding,
                optionsPadding,
                optionsPadding
        );
        OptionScrollView scroll = optionScroll;
        if (scroll != null) {
            scroll.setOptionLimits(
                    spacing.optionsCollapsedLimit(),
                    spacing.optionsExpandedLimit()
            );
            applyScrollbarTheme(scroll, currentTheme.controls());
        }
        ScrollView historyScroll = this.historyScroll;
        if (historyScroll != null) {
            applyScrollbarTheme(
                    historyScroll,
                    currentTheme.controls()
            );
        }

        name.setTextColor(textTheme.primary().argb());
        name.setTextSize(textTheme.speakerSizeSp());
        expand.setTextColor(textTheme.primary().argb());
        expand.setTextSize(textTheme.auxiliarySizeSp());
        applyControlButtonTheme(expand);
        history.setTextColor(textTheme.primary().argb());
        history.setTextSize(textTheme.auxiliarySizeSp());
        applyControlButtonTheme(history);
        text.setTextColor(textTheme.primary().argb());
        text.setTextSize(textTheme.dialogueSizeSp());
        error.setTextColor(textTheme.error().argb());
        error.setTextSize(textTheme.auxiliarySizeSp());
        loading.setTextColor(textTheme.primary().argb());
        loading.setTextSize(textTheme.auxiliarySizeSp());

        LinearLayout historyPanel = this.historyPanel;
        TextView historyTitle = this.historyTitle;
        Button historyClose = historyCloseButton;
        if (historyPanel != null) {
            historyPanel.setBackground(createShape(
                    historyPanel,
                    boxTheme.background().argb(),
                    boxTheme.border().argb(),
                    boxTheme.cornerRadiusDp(),
                    boxTheme.borderWidthDp()
            ));
        }
        if (historyTitle != null) {
            historyTitle.setTextColor(textTheme.primary().argb());
            historyTitle.setTextSize(textTheme.speakerSizeSp());
            int horizontal = historyTitle.dp(
                    spacing.headerHorizontalDp()
            );
            int vertical = historyTitle.dp(spacing.headerVerticalDp());
            historyTitle.setPadding(
                    horizontal,
                    vertical,
                    horizontal,
                    vertical
            );
        }
        if (historyClose != null) {
            historyClose.setTextColor(textTheme.primary().argb());
            historyClose.setTextSize(textTheme.auxiliarySizeSp());
            applyControlButtonTheme(historyClose);
            ViewGroup.LayoutParams rawParams =
                    historyClose.getLayoutParams();
            if (rawParams instanceof LinearLayout.LayoutParams params) {
                params.rightMargin = historyClose.dp(
                        spacing.headerHorizontalDp()
                );
                historyClose.setLayoutParams(params);
            }
        }
    }

    private void applyControlButtonTheme(Button button) {
        ThemeOption theme = currentTheme.option();
        button.setBackground(createOptionBackground(button, theme));
        int horizontal = button.dp(theme.horizontalPaddingDp());
        int vertical = button.dp(Math.max(2, theme.verticalPaddingDp() / 2));
        button.setPadding(
                horizontal,
                vertical,
                horizontal,
                vertical
        );
    }

    private static void applyScrollbarTheme(
            ScrollView scroll,
            ThemeControls controls
    ) {
        int width = scroll.dp(controls.scrollbarWidthDp());

        ShapeDrawable thumb = new ShapeDrawable();
        thumb.setShape(ShapeDrawable.VLINE);
        thumb.setStroke(width, controls.scrollbarThumb().argb());
        thumb.setSize(width, -1);
        thumb.setCornerRadius(width / 2.0F);
        scroll.setVerticalScrollbarThumbDrawable(thumb);

        ShapeDrawable track = new ShapeDrawable();
        track.setShape(ShapeDrawable.VLINE);
        track.setStroke(width, controls.scrollbarTrack().argb());
        track.setSize(width, -1);
        track.setCornerRadius(width / 2.0F);
        scroll.setVerticalScrollbarTrackDrawable(track);
    }

    private static void setDividerTheme(@Nullable View divider, int color) {
        if (divider == null) {
            return;
        }
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(color);
        divider.setBackground(background);
    }

    @Override
    public void onDestroy() {
        DialogueSceneLayer scene = sceneLayer;
        if (scene != null) {
            scene.clearScene();
        }
        DialogueTextPlayer player = textPlayer;
        if (player != null) {
            player.clear();
        }
        rootLayout = null;
        sceneLayer = null;
        dialogueBox = null;
        renderedGeneration = Long.MIN_VALUE;
        headerSection = null;
        speakerName = null;
        expandButton = null;
        historyButton = null;
        headerDivider = null;
        contentSection = null;
        dialogueText = null;
        textPlayer = null;
        errorText = null;
        optionsDivider = null;
        optionsSection = null;
        loadingText = null;
        optionScroll = null;
        optionList = null;
        historyOverlay = null;
        historyPanel = null;
        historyTitle = null;
        historyCloseButton = null;
        historyHeaderDivider = null;
        historyScroll = null;
        historyList = null;
        historyMarkflow = null;
        renderedHistorySize = -1;
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
