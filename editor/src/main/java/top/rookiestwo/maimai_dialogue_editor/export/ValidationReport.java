package top.rookiestwo.maimai_dialogue_editor.export;

import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import java.util.List;

public record ValidationReport(ProjectDraft source, List<ValidationIssue> issues, List<String> dependencies) {
    public ValidationReport {
        issues = List.copyOf(issues);
        dependencies = List.copyOf(dependencies);
    }
    public boolean valid() { return issues.isEmpty(); }
}
