package top.rookiestwo.maimai_dialogue.content;

import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

public record DefinitionLoadResult<T>(
        DefinitionRegistry<T> registry,
        List<DefinitionLoadIssue> issues
) {
    public DefinitionLoadResult {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(issues, "issues");
        issues = List.copyOf(issues);
    }

    // 统一记录当前 definition 类型的所有加载错误。
    public void logIssues(Logger logger, DefinitionType<T> type) {
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(type, "type");
        for (DefinitionLoadIssue issue : issues) {
            logger.error(
                    "Failed to load {} resource {}: {}",
                    type.displayName(),
                    issue.resourceId(),
                    issue.message()
            );
        }
    }
}
