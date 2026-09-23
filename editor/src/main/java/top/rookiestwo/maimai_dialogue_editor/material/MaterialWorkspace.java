package top.rookiestwo.maimai_dialogue_editor.material;

import com.google.gson.*;
import top.rookiestwo.maimai_dialogue_editor.project.*;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** File-picker state, asset edits and automatic asset synchronization, independent of Views. UI-thread owner. */
public final class MaterialWorkspace {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public interface PreviewAssets {
        CompletableFuture<String> refresh(ProjectDraft draft);
        void clear();
    }
    private final ProjectWorkspace project;
    private final Executor io, ui;
    private final Runnable changed;
    private PreviewAssets preview;
    private MaterialFilePicker picker;
    private Path lastDirectory, projectDirectory;
    private final Set<Path> selected = new LinkedHashSet<>();
    private String target = "", error = "", loadedSignature = "", previewNamespace = "";
    private String folder = "";
    private String refreshError = "";
    private String attemptedSignature = "";
    private boolean refreshQueued;
    private ResourceKey replacement;
    private long pickerRevision, refreshRevision, loadedRevision;
    private long projectGeneration;
    private boolean selectingFiles, refreshing, disposed;
    private final Map<ResourceKey, String> variants = new HashMap<>();

    public MaterialWorkspace(ProjectWorkspace project, Executor io, Executor ui, Runnable changed, Path initialDirectory) {
        this.project = project; this.io = io; this.ui = ui; this.changed = changed; lastDirectory = initialDirectory;
    }
    public void setPreview(PreviewAssets preview) { this.preview = preview; synchronize(); }
    public boolean choosing() { return project.page() == ProjectWorkspace.Page.IMPORT; }
    public void setFilePicker(MaterialFilePicker picker) { this.picker = Objects.requireNonNull(picker); }
    public boolean selectingFiles() { return selectingFiles; }
    public List<Path> selectedFiles() { return List.copyOf(selected); }
    public boolean batch() { return selected.size() > 1; }
    public String target() { return batch() ? folder : target; }
    public String error() { return error; }
    public String refreshError() { return refreshError; }
    public void reportLoadFailure(String message) {
        if (disposed || project.draft() == null) return;
        refreshError = message;
        changed.run();
    }
    public boolean replacing() { return replacement != null; }
    public String previewNamespace() { return previewNamespace; }
    public long loadedRevision() { return loadedRevision; }
    public boolean refreshing() { return refreshing; }
    public String status() {
        if (refreshing) return "material.refreshing";
        if (!refreshError.isEmpty()) return "material.failed";
        if (project.draft() == null || project.draft().resourceKeys().stream().noneMatch(key -> key.kind().material()))
            return "material.empty";
        if (!loadedSignature.equals(MaterialPack.signature(project.draft()))) return "material.not_loaded";
        return "material.loaded";
    }
    public boolean canRefresh() {
        return !disposed && preview != null && !refreshing && !project.busy() && project.draft() != null
                && project.page() == ProjectWorkspace.Page.NONE && project.resources().form() == ResourceWorkspace.Form.NONE;
    }
    public void synchronize() {
        if (disposed) return;
        if (!Objects.equals(projectDirectory, project.directory()) || projectGeneration != project.projectGeneration()) {
            projectDirectory = project.directory();
            projectGeneration = project.projectGeneration();
            ++refreshRevision; cancelSelection(); selected.clear();
            loadedSignature = ""; attemptedSignature = ""; previewNamespace = ""; loadedRevision++;
            refreshing = false; error = ""; refreshError = ""; variants.clear();
            if (preview != null) preview.clear();
        }
        if (preview == null || project.draft() == null || project.busy() || refreshing || refreshQueued
                || attemptedSignature.equals(MaterialPack.signature(project.draft()))) return;
        refreshQueued = true;
        // Collapse changes queued in the same UI turn; a running preparation is followed by only the latest draft.
        ui.execute(() -> {
            refreshQueued = false;
            if (!disposed && preview != null && project.draft() != null && !project.busy() && !refreshing
                    && !attemptedSignature.equals(MaterialPack.signature(project.draft()))) refreshNow();
        });
    }
    public void begin(ResourceKey replace) {
        if (!canSelectFiles() || project.page() != ProjectWorkspace.Page.NONE) return;
        if (replace != null && !replace.kind().material()) return;
        replacement = replace; selected.clear(); target = replace == null ? "" : replace.path(); folder = ""; error = "";
        chooseFiles();
    }
    public boolean canSelectFiles() {
        return !disposed && picker != null && !selectingFiles && !project.busy() && project.draft() != null
                && project.resources().form() == ResourceWorkspace.Form.NONE
                && (project.page() == ProjectWorkspace.Page.NONE || choosing());
    }
    public void chooseFiles() {
        if (!canSelectFiles()) return;
        long expected = ++pickerRevision;
        long generation = project.projectGeneration();
        Path directory = project.directory();
        ProjectWorkspace.Page page = project.page();
        ResourceKind kind = replacement == null ? null : replacement.kind();
        selectingFiles = true;
        changed.run();
        CompletableFuture<List<Path>> pending;
        try { pending = picker.choose(new MaterialFilePicker.Request(lastDirectory, kind)); }
        catch (RuntimeException failure) { pending = CompletableFuture.failedFuture(failure); }
        pending.thenApplyAsync(paths -> {
            try { return MaterialFiles.validateSelection(paths, kind); }
            catch (java.io.IOException failure) { throw new CompletionException(failure); }
        }, io).whenComplete((files, failure) -> ui.execute(() -> {
            if (disposed || expected != pickerRevision) return;
            selectingFiles = false;
            if (generation != project.projectGeneration() || !Objects.equals(directory, project.directory())
                    || project.busy() || project.page() != page || project.resources().form() != ResourceWorkspace.Form.NONE) {
                changed.run(); return;
            }
            if (failure != null) {
                Throwable cause = failure;
                while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                error = String.valueOf(cause.getMessage());
                LOGGER.error("Failed to choose editor material files", cause);
                project.showImport();
            } else if (!files.isEmpty()) {
                boolean different = !List.copyOf(selected).equals(files);
                selected.clear(); selected.addAll(files);
                lastDirectory = files.getFirst().getParent();
                error = "";
                if (!replacing() && !batch() && different) {
                    Path source = files.getFirst();
                    target = project.resources().catalog().unusedPath(MaterialFiles.kind(source), suggestedPath(source));
                }
                project.showImport();
            } else changed.run();
        }));
    }
    /** Native dialogs cannot be dismissed from the model; their eventual result must not affect another operation. */
    public void cancelSelection() { ++pickerRevision; selectingFiles = false; }
    private static String suggestedPath(Path source) {
        String filename = source.getFileName().toString();
        return ProjectNames.suggestNamespace(filename.substring(0, filename.lastIndexOf('.')));
    }
    public void setTarget(String value) {
        if (!choosing() || project.busy() || replacing()) return;
        if (batch()) folder = value; else target = value;
        changed.run();
    }
    public void submit() {
        if (!choosing() || selected.isEmpty() || project.busy() || selectingFiles) return;
        if (batch() ? !folder.isEmpty() && !MaterialPack.portable(folder) : !MaterialPack.portable(target)) {
            error = "Invalid resource path"; changed.run(); return;
        }
        List<MaterialFiles.ImportRequest> requests = new ArrayList<>();
        Set<ResourceKey> reserved = new HashSet<>(project.draft().resourceKeys());
        for (Path source : selected) {
            ResourceKind kind = MaterialFiles.kind(source);
            String path = batch() ? (folder.isEmpty() ? "" : folder + "/") + suggestedPath(source) : target;
            if (batch()) {
                String seed = path;
                for (int suffix = 2; reserved.contains(new ResourceKey(kind, path)); suffix++) path = seed + "_" + suffix;
            }
            ResourceKey key = new ResourceKey(kind, path);
            if (!replacing() && !reserved.add(key)) { error = "Resource already exists"; changed.run(); return; }
            requests.add(new MaterialFiles.ImportRequest(source, key));
        }
        project.importMaterials(requests, replacing());
    }
    public void refresh() {
        if (!canRefresh()) return;
        refreshNow();
    }
    private void refreshNow() {
        ProjectDraft draft = project.draft(); Path directory = project.directory();
        long expected = ++refreshRevision; String signature = MaterialPack.signature(draft);
        attemptedSignature = signature;
        refreshing = true; refreshError = ""; changed.run();
        CompletableFuture<String> future;
        try { future = preview.refresh(draft); }
        catch (RuntimeException failure) { future = CompletableFuture.failedFuture(failure); }
        future.whenComplete((namespace, failure) -> ui.execute(() -> {
            if (disposed || expected != refreshRevision || !Objects.equals(directory, project.directory())) return;
            refreshing = false;
            if (!signature.equals(MaterialPack.signature(project.draft()))) {
                changed.run(); // synchronize schedules the newest revision; never publish an obsolete completion.
                return;
            }
            if (failure == null) { previewNamespace = namespace; loadedSignature = signature; loadedRevision++; }
            else {
                Throwable cause = failure;
                while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                refreshError = String.valueOf(cause.getMessage());
                if (!(cause instanceof CancellationException))
                    LOGGER.error("Failed to refresh editor materials for project {} at {}", draft.namespace(), directory, cause);
            }
            changed.run();
        }));
    }
    public String variant(ResourceKey key, JsonObject data) {
        JsonObject values = data == null || !(data.get("variants") instanceof JsonObject v) ? null : v;
        if (values == null || values.isEmpty()) return "";
        String selected = variants.get(key);
        return selected != null && values.has(selected) ? selected : values.keySet().iterator().next();
    }
    public void selectVariant(String value) {
        var state = project.content().snapshot();
        if (state.key() == null) return;
        variants.put(state.key(), value); project.endEdit(); changed.run();
    }
    public List<EditorSessionState.Choice> variantPreferences() {
        return variants.entrySet().stream().filter(entry -> project.draft() != null && project.draft().revision(entry.getKey()) != null)
                .map(entry -> new EditorSessionState.Choice(entry.getKey(), entry.getValue())).toList();
    }
    public void restoreVariantPreferences(List<EditorSessionState.Choice> preferences) {
        synchronize(); variants.clear();
        for (var entry : preferences) if (entry.resource().kind() == ResourceKind.VISUAL_ASSET && project.draft().revision(entry.resource()) != null)
            variants.put(entry.resource(), entry.value());
    }
    public void addVariant() {
        edit(ResourceKind.VISUAL_ASSET, null, data -> {
            JsonObject values = data.getAsJsonObject("variants");
            String name = "variant";
            for (int i = 2; values.has(name); i++) name = "variant_" + i;
            values.addProperty(name, "");
            variants.put(project.resources().opened(), name);
        });
    }
    public void deleteVariant() {
        edit(ResourceKind.VISUAL_ASSET, null, data -> data.getAsJsonObject("variants")
                .remove(variant(project.resources().opened(), data)));
    }
    /** A completed name edit. The View keeps intermediate text private until focus leaves the field. */
    public String renameVariant(String name) {
        if (!name.matches("[a-z0-9_-]+")) return "material.variant_name.invalid";
        var selected = project.content().snapshot();
        if (selected.key() == null || selected.key().kind() != ResourceKind.VISUAL_ASSET || selected.data() == null
                || !(selected.data().get("variants") instanceof JsonObject existing)) return "";
        String current = variant(selected.key(), selected.data());
        if (current.equals(name)) return "";
        if (existing.has(name)) return "material.variant_name.duplicate";
        edit(ResourceKind.VISUAL_ASSET, "variant_name", data -> {
            var key = project.resources().opened();
            String old = variant(key, data);
            JsonObject values = data.getAsJsonObject("variants");
            if (!values.has(old) || old.equals(name) || values.has(name)) return;
            JsonObject next = new JsonObject();
            values.entrySet().forEach(e -> next.add(e.getKey().equals(old) ? name : e.getKey(), e.getValue()));
            data.add("variants", next); variants.put(key, name);
        });
        return "";
    }
    public void setVariantImage(String image) {
        edit(ResourceKind.VISUAL_ASSET, "variant_image", data -> {
            String selected = variant(project.resources().opened(), data);
            if (data.getAsJsonObject("variants").has(selected)) data.getAsJsonObject("variants").addProperty(selected, image);
        });
    }
    public void setSampling(String sampling) {
        if (Set.of("linear", "nearest").contains(sampling))
            edit(ResourceKind.VISUAL_ASSET, null, data -> data.addProperty("sampling", sampling));
    }
    public void setEvent(String event) { edit(ResourceKind.SOUND, "event", data -> data.addProperty("event", event)); }
    public void setStream(boolean stream) { edit(ResourceKind.SOUND, null, data -> data.addProperty("stream", stream)); }
    private void edit(ResourceKind kind, String group, Consumer<JsonObject> operation) {
        if (!project.content().active()) return;
        var key = project.resources().opened();
        if (key == null || key.kind() != kind || !key.equals(project.resources().selection().owner())) return;
        JsonElement value = project.draft().resource(key);
        if (!(value instanceof JsonObject data)) return;
        if (kind == ResourceKind.VISUAL_ASSET && !(data.get("variants") instanceof JsonObject)) return;
        operation.accept(data);
        project.editAsset(key, data, group);
    }
    public void dispose() {
        disposed = true; cancelSelection(); ++refreshRevision;
        if (preview != null) preview.clear();
    }
}
