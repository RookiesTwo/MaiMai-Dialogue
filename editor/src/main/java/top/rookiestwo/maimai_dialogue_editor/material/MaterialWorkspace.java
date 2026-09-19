package top.rookiestwo.maimai_dialogue_editor.material;

import com.google.gson.*;
import top.rookiestwo.maimai_dialogue_editor.project.*;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** File-picker state, asset edits and explicit refresh status, independent of Views. UI-thread owner. */
public final class MaterialWorkspace {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public interface PreviewAssets {
        CompletableFuture<String> refresh(ProjectDraft draft);
        void clear();
    }
    private final ProjectWorkspace project;
    private final Executor io, ui;
    private final Runnable changed;
    private final Path initialDirectory;
    private PreviewAssets preview;
    private Path browsing, projectDirectory;
    private final Set<Path> selected = new LinkedHashSet<>();
    private List<MaterialFiles.Entry> files = List.of();
    private String target = "", error = "", loadedSignature = "", previewNamespace = "";
    private String folder = "";
    private String refreshError = "";
    private ResourceKey replacement;
    private long browseRevision, refreshRevision, loadedRevision;
    private long projectGeneration;
    private boolean browsingBusy, refreshing, disposed;
    private final Map<ResourceKey, String> variants = new HashMap<>();

    public MaterialWorkspace(ProjectWorkspace project, Executor io, Executor ui, Runnable changed, Path initialDirectory) {
        this.project = project; this.io = io; this.ui = ui; this.changed = changed; this.initialDirectory = initialDirectory;
    }
    public void setPreview(PreviewAssets preview) { this.preview = preview; }
    public boolean choosing() { return project.page() == ProjectWorkspace.Page.IMPORT; }
    public Path browsing() { return browsing; }
    public List<Path> selectedFiles() { return List.copyOf(selected); }
    public boolean isSelected(Path path) { return selected.contains(path); }
    public boolean batch() { return selected.size() > 1; }
    public List<MaterialFiles.Entry> files() { return files; }
    public String target() { return batch() ? folder : target; }
    public String error() { return error; }
    public String refreshError() { return refreshError; }
    public boolean browsingBusy() { return browsingBusy; }
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
        if (Objects.equals(projectDirectory, project.directory()) && projectGeneration == project.projectGeneration()) return;
        projectDirectory = project.directory();
        projectGeneration = project.projectGeneration();
        ++refreshRevision; ++browseRevision;
        loadedSignature = ""; previewNamespace = ""; loadedRevision++;
        refreshing = false; error = ""; refreshError = ""; variants.clear();
        if (preview != null) preview.clear();
    }
    public void begin(ResourceKey replace) {
        if (disposed || project.busy() || project.draft() == null) return;
        if (replace != null && !replace.kind().material()) return;
        replacement = replace; selected.clear(); target = replace == null ? "" : replace.path(); folder = ""; error = "";
        project.showImport();
        browse(browsing == null ? initialDirectory : browsing);
    }
    public void browse(Path path) {
        if (!choosing() || project.busy() || disposed) return;
        long expected = ++browseRevision;
        Path destination = path == null ? null : path.toAbsolutePath().normalize();
        browsingBusy = true;
        io.execute(() -> {
            List<MaterialFiles.Entry> entries = List.of(); String failure = "";
            try { entries = MaterialFiles.list(destination); } catch (Exception exception) { failure = String.valueOf(exception.getMessage()); }
            var result = entries; String message = failure;
            ui.execute(() -> {
                if (disposed || expected != browseRevision || !choosing()) return;
                browsingBusy = false;
                if (message.isEmpty()) { browsing = destination; files = result; }
                error = message; changed.run();
            });
        });
    }
    public void choose(MaterialFiles.Entry entry) {
        if (!choosing() || project.busy() || !files.contains(entry)) return;
        if (entry.directory()) { browse(entry.path()); return; }
        if (replacement != null && MaterialFiles.kind(entry.path()) != replacement.kind()) {
            error = "PNG / OGG Vorbis"; changed.run(); return;
        }
        if (replacement != null) { selected.clear(); selected.add(entry.path()); }
        else if (!selected.remove(entry.path())) selected.add(entry.path());
        selectionChanged();
    }
    public void selectAll() {
        if (!choosing() || project.busy() || replacing()) return;
        boolean modified = false;
        for (MaterialFiles.Entry entry : files) if (!entry.directory()) modified |= selected.add(entry.path());
        if (modified) selectionChanged();
    }
    public void clearSelection() {
        if (!choosing() || project.busy() || selected.isEmpty()) return;
        selected.clear(); selectionChanged();
    }
    private void selectionChanged() {
        error = "";
        if (!replacing() && selected.size() == 1) {
            Path source = selected.iterator().next();
            target = project.resources().catalog().unusedPath(MaterialFiles.kind(source), suggestedPath(source));
        } else if (!replacing() && selected.isEmpty()) target = "";
        changed.run();
    }
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
        if (!choosing() || selected.isEmpty() || project.busy()) return;
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
        ProjectDraft draft = project.draft(); Path directory = project.directory();
        long expected = ++refreshRevision; String signature = MaterialPack.signature(draft);
        refreshing = true; refreshError = ""; changed.run();
        CompletableFuture<String> future;
        try { future = preview.refresh(draft); }
        catch (RuntimeException failure) { future = CompletableFuture.failedFuture(failure); }
        future.whenComplete((namespace, failure) -> ui.execute(() -> {
            if (disposed || expected != refreshRevision || !Objects.equals(directory, project.directory())) return;
            refreshing = false;
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
    public void renameVariant(String name) {
        edit(ResourceKind.VISUAL_ASSET, "variant_name", data -> {
            var key = project.resources().opened();
            String old = variant(key, data);
            JsonObject values = data.getAsJsonObject("variants");
            if (!values.has(old) || old.equals(name) || values.has(name)) return;
            JsonObject next = new JsonObject();
            values.entrySet().forEach(e -> next.add(e.getKey().equals(old) ? name : e.getKey(), e.getValue()));
            data.add("variants", next); variants.put(key, name);
        });
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
        disposed = true; ++browseRevision; ++refreshRevision;
        if (preview != null) preview.clear();
    }
}
