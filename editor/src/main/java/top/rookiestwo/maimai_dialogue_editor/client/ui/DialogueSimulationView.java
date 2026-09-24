package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.*;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.preview.PreviewScenario;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import java.util.List;

// 仅修改项目本地的预览条件，不进入剧情草稿和导出内容。
final class DialogueSimulationView extends LinearLayout {
    private final ProjectWorkspace project;
    private final EditorPreviewHost preview;
    private final EditorPropertySection section;
    private final EditorTextField nodes;
    private final TextView results;
    private final Button outcome, skip;
    private long generation = -1;
    private boolean refreshing;
    DialogueSimulationView(Context context, ProjectWorkspace project, EditorPreviewHost preview, ChoicePresenter choices, EditorLayoutState layout) {
        super(context); this.project = project; this.preview = preview; setOrientation(VERTICAL);
        section = new EditorPropertySection(context, "simulation.title", layout); addView(section);
        var edit = new EditorTextBinding(() -> String.join("\n", project.simulation().progress()),
                () -> active() && !refreshing, text -> {
                    try { project.simulation(project.simulation().withProgress(text)); return ""; }
                    catch (RuntimeException invalid) { return "simulation.invalid_nodes"; }
                }, () -> {}).commitUnchanged().keepErrorOnFocus();
        nodes = new EditorTextField(context, edit);
        nodes.input().setSingleLine(false); nodes.input().setMinLines(2); nodes.input().setMaxLines(5);
        EditorWidgets.propertyRow(section.body(), "simulation.nodes", nodes.input(), true);
        section.body().addView(nodes.error());
        outcome = EditorWidgets.fieldButton(context, "", () -> {});
        outcome.setOnClickListener(view -> { if (active()) {
            long expected = generation;
            choices.show(outcome, List.of(new ChoicePresenter.Item("false", EditorWidgets.tr("simulation.success")),
                    new ChoicePresenter.Item("true", EditorWidgets.tr("simulation.failure"))), Boolean.toString(project.simulation().commandFailure()), value -> {
                if (active() && generation == expected) {
                    var current = project.simulation(); project.simulation(new PreviewScenario(current.progress(), current.texts(), Boolean.parseBoolean(value)));
                }
            });
        }});
        EditorWidgets.propertyRow(section.body(), "simulation.command_result", outcome, false);
        var row = new EditorActionRow(context); section.body().addView(row);
        skip = EditorWidgets.button(context, "simulation.skip", preview::simulateSkip); row.addView(skip);
        results = EditorWidgets.compactParagraph(context, ""); results.setTextIsSelectable(true); section.body().addView(results);
        preview.setSimulationListener(this::refreshResults);
    }
    private boolean active() {
        var key = project.resources().selection().owner();
        return isAttachedToWindow() && project.content().active() && generation == project.projectGeneration()
                && key != null && key.kind() == ResourceKind.DIALOGUE && key.equals(project.resources().opened()) && !project.actions().inspecting();
    }
    void refresh() {
        refreshing = true;
        try {
            if (generation != project.projectGeneration()) { clearFocus(); generation = project.projectGeneration(); nodes.reset(); }
            nodes.refresh(active()); EditorWidgets.enabled(outcome, active());
            outcome.setText(EditorWidgets.tr(project.simulation().commandFailure() ? "simulation.failure" : "simulation.success") + " ▾");
            section.refresh(); refreshResults();
        } finally { refreshing = false; }
    }
    private void refreshResults() {
        EditorWidgets.enabled(skip, active() && preview.canSimulateSkip());
        String text = preview.simulationResults().stream().map(value -> EditorWidgets.tr("simulation." + value.kind()) + " · " + value.detail())
                .collect(java.util.stream.Collectors.joining("\n"));
        results.setText(text); results.setVisibility(text.isEmpty() ? GONE : VISIBLE);
    }
}
