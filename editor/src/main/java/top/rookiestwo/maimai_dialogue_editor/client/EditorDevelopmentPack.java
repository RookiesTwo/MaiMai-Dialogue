package top.rookiestwo.maimai_dialogue_editor.client;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.world.flag.FeatureFlagSet;
import top.rookiestwo.maimai_dialogue_editor.material.*;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Hidden session-only assets pack. Repository mutations and reload requests run on the Minecraft thread. */
public final class EditorDevelopmentPack implements MaterialWorkspace.PreviewAssets {
    private static final String ID = "maimai_dialogue_editor/preview_assets";
    private static final AtomicLong NAMES = new AtomicLong();
    private static Pack active;
    private static final RepositorySource SOURCE = consumer -> { if (active != null) consumer.accept(active); };
    private static EditorDevelopmentPack owner;
    private static Path activeDirectory;
    private static boolean registered;
    private static CompletableFuture<Void> reloads = CompletableFuture.completedFuture(null);
    private final Executor io;
    private final Path cache;
    private final AtomicLong revision = new AtomicLong();

    public EditorDevelopmentPack(Executor io, Path gameDirectory) {
        this.io = io; cache = gameDirectory.resolve("maimai-dialogue-editor-cache");
    }
    @Override public CompletableFuture<String> refresh(ProjectDraft draft) {
        long request = revision.incrementAndGet();
        String namespace = "maimai_editor_preview_" + NAMES.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return prepareFiles(cache, draft, namespace, SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES));
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }, io).thenCompose(path -> {
            CompletableFuture<String> result = new CompletableFuture<>();
            Minecraft.getInstance().execute(() -> {
                reloads = reloads.handle((unused, failure) -> null).thenComposeAsync(unused -> {
                    if (request != revision.get()) {
                        cleanup(path); result.completeExceptionally(new CancellationException()); return CompletableFuture.completedFuture(null);
                    }
                    Minecraft minecraft = Minecraft.getInstance();
                    PackRepository repository = minecraft.getResourcePackRepository();
                    if (!registered) { repository.addPackFinder(SOURCE); registered = true; }
                    Path previous = activeDirectory;
                    Pack previousPack = active;
                    EditorDevelopmentPack previousOwner = owner;
                    PackLocationInfo location = new PackLocationInfo(ID, Component.literal("MaiMai Editor Preview"), PackSource.BUILT_IN, Optional.empty());
                    active = new Pack(location, new PathPackResources.PathResourcesSupplier(path),
                            new Pack.Metadata(Component.literal("MaiMai Editor Preview"), PackCompatibility.COMPATIBLE,
                                    FeatureFlagSet.of(), List.of(), true),
                            new PackSelectionConfig(true, Pack.Position.TOP, true));
                    activeDirectory = path; owner = this;
                    repository.reload();
                    return minecraft.reloadResourcePacks().handleAsync((ignored, failure) -> {
                        if (failure == null) {
                            cleanup(previous);
                            if (request == revision.get()) result.complete(namespace);
                            else result.completeExceptionally(new CancellationException());
                        } else {
                            active = previousPack; activeDirectory = previous; owner = previousOwner;
                            repository.reload();
                            cleanup(path);
                            result.completeExceptionally(failure);
                        }
                        return (Void)null;
                    }, minecraft);
                }, Minecraft.getInstance());
            });
            return result;
        });
    }
    /** Pack file preparation only; no Minecraft client or UI lifecycle is started here. */
    static Path prepareFiles(Path cache, ProjectDraft draft, String namespace, int packFormat) throws IOException {
        // Minecraft's development launch supplies gameDir ".". Use one path representation for both sides of containment checks.
        cache = cache.toAbsolutePath().normalize();
        Path staging = null;
        try {
            if (Files.isSymbolicLink(cache)) throw new IOException("Invalid preview cache");
            Files.createDirectories(cache);
            if (!cache.toRealPath().equals(cache.getParent().toRealPath().resolve(cache.getFileName())))
                throw new IOException("Invalid preview cache");
            staging = Files.createTempDirectory(cache, "assets-");
            Files.writeString(staging.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":"
                    + packFormat
                    + ",\"description\":\"MaiMai Editor Preview\"}}");
            Path output = staging;
            MaterialPack.write(draft, (relative, bytes) -> {
                Path path = output.resolve(relative).normalize();
                if (!path.startsWith(output)) throw new IOException("Invalid preview path");
                Files.createDirectories(path.getParent());
                Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
            }, namespace);
            return output;
        } catch (IOException | RuntimeException failure) {
            cleanup(staging);
            throw failure;
        }
    }

    @Override public void clear() {
        revision.incrementAndGet();
        Minecraft.getInstance().execute(() -> reloads = reloads.handle((unused, failure) -> null)
                .thenComposeAsync(unused -> {
                    if (owner != this) return CompletableFuture.completedFuture(null);
                    Path previous = activeDirectory;
                    active = null; activeDirectory = null; owner = null;
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.getResourcePackRepository().reload();
                    return minecraft.reloadResourcePacks().whenComplete((done, failure) -> {
                        if (failure == null) cleanup(previous);
                    });
                }, Minecraft.getInstance()));
    }
    private static void cleanup(Path directory) {
        if (directory == null) return;
        CompletableFuture.runAsync(() -> {
            // Only application-created assets-* children; never delete project files or follow links.
            if (!directory.getFileName().toString().startsWith("assets-")
                    || !directory.getParent().getFileName().toString().equals("maimai-dialogue-editor-cache")) return;
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            } catch (IOException ignored) { /* A locked cache can be retained without affecting the project. */ }
        });
    }
}
