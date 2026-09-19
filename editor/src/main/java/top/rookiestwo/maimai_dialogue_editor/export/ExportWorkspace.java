package top.rookiestwo.maimai_dialogue_editor.export;

import top.rookiestwo.maimai_dialogue.client.resource.ClientContentSnapshot;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Per-open editor validation/export state; UI callbacks never publish another project's results. */
public final class ExportWorkspace {
    public record Environment(ClientContentSnapshot content, int resourceFormat, int dataFormat) {}
    private final ProjectWorkspace project;
    private final Executor io, ui;
    private final Supplier<CompletableFuture<Environment>> environment;
    private final PackExporter exporter;
    private Runnable changed = () -> {};
    private boolean busy, disposed;
    private ValidationReport report;
    private ProjectDraft source;
    private Path directory, output;
    private String error = "";

    public ExportWorkspace(ProjectWorkspace project, Executor io, Executor ui,
                           Supplier<CompletableFuture<Environment>> environment, PackExporter exporter) {
        this.project = project; this.io = io; this.ui = ui; this.environment = environment; this.exporter = exporter;
    }
    public void setListener(Runnable changed) { this.changed = Objects.requireNonNull(changed); }
    public boolean busy() { return busy; }
    public boolean canRun() { return !disposed && !busy && !project.busy() && project.draft() != null; }
    private boolean current() { return source == project.draft() && Objects.equals(directory, project.directory()); }
    public ValidationReport report() { return current() ? report : null; }
    public Path output() { return current() ? output : null; }
    public String error() { return current() ? error : ""; }
    public String status() {
        if (busy) return "export.working";
        if (!current()) return "export.unchecked";
        if (!error.isEmpty()) return "export.failed";
        if (output != null) return "export.complete";
        if (report == null) return "export.unchecked";
        return report.valid() ? "export.valid" : "export.invalid";
    }
    public void validate() { run(false); }
    public void export() { run(true); }

    private void run(boolean write) {
        if (!canRun()) return;
        project.endEdit();
        source = project.draft();
        directory = project.directory();
        ProjectDraft captured = source;
        busy = true;
        report = null;
        output = null;
        error = "";
        changed.run();
        try {
            environment.get().whenComplete((env, failure) -> {
                if (failure != null) { finish(null, null, failure); return; }
                try {
                    io.execute(() -> {
                        ValidationReport checked = null;
                        Path written = null;
                        Throwable error = null;
                        try {
                            checked = ProjectValidator.validate(captured, env.content());
                            if (write && checked.valid()) written = exporter.export(checked, env.resourceFormat(), env.dataFormat());
                        } catch (Exception exception) { error = exception; }
                        finish(checked, written, error);
                    });
                } catch (RuntimeException exception) { finish(null, null, exception); }
            });
        } catch (RuntimeException failure) { finish(null, null, failure); }
    }

    private void finish(ValidationReport checked, Path written, Throwable failure) {
        ui.execute(() -> {
            if (disposed) return;
            busy = false;
            report = checked;
            output = written;
            error = failure == null ? "" : String.valueOf(failure.getMessage());
            changed.run();
        });
    }
    public void dispose() { disposed = true; changed = () -> {}; }
}
