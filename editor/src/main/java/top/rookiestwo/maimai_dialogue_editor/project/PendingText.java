package top.rookiestwo.maimai_dialogue_editor.project;

import top.rookiestwo.maimai_dialogue_editor.document.ContentCursor;

import com.google.gson.JsonObject;
import top.rookiestwo.maimai_dialogue_editor.document.ContentTextField;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;

/** Immutable input buffer; applying it never changes the preview draft or its undo history. */
record PendingText(ResourceKey key, ProjectResource base, ContentCursor cursor,
                   ContentTextField field, String text) {
    boolean appliesTo(ProjectDraft draft) { return draft != null && base == draft.revision(key); }

    ProjectDraft apply(ProjectDraft draft) {
        if (!appliesTo(draft)) return draft;
        if (draft.resource(key) instanceof JsonObject data && field.apply(key.kind(), data, cursor, text))
            return draft.withResource(key, data);
        return draft;
    }
}
