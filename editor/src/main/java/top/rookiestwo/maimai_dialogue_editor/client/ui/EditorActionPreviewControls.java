package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.*;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.*;
import net.minecraft.client.Minecraft;
import top.rookiestwo.maimai_dialogue.client.bootstrap.ClientServices;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import java.util.*;

/** Compact preview context selectors; selection changes do not edit the Action or Scene. */
final class EditorActionPreviewControls extends LinearLayout {
    private final EditorPreviewHost host;
    private final ChoicePresenter choices;
    private final Button scene, target, play, stop;
    EditorActionPreviewControls(Context context, EditorPreviewHost host, ChoicePresenter choices) {
        super(context); this.host = host; this.choices = choices;
        setOrientation(VERTICAL); setBackground(EditorWidgets.shape(EditorWidgets.HEADER, 0));
        var settings = row();
        settings.addView(EditorWidgets.compactParagraph(context, "action.preview_scene"));
        scene = button(settings, "", this::chooseScene);
        settings.addView(EditorWidgets.compactParagraph(context, "action.target"));
        target = button(settings, "", this::chooseTarget);
        var transport = row();
        play = button(transport, "action.preview_play", () -> host.actionPreview().play());
        stop = button(transport, "preview.stop", () -> host.actionPreview().stop());
    }
    private LinearLayout row() {
        var row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
        var scroll = new HorizontalScrollView(getContext()); scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(scroll); EditorWidgets.bindMetrics(scroll, () -> scroll.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(26))));
        return row;
    }
    private Button button(LinearLayout row, String key, Runnable action) {
        var button = EditorWidgets.button(getContext(), key, action); row.addView(button);
        EditorWidgets.bindMetrics(button, () -> {
            var params = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(22)); params.setMargins(dp(4), dp(2), dp(4), dp(2));
            button.setPadding(dp(4), 0, dp(4), 0); button.setLayoutParams(params);
        });
        return button;
    }
    private boolean accepts(long generation, Object key) {
        return isAttachedToWindow() && host.actionPreview().canPlay() && generation == host.workspace().projectGeneration()
                && Objects.equals(key, host.workspace().resources().opened());
    }
    private void chooseScene() {
        var project = host.workspace(); var key = project.resources().opened(); long generation = project.projectGeneration();
        if (!accepts(generation, key)) return;
        String namespace = project.draft().namespace(); var ids = new TreeSet<String>();
        project.resources().catalog().keys().stream().filter(resource -> resource.kind() == ResourceKind.SCENE).forEach(resource -> ids.add(resource.id(namespace)));
        Minecraft.getInstance().execute(() -> {
            var external = ClientServices.get().content().current().scenes().ids().stream()
                    .filter(id -> !id.getNamespace().equals(namespace)).map(Object::toString).toList();
            Core.getUiHandler().post(() -> {
                if (!accepts(generation, key)) return;
                ids.addAll(external); var items = new ArrayList<ChoicePresenter.Item>();
                items.add(new ChoicePresenter.Item("", EditorWidgets.tr("action.preview_empty_scene")));
                ids.forEach(id -> items.add(new ChoicePresenter.Item(id, id)));
                choices.showSearchable(scene, items, project.actions().previewContext().scene(), value -> {
                    if (accepts(generation, key)) project.actions().previewScene(value);
                });
            });
        });
    }
    private void chooseTarget() {
        var project = host.workspace(); var key = project.resources().opened(); long generation = project.projectGeneration();
        if (!accepts(generation, key)) return;
        String sceneId = project.actions().previewContext().scene();
        choices.showSearchable(target, host.actionPreview().targets().stream().map(value -> new ChoicePresenter.Item(value, targetLabel(value))).toList(),
                project.actions().previewContext().target(), value -> {
                    if (accepts(generation, key) && sceneId.equals(project.actions().previewContext().scene())) project.actions().previewTarget(value);
                });
    }
    private static String targetLabel(String value) {
        return List.of("background", "dialogue").contains(value) ? EditorWidgets.tr("action.target." + value) : value;
    }
    void refresh() {
        var context = host.workspace().actions().previewContext();
        scene.setText((context.scene().isEmpty() ? EditorWidgets.tr("action.preview_empty_scene") : context.scene()) + " ▾");
        scene.setTooltipText(context.scene()); target.setText(targetLabel(context.target()) + " ▾");
        for (var button : new Button[]{scene, target, play}) EditorWidgets.enabled(button, host.actionPreview().canPlay());
        play.setText(EditorWidgets.tr(host.actionPreview().playing() ? "preview.restart" : "action.preview_play"));
        EditorWidgets.enabled(stop, host.actionPreview().playing());
    }
}
