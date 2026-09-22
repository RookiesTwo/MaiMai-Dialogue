package top.rookiestwo.maimai_dialogue_editor.client.ui;

import com.google.gson.JsonObject;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue_editor.preview.ActionTimeline;
import icyllis.modernui.widget.*;
import top.rookiestwo.maimai_dialogue_editor.document.*;
import top.rookiestwo.maimai_dialogue_editor.project.*;
import java.util.*;

/** Scrollable Step/End action calls; resources themselves remain in the left resource tree. */
final class ActionCallsView extends LinearLayout {
    private final ProjectWorkspace project;
    private final ActionWorkspace model;
    private final EditorPreviewHost preview;
    private final EditorActionKeyframes keyframes;
    private final ActionTimelineStrip rulerStrip;
    private final ActionTimelineRow ruler;
    private final EditText position;
    private final TextView totalTime;
    private final TextView[] ticks = new TextView[3];
    private boolean lastCanSeek;
    private final List<ActionTimelineStrip> strips = new ArrayList<>();
    private final List<Integer> callIndices = new ArrayList<>();
    private ScenePlayback shownPlayback, editingPosition;
    private int lastSelected = Integer.MIN_VALUE;
    private final LinearLayout list;
    private final TextView empty;
    private final List<Button> controls = new ArrayList<>(), rows = new ArrayList<>();
    private Object revision;
    private ActionWorkspace.Context context;
    ActionCallsView(Context context, ProjectWorkspace project, EditorPreviewHost preview, ChoicePresenter choices) {
        super(context); this.project = project; this.preview = preview; model = project.actions(); setOrientation(VERTICAL); keyframes = new EditorActionKeyframes(project, preview, choices);
        var toolbar = new LinearLayout(context); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        var toolbarScroll = new HorizontalScrollView(context); toolbarScroll.setHorizontalScrollBarEnabled(false);
        toolbarScroll.addView(toolbar, new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(toolbarScroll);
        EditorWidgets.bindMetrics(toolbarScroll, () -> toolbarScroll.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(28))));
        button(toolbar, "+", "action.add", () -> model.add("custom"));
        button(toolbar, "↗", "action.add_reference", () -> model.add("reference"));
        button(toolbar, "⧉", "browser.copy", model::copy);
        button(toolbar, "−", "browser.delete", model::delete);
        button(toolbar, "↑", "edit.up", () -> model.move(-1));
        button(toolbar, "↓", "edit.down", () -> model.move(1));
        button(toolbar, "▶", "timeline.replay", preview::replayTimeline);
        button(toolbar, "■", "timeline.stop", () -> preview.seekTimeline(preview.timelinePlayback(), 0));
        position = EditorWidgets.compactInput(context, "0", ignored -> {}, () -> {});
        position.setTooltipText(EditorWidgets.tr("timeline.position"));
        toolbar.addView(position);
        EditorWidgets.bindMetrics(position, () -> {
            var params = new LayoutParams(dp(68), dp(EditorWidgets.COMPACT_CONTROL_DP));
            params.setMargins(dp(8), dp(2), dp(4), dp(2)); position.setLayoutParams(params);
        });
        totalTime = EditorWidgets.label(context, "", 13, EditorWidgets.MUTED);
        toolbar.addView(totalTime, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        EditorWidgets.bindMetrics(totalTime, () -> totalTime.setPadding(0, 0, dp(6), 0));
        position.setOnFocusChangeListener((view, focused) -> {
            if (focused) editingPosition = preview.timelinePlayback();
            else {
                try { preview.seekTimeline(editingPosition, Integer.parseInt(position.getText().toString().strip())); }
                catch (NumberFormatException ignored) { /* Restore the current playhead on invalid input. */ }
                editingPosition = null; refreshPosition();
            }
        });
        var rulerArea = new FrameLayout(context);
        rulerStrip = new ActionTimelineStrip(context, preview, null, () -> {}, keyframes);
        var strip = rulerStrip;
        rulerArea.addView(strip, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        var labels = new LinearLayout(context);
        for (int i = 0; i < ticks.length; i++) {
            ticks[i] = EditorWidgets.label(context, "", 12, EditorWidgets.MUTED);
            ticks[i].setGravity((i == 0 ? Gravity.START : i == 1 ? Gravity.CENTER : Gravity.END) | Gravity.CENTER_VERTICAL);
            labels.addView(ticks[i], new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        }
        rulerArea.addView(labels);
        EditorWidgets.bindMetrics(labels, () -> {
            labels.setPadding(dp(ActionTimelineStrip.EDGE_INSET_DP), 0, dp(ActionTimelineStrip.EDGE_INSET_DP), 0);
            labels.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_ROW_DP)));
        });
        var heading = EditorWidgets.label(context, "timeline.action", 13, EditorWidgets.MUTED);
        EditorWidgets.bindMetrics(heading, () -> heading.setPadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0, 0, 0));
        ruler = new ActionTimelineRow(context, heading, rulerArea); addView(ruler);
        EditorWidgets.bindMetrics(ruler, () -> {
            var params = new LayoutParams(LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_ROW_DP + ActionTimelineStrip.RULER_HANDLE_DP));
            params.setMargins(0, 0, dp(4), 0); ruler.setLayoutParams(params);
        });
        list = new LinearLayout(context); list.setOrientation(VERTICAL);
        list.setOnClickListener(view -> model.select(-1));
        empty = EditorWidgets.compactParagraph(context, "action.empty");
        var scroll = EditorWidgets.formScroll(context, list); scroll.setFillViewport(true);
        // Reserve the same scrollbar gutter in both the fixed ruler and scrolling track viewport.
        scroll.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        EditorWidgets.bindMetrics(scroll, () -> { scroll.setPadding(0, 0, dp(4), 0); scroll.setScrollBarSize(dp(4)); });
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        preview.setTimelineListener(this::refreshTimeline);
    }
    private Button button(LinearLayout toolbar, String icon, String label, Runnable action) {
        var button = EditorWidgets.icon(getContext(), icon, label, action); toolbar.addView(button); controls.add(button);
        EditorWidgets.bindMetrics(button, () -> {
            var params = new LayoutParams(dp(EditorWidgets.COMPACT_CONTROL_DP), dp(EditorWidgets.COMPACT_CONTROL_DP));
            params.setMargins(dp(2), dp(2), dp(2), dp(2)); button.setLayoutParams(params);
        }); return button;
    }
    void refresh() {
        var next = model.context(); var resource = next == null ? null : project.draft().revision(next.resource());
        var playback = preview.timelinePlayback();
        if (!Objects.equals(context, next) || revision != resource || shownPlayback != playback) {
            context = next; revision = resource; shownPlayback = playback;
            list.removeAllViews(); rows.clear(); strips.clear(); callIndices.clear(); lastSelected = Integer.MIN_VALUE;
            var calls = model.calls();
            if (context != null && playback != null) {
                for (var lane : preview.timeline().lanes()) addRow(lane.callIndex(), lane);
                if (preview.timeline().lanes().isEmpty()) { empty.setText(EditorWidgets.tr("action.empty")); list.addView(empty); }
            } else if (calls == null || calls.isEmpty()) {
                empty.setText(EditorWidgets.tr(context == null || context.standalone() ? "no_step" : calls == null ? "edit.invalid_object" : "action.empty")); list.addView(empty);
            } else for (int i = 0; i < calls.size(); i++) addRow(i, null);
        }
        int selected = model.selected();
        for (int i = 0; i < controls.size(); i++) EditorWidgets.enabled(controls.get(i), switch (i) {
            case 6 -> context != null && (context.standalone() ? preview.actionPreview().canPlay() : model.active());
            case 7 -> preview.canSeekTimeline();
            default -> model.canAdd() && switch (i) {
                case 0, 1 -> true; case 4 -> selected > 0;
                case 5 -> selected >= 0 && selected + 1 < model.calls().size(); default -> selected >= 0;
            };
        });
        for (int i = 0; i < rows.size(); i++) EditorWidgets.enabled(rows.get(i), model.active() && callIndices.get(i) >= 0);
        ruler.setVisibility(context == null ? GONE : VISIBLE);
        lastCanSeek = preview.canSeekTimeline(); position.setEnabled(lastCanSeek);
        refreshPosition();
    }
    private void addRow(int index, ActionTimeline.Lane lane) {
        var expected = context;
        Runnable select = () -> {
            if (index >= 0 && Objects.equals(expected, model.context()) && !expected.standalone())
                model.select(model.selected() == index ? -1 : index);
        };
        var button = EditorWidgets.button(getContext(), "", select);
        String title;
        if (index < 0) title = EditorWidgets.tr("timeline.automatic_fade");
        else if (context.standalone()) title = context.resource().path();
        else {
            JsonObject call = DialogueDraft.object(model.calls().get(index));
            title = ActionFields.text(call, "action.type", "").equals("reference") ? ActionFields.text(call, "action.id", "")
                    : summary(DialogueDraft.object(ActionFields.get(call, "action.action")));
            String target = ActionFields.text(call, "target", "");
            title = (index + 1) + "  " + (target.isEmpty() ? "" : target + " · ") + title;
        }
        button.setText(title); button.setTooltipText(title + (lane == null ? "" : "\n" + lane.startMs() + "–" + lane.endMs() + " ms"));
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); rows.add(button); callIndices.add(index);
        EditorWidgets.bindMetrics(button, () -> button.setPadding(dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0, dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP), 0));
        View row = button;
        if (lane != null) {
            var strip = new ActionTimelineStrip(getContext(), preview, lane, () -> {
                if (index >= 0 && !expected.standalone() && Objects.equals(expected, model.context())) model.select(index);
            }, keyframes);
            strips.add(strip); row = new ActionTimelineRow(getContext(), button, strip);
        }
        list.addView(row); View item = row;
        EditorWidgets.bindMetrics(item, () -> item.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(EditorWidgets.COMPACT_ROW_DP))));
    }
    private void refreshTimeline() {
        if (shownPlayback != preview.timelinePlayback() || lastCanSeek != preview.canSeekTimeline()
                || !Objects.equals(context, model.context())) refresh(); else refreshPosition();
    }
    private void refreshPosition() {
        if (!position.isFocused()) {
            String text = Integer.toString(shownPlayback == null ? 0 : preview.timeline().position());
            if (!position.getText().toString().equals(text)) position.setText(text);
        }
        int duration = shownPlayback == null ? 0 : preview.timeline().duration();
        String total = "ms / " + duration + " ms";
        if (!totalTime.getText().toString().equals(total)) totalTime.setText(total);
        for (int i = 0; i < ticks.length; i++) {
            String text = Integer.toString((int)(duration * (i / 2.0))) + (i == 2 ? " ms" : "");
            if (!ticks[i].getText().toString().equals(text)) ticks[i].setText(text);
        }
        if (lastSelected != model.selected()) {
            lastSelected = model.selected();
            for (int i = 0; i < rows.size(); i++) rows.get(i).setSelected(callIndices.get(i) >= 0 && callIndices.get(i) == lastSelected);
        }
        ruler.invalidate(); invalidateStrips(ruler); rulerStrip.refreshPointerHover();
        strips.forEach(strip -> { strip.refreshPointerHover(); strip.invalidate(); });
    }
    private static void invalidateStrips(icyllis.modernui.view.ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            var child = group.getChildAt(i); if (child instanceof ActionTimelineStrip) child.invalidate();
            if (child instanceof icyllis.modernui.view.ViewGroup nested) invalidateStrips(nested);
        }
    }
    private String summary(JsonObject action) {
        if (action == null) return EditorWidgets.tr("edit.invalid_object");
        var fields = ActionFields.COMPONENTS.stream()
                .filter(action::has).map(field -> EditorWidgets.tr("action." + field)).toList();
        return fields.isEmpty() ? EditorWidgets.tr("action.preset.custom") : String.join(" · ", fields);
    }
}
