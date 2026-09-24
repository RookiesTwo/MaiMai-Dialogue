package top.rookiestwo.maimai_dialogue_editor.document;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import top.rookiestwo.maimai_dialogue_editor.document.edit.EditOrigin;
import top.rookiestwo.maimai_dialogue_editor.project.*;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.util.*;
import java.util.function.Consumer;

// 主题草稿、校验和临时手势独立于 View，不更改游戏全局主题。
public final class ThemeWorkspace {
    private final ProjectWorkspace project;
    private ProjectResource revision;
    private ThemeDefinition decoded;
    private String error = "";
    private Gesture gesture;
    private Consumer<Boolean> liveChanged = immediate -> {};
    public ThemeWorkspace(ProjectWorkspace project) { this.project = project; }
    public ContentWorkspace.Snapshot snapshot() { return project.content().snapshot(); }
    public boolean active() {
        var state = snapshot();
        return project.content().active() && state.key() != null && state.key().kind() == ResourceKind.THEME
                && state.key().equals(project.resources().selection().resource()) && state.data() != null;
    }
    public String value(ThemeFields.Field field) {
        if (gesture != null && gesture.valid() && gesture.field.equals(field) && gesture.changed)
            return gesture.value == null ? field.fallback() : gesture.value.getAsString();
        var data = snapshot().data();
        return SceneWorkspace.text(data == null ? null : SceneWorkspace.object(data.get(field.group())), field.name(), field.fallback());
    }
    public void setLiveListener(Consumer<Boolean> listener) { liveChanged = Objects.requireNonNull(listener); }
    public ThemeDefinition preview() {
        var state = snapshot();
        var next = project.draft() == null || state.key() == null || state.key().kind() != ResourceKind.THEME
                ? null : project.draft().revision(state.key());
        if (next != revision) {
            revision = next; decoded = null; error = "";
            if (next != null && state.data() != null) {
                var errors = ThemeFields.errors(state.data());
                if (!errors.isEmpty()) error = String.join(", ", errors.keySet());
                else {
                    var parsed = ThemeDefinition.CODEC.parse(JsonOps.INSTANCE, state.data());
                    decoded = parsed.result().orElse(null);
                    error = parsed.error().map(failure -> failure.message()).orElse("");
                }
            } else if (next != null) error = "$";
        }
        if (gesture != null && !gesture.valid()) gesture = null;
        if (decoded == null || gesture == null || !gesture.changed) return decoded;
        var theme = ThemeFields.apply(decoded, gesture.field, gesture.value);
        if (gesture.field.group().equals("spacing")) {
            // 运行时会提升展开上限；临时编辑仍以草稿里原始的两个上限共同计算。
            int collapsed = limitValue("options_collapsed_limit");
            int expanded = limitValue("options_expanded_limit");
            var spacing = theme.spacing();
            theme = new ThemeDefinition(theme.box(), theme.text(), theme.option(),
                    new top.rookiestwo.maimai_dialogue.theme.ThemeSpacing(spacing.headerHorizontalDp(), spacing.headerVerticalDp(),
                            spacing.contentHorizontalDp(), spacing.contentVerticalDp(), spacing.optionsPaddingDp(), collapsed, expanded), theme.controls());
        }
        return theme;
    }
    private int limitValue(String name) {
        var field = ThemeFields.ALL.stream().filter(item -> item.group().equals("spacing") && item.name().equals(name)).findFirst().orElseThrow();
        return Integer.parseInt(value(field));
    }
    public String error() { preview(); return error; }
    public String set(ThemeFields.Field field, String raw) {
        if (!active()) return "";
        JsonPrimitive value;
        try { value = field.parse(raw); } catch (RuntimeException invalid) { return field.error(); }
        gesture = null;
        var state = snapshot(); var next = state.data().deepCopy();
        var group = SceneWorkspace.object(next.get(field.group()));
        if (group == null) group = new JsonObject();
        if (value == null) group.remove(field.name()); else group.add(field.name(), value);
        if (group.isEmpty()) next.remove(field.group()); else next.add(field.group(), group);
        if (!next.equals(state.data())) project.editAsset(state.key(), next, field.path());
        return "";
    }
    public void resetGroup(String group) {
        if (!active()) return;
        endGesture(false); project.endEdit();
        var state = snapshot(); var next = state.data().deepCopy();
        if (next.get(group) instanceof JsonObject section) {
            ThemeFields.ALL.stream().filter(field -> field.group().equals(group)).forEach(field -> section.remove(field.name()));
            if (section.isEmpty()) next.remove(group);
        } else next.remove(group);
        if (!next.equals(state.data())) project.editAsset(state.key(), next, null);
        project.endEdit();
    }
    public EditGesture beginGesture(ThemeFields.Field field) {
        endGesture(false);
        if (!active()) return null;
        project.endEdit(); return gesture = new Gesture(field);
    }
    public boolean editing() { return gesture != null && gesture.valid(); }
    public void endGesture(boolean commit) { if (gesture != null) gesture.finish(commit); }
    private final class Gesture implements EditGesture {
        private final EditOrigin origin = new EditOrigin(project.draft(), project.projectGeneration());
        private final ResourceKey key = snapshot().key();
        private final ThemeFields.Field field;
        private final String original;
        private JsonPrimitive value;
        private boolean changed;
        Gesture(ThemeFields.Field field) { this.field = field; original = value(field); }
        boolean valid() { return gesture == this && origin.matches(project.draft(), project.projectGeneration())
                && active() && key.equals(snapshot().key()); }
        @Override public boolean update(String raw) {
            if (!valid()) return false;
            try { value = field.parse(raw); } catch (RuntimeException invalid) { return false; }
            changed = true; liveChanged.accept(false); return true;
        }
        @Override public void finish(boolean commit) {
            if (gesture != this) return;
            boolean valid = valid();
            if (commit && valid && changed) liveChanged.accept(true);
            gesture = null;
            String result = value == null ? field.fallback() : value.getAsString();
            if (commit && valid && changed && !original.equals(result)) {
                set(field, value == null ? "" : result); project.endEdit();
            }
            liveChanged.accept(false);
        }
    }
}
