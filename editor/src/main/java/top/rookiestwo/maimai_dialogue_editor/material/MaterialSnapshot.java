package top.rookiestwo.maimai_dialogue_editor.material;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue_editor.project.*;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.io.*;
import java.util.*;

/** Project-local asset index. Metadata is loaded in the background; binary bytes remain lazy. */
public record MaterialSnapshot(String namespace, Map<ResourceLocation, ProjectBlob> images,
                               Map<ResourceLocation, List<SoundFile>> sounds) {
    public static final MaterialSnapshot EMPTY = new MaterialSnapshot("", Map.of(), Map.of());
    public record SoundFile(ResourceLocation id, ProjectBlob blob, boolean stream) {
        /** The caller opens/decodes on an IO thread and owns the returned stream. */
        public InputStream open() throws IOException { return new ByteArrayInputStream(blob.read()); }
    }
    public MaterialSnapshot {
        images = Map.copyOf(images);
        Map<ResourceLocation, List<SoundFile>> frozen = new LinkedHashMap<>();
        sounds.forEach((id, files) -> frozen.put(id, List.copyOf(files)));
        sounds = Collections.unmodifiableMap(frozen);
    }
    public boolean owns(ResourceLocation id) { return namespace.equals(id.getNamespace()); }

    public static MaterialSnapshot prepare(ProjectDraft draft) throws IOException {
        if (!draft.namespace().matches("[a-z0-9_.-]+")) throw new IOException("Invalid project namespace");
        Map<ResourceLocation, ProjectBlob> images = new LinkedHashMap<>();
        Map<ResourceLocation, List<SoundFile>> sounds = new LinkedHashMap<>();
        for (ResourceKey key : draft.resourceKeys().stream().sorted(Comparator.comparing(ResourceKey::kind)
                .thenComparing(ResourceKey::path)).toList()) {
            if (!key.kind().material()) continue;
            draft.load(key);
            if (!(draft.resource(key) instanceof JsonObject data)) throw new IOException("Invalid material: " + key);
            String blobId = MaterialPack.string(data.get("blob"));
            ProjectBlob blob = draft.blob(blobId);
            if (blob == null || !blobId.endsWith(key.kind() == ResourceKind.IMAGE ? ".png" : ".ogg"))
                throw new IOException("Missing material file: " + key);
            if (key.kind() == ResourceKind.IMAGE) {
                ResourceLocation id = ResourceLocation.tryParse(MaterialPack.imageId(key, draft.namespace()));
                if (id == null) throw new IOException("Invalid image ID: " + key);
                images.put(id, blob);
            } else {
                String event = MaterialPack.string(data.get("event"));
                ResourceLocation id = ResourceLocation.tryBuild(draft.namespace(), event);
                // Incomplete event fields are valid drafts; keep images usable while editing a sound name.
                if (id == null || event.isEmpty()) continue;
                boolean stream = data.has("stream") && data.get("stream").isJsonPrimitive()
                        && data.getAsJsonPrimitive("stream").isBoolean() && data.get("stream").getAsBoolean();
                sounds.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new SoundFile(
                        ResourceLocation.fromNamespaceAndPath(draft.namespace(), key.path()), blob, stream));
            }
        }
        return new MaterialSnapshot(draft.namespace(), images, sounds);
    }
}
