package top.rookiestwo.maimai_dialogue_editor.export;

import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;

/** A null resource identifies project metadata; field uses runtime JSON paths. */
public record ValidationIssue(ResourceKey resource, String field, String reason, String detail) {}
