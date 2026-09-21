package top.rookiestwo.maimai_dialogue_editor.export;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.resource.ClientContentSnapshot;
import top.rookiestwo.maimai_dialogue.content.resolve.SceneResolver;
import top.rookiestwo.maimai_dialogue.dialogue.*;
import top.rookiestwo.maimai_dialogue.dialogue.branch.*;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneActionCall;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerOperation;
import top.rookiestwo.maimai_dialogue_editor.content.ProjectContentSnapshot;
import top.rookiestwo.maimai_dialogue_editor.content.ProjectDefinitions;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import top.rookiestwo.maimai_dialogue_editor.material.MaterialPack;

import java.util.*;

/** Validates an immutable snapshot without modifying drafts or running a playback session. */
public final class ProjectValidator {
    private final List<ValidationIssue> issues = new ArrayList<>();
    private final Set<String> dependencies = new TreeSet<>();

    public static ValidationReport validate(ProjectDraft draft, ClientContentSnapshot external) {
        return validate(draft, external, MaterialPack.External.EMPTY);
    }
    public static ValidationReport validate(ProjectDraft draft, ClientContentSnapshot external, MaterialPack.External media) {
        return new ProjectValidator().run(draft, external, media);
    }

    private ValidationReport run(ProjectDraft draft, ClientContentSnapshot external, MaterialPack.External media) {
        if (draft.name().isBlank()) issue(null, "name", "required", "");
        if (!draft.namespace().matches("[a-z0-9_.-]+") || !portablePath(draft.namespace())) {
            issue(null, "namespace", "invalid_id", draft.namespace());
        }
        JsonObject groups = draft.resources();
        MaterialPack.validate(draft).forEach(problem -> issue(problem.resource(), problem.field(), "material", problem.message()));
        Map<ResourceKey, JsonElement> definitions = new LinkedHashMap<>();
        for (var group : groups.entrySet()) {
            ResourceKind kind = Arrays.stream(ResourceKind.values()).filter(k -> k.directory().equals(group.getKey()))
                    .findFirst().orElse(null);
            if (kind != null && kind.material()) {
                if (!group.getValue().isJsonObject()) issue(null, "resources." + group.getKey(), "object", "");
                continue;
            }
            if (kind == null || ProjectDefinitions.type(kind) == null) {
                if (!group.getValue().isJsonObject() || !group.getValue().getAsJsonObject().isEmpty())
                    issue(null, "resources." + group.getKey(), "unsupported", "");
                continue;
            }
            if (!group.getValue().isJsonObject()) {
                issue(null, "resources." + group.getKey(), "object", "");
                continue;
            }
            for (var entry : group.getValue().getAsJsonObject().entrySet()) {
                ResourceKey key = new ResourceKey(kind, entry.getKey());
                if (!ResourceKey.validPath(key.path()) || !portablePath(key.path())) {
                    issue(key, "$id", "invalid_id", key.path());
                    continue;
                }
                definitions.put(key, entry.getValue());
                validateDefinition(key, entry.getValue());
            }
        }
        // JSON filename/parent directory conflicts cannot be represented by an installed pack.
        Set<String> files = new HashSet<>();
        definitions.keySet().forEach(k -> files.add(k.kind().directory() + "/" + k.path() + ".json"));
        definitions.keySet().forEach(k -> {
            String path = k.kind().directory() + "/" + k.path() + ".json";
            int slash = path.indexOf('/');
            while (slash >= 0) {
                if (files.contains(path.substring(0, slash))) { issue(k, "$id", "path_conflict", path); break; }
                slash = path.indexOf('/', slash + 1);
            }
        });
        if (!issues.stream().anyMatch(i -> i.resource() == null)) {
            ProjectContentSnapshot content = new ProjectContentSnapshot(draft, external);
            for (var entry : definitions.entrySet()) {
                ResourceKey key = entry.getKey();
                for (var reference : ResourceReferences.scan(key.kind(), entry.getValue())) {
                    ResourceLocation id = ResourceLocation.tryParse(reference.id());
                    if (id == null || id.getPath().isEmpty()) { issue(key, reference.field(), "invalid_id", reference.id()); continue; }
                    try {
                        boolean found = reference.kind().material()
                                ? id.getNamespace().equals(draft.namespace()) ? MaterialPack.contains(draft, reference.kind(), id.toString())
                                : (reference.kind() == ResourceKind.IMAGE ? media.images() : media.sounds()).contains(id.toString())
                                : exists(content, reference.kind(), id);
                        if (!found) issue(key, reference.field(), "missing_reference", reference.id());
                        else if (!id.getNamespace().equals(draft.namespace())) dependencies.add(reference.id());
                    } catch (RuntimeException failure) {
                        issue(key, reference.field(), "invalid_reference", failure.getMessage());
                    }
                }
                if ((key.kind() == ResourceKind.DIALOGUE || key.kind() == ResourceKind.SCENE)
                        && issues.stream().noneMatch(i -> key.equals(i.resource()))) {
                    boolean dialogue = key.kind() == ResourceKind.DIALOGUE;
                    String field = dialogue ? "scene" : "$";
                    try {
                        var id = ResourceLocation.parse(key.id(draft.namespace()));
                        var resolved = dialogue
                                ? SceneResolver.resolve(content.dialogue(id).orElseThrow().scene(), content::scene, content::theme, content::visualAsset)
                                : SceneResolver.resolve(content.scene(id).orElseThrow(), content::theme, content::visualAsset);
                        if (resolved.missingTheme()) issue(key, dialogue ? field : "theme", "missing_reference", resolved.source().theme().toString());
                        if (!resolved.source().theme().getNamespace().equals(draft.namespace())) dependencies.add(resolved.source().theme().toString());
                        for (String error : resolved.sceneErrors()) issue(key, field, "invalid_reference", error);
                        for (String error : resolved.visualErrors()) issue(key, dialogue ? field
                                : error.startsWith("Background") ? "background" : "visual_objects", "invalid_reference", error);
                    } catch (RuntimeException failure) { issue(key, field, "invalid_reference", failure.getMessage()); }
                }
            }
        }
        return new ValidationReport(draft, issues, new ArrayList<>(dependencies));
    }

    /** Windows-compatible segments also prevent traversal and device-file output. */
    static boolean portablePath(String path) {
        if (path.isEmpty()) return false;
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.endsWith(".")) return false;
            String base = segment.split("\\.", 2)[0];
            if (base.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])")) return false;
        }
        return true;
    }

    private void validateDefinition(ResourceKey key, JsonElement json) {
        if (!json.isJsonObject()) { issue(key, "$", "object", ""); return; }
        int before = issues.size();
        JsonObject object = json.getAsJsonObject();
        if (key.kind() == ResourceKind.THEME) {
            top.rookiestwo.maimai_dialogue_editor.document.ThemeFields.errors(object)
                    .forEach((field, reason) -> issue(key, field, reason.equals("edit.invalid_object") ? "object"
                            : reason.equals("scene.invalid_color") ? "theme_color" : "theme_number", ""));
        } else if (key.kind() == ResourceKind.SPEAKER) {
            requiredText(key, object.get("name"), "name");
        } else if (key.kind() == ResourceKind.SCENE) {
            if (object.has("background")) check(key, object.get("background"), "background",
                    top.rookiestwo.maimai_dialogue.presentation.scene.SceneBackground.CODEC);
            if (object.has("theme")) check(key, object.get("theme"), "theme", ResourceLocation.CODEC);
            if (object.has("dialogue_box")) check(key, object.get("dialogue_box"), "dialogue_box",
                    top.rookiestwo.maimai_dialogue.presentation.DialogueBoxLayout.CODEC);
        } else if (key.kind() == ResourceKind.DIALOGUE) {
            check(key, object.get("scene"), "scene", ResourceLocation.CODEC);
            JsonElement steps = object.get("steps");
            if (steps != null) {
                if (!steps.isJsonArray()) issue(key, "steps", "array", "");
                else for (int i = 0; i < steps.getAsJsonArray().size(); i++)
                    step(key, steps.getAsJsonArray().get(i), "steps[" + i + "]", false);
            }
            step(key, object.get("end"), "end", true);
        }
        // Full runtime validation remains authoritative, including fields whose editors come later.
        String error = codecError(ProjectDefinitions.type(key.kind()).codec(), json);
        if (error != null && before == issues.size()) issue(key, "$", "codec", error);
    }

    private void step(ResourceKey key, JsonElement json, String path, boolean end) {
        if (json == null) { issue(key, path, "required", ""); return; }
        if (!json.isJsonObject()) { issue(key, path, "object", ""); return; }
        int before = issues.size();
        JsonObject node = json.getAsJsonObject();
        if (node.has("text")) check(key, node.get("text"), path + ".text", DialogueText.CODEC);
        if (node.has("speaker")) {
            JsonElement speaker = node.get("speaker");
            if (speaker.isJsonObject() && "set".equals(text(speaker.getAsJsonObject().get("type"))))
                check(key, speaker.getAsJsonObject().get("id"), path + ".speaker.id", ResourceLocation.CODEC);
            else check(key, speaker, path + ".speaker", SpeakerOperation.CODEC);
        }
        if (node.has("actions")) check(key, node.get("actions"), path + ".actions", SceneActionCall.CODEC.listOf());
        if (end) {
            JsonElement exit = node.get("exit");
            if (exit != null && exit.isJsonObject() && "options".equals(text(exit.getAsJsonObject().get("type")))) {
                JsonElement options = exit.getAsJsonObject().get("options");
                if (options != null && options.isJsonArray()) for (int i = 0; i < options.getAsJsonArray().size(); i++) {
                    JsonElement option = options.getAsJsonArray().get(i);
                    String location = path + ".exit.options[" + i + "]";
                    if (option.isJsonObject()) {
                        var data = option.getAsJsonObject();
                        int optionBefore = issues.size();
                        requiredText(key, data.get("text"), location + ".text");
                        target(key, data.get("target"), location + ".target", OptionTarget.CODEC);
                        if (data.has("icon")) check(key, data.get("icon"), location + ".icon", OptionIcon.CODEC);
                        if (issues.size() == optionBefore) check(key, option, location, DialogueOption.CODEC);
                    } else {
                        check(key, option, location, DialogueOption.CODEC);
                    }
                }
            }
            if (issues.size() == before) target(key, exit, path + ".exit", DialogueExit.CODEC);
        }
        String error = codecError(end ? DialogueEnd.CODEC : DialogueStep.CODEC, json);
        if (error != null && issues.size() == before) issue(key, path, "codec", error);
    }

    private void requiredText(ResourceKey key, JsonElement value, String path) {
        if (text(value).isBlank()) issue(key, path, "required", "");
    }
    private void target(ResourceKey key, JsonElement value, String path, Codec<?> codec) {
        if (value != null && value.isJsonObject() && "dialogue".equals(text(value.getAsJsonObject().get("type"))))
            check(key, value.getAsJsonObject().get("dialogue"), path + ".dialogue", ResourceLocation.CODEC);
        else check(key, value, path, codec);
    }
    private static String text(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }
    private void check(ResourceKey key, JsonElement value, String path, Codec<?> codec) {
        if (value == null) { issue(key, path, "required", ""); return; }
        String error = codecError(codec, value);
        if (error != null) issue(key, path, "codec", error);
    }
    private static String codecError(Codec<?> codec, JsonElement value) {
        try { return codec.parse(JsonOps.INSTANCE, value).error().map(e -> e.message()).orElse(null); }
        catch (RuntimeException failure) { return String.valueOf(failure.getMessage()); }
    }
    private static boolean exists(ProjectContentSnapshot content, ResourceKind kind, ResourceLocation id) {
        return switch (kind) {
            case DIALOGUE -> content.dialogue(id).isPresent();
            case SPEAKER -> content.speaker(id).isPresent();
            case THEME -> content.theme(id).isPresent();
            case SCENE -> content.scene(id).isPresent();
            case VISUAL_ASSET -> content.visualAsset(id).isPresent();
            case ACTION -> content.action(id).isPresent();
            default -> false;
        };
    }
    private void issue(ResourceKey key, String field, String reason, String detail) {
        var issue = new ValidationIssue(key, field, reason, detail == null ? "" : detail);
        if (!issues.contains(issue)) issues.add(issue);
    }
}
