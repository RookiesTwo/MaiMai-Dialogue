package top.rookiestwo.maimai_dialogue_editor.project;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import top.rookiestwo.maimai_dialogue_editor.preview.PreviewScenario;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;

// 管理编辑器偏好与恢复；跨 Fragment 的串行屏障也供自动保存复用。
public final class ProjectSessionCoordinator {
    public interface Owner {
        boolean disposed();
        boolean canRestore();
        boolean canPersist();
        Path directory();
        EditorSessionState snapshot();
        void opened(OpenedSession session);
        void failed(Exception error);
        void changed();
    }
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static CompletableFuture<Void> sessionWrites = CompletableFuture.completedFuture(null);
    private final ProjectStore store;
    private final Executor io, ui;
    private final Owner owner;
    private EditorSessionStore sessionStore;
    private long startupRequest, sessionSaveRequest;
    private EditorSessionState queuedSession;
    private Path queuedSessionDirectory;
    private EditorSessionState.Layout layout = EditorSessionState.Layout.defaults();
    private int themeExample;
    private PreviewScenario simulation = PreviewScenario.defaults();

    public ProjectSessionCoordinator(ProjectStore store, Executor io, Executor ui, Owner owner) {
        this.store = store; this.io = io; this.ui = ui; this.owner = owner;
    }
    public boolean started() { return sessionStore != null; }
    public void cancelRestore() { ++startupRequest; }
    public EditorSessionState.Layout layout() { return layout; }
    public void layout(EditorSessionState.Layout value) { layout = value; changed(); }
    public int themeExample() { return themeExample; }
    public void themeExample(int value) { themeExample = Math.clamp(value, 0, 2); changed(); }
    public PreviewScenario simulation() { return simulation; }
    public void simulation(PreviewScenario value) { simulation = value; }
    public void restorePreferences(EditorSessionState state) {
        layout = state.layout(); themeExample = state.preview().themeExample(); simulation = state.preview().simulation();
    }
    // 仅供已经排入写入屏障的保存操作调用，保持草稿、偏好、last project 的原顺序。
    public void writePreferences(Path directory, EditorSessionState state) throws java.io.IOException {
        sessionStore.write(directory, state); sessionStore.remember(directory);
    }
    public void start() {
        if (owner.disposed() || sessionStore != null) return;
        sessionStore = new EditorSessionStore(store);
        long request = ++startupRequest;
        if (!owner.canRestore()) return;
        CompletableFuture<Void> previousWrites;
        synchronized (ProjectSessionCoordinator.class) { previousWrites = sessionWrites; }
        io.execute(() -> {
            previousWrites.join();
            Path last = sessionStore.lastProject();
            if (last == null) return;
            OpenedSession result = null;
            Exception failure = null;
            try { result = read(last); } catch (Exception error) { failure = error; }
            OpenedSession loaded = result;
            Exception error = failure;
            ui.execute(() -> {
                if (owner.disposed() || request != startupRequest || !owner.canRestore()) return;
                if (error == null) owner.opened(loaded); else owner.failed(error);
                owner.changed();
            });
        });
    }

    public record OpenedSession(Path directory, ProjectStore.Loaded project, EditorSessionState state) {}
    public OpenedSession read(Path target) throws java.io.IOException {
        var loaded = store.open(target);
        var state = sessionStore == null ? EditorSessionState.defaults() : sessionStore.read(target);
        // Expanded Dialogue children need their text before publishing the restored tree, even when not selected.
        // Only these and the current document/selection are loaded; all other resource bodies remain lazy.
        var keys = new java.util.HashSet<ResourceKey>();
        keys.add(state.navigation().opened()); keys.add(state.navigation().selection().owner());
        keys.addAll(state.navigation().expandedDialogues());
        for (var key : keys) if (key != null && loaded.draft().revision(key) != null) {
            try { loaded.draft().load(key); }
            catch (java.io.IOException | RuntimeException failure) {
                LOGGER.warn("Cannot restore editor document {}", key, failure);
            }
        }
        return new OpenedSession(target, loaded, state);
    }

    public void changed() {
        if (owner.disposed() || sessionStore == null || !owner.canPersist()) return;
        long request = ++sessionSaveRequest;
        CompletableFuture.delayedExecutor(500, java.util.concurrent.TimeUnit.MILLISECONDS, ui).execute(() -> {
            if (!owner.disposed() && request == sessionSaveRequest) flush();
        });
    }

    public void flush() {
        ++sessionSaveRequest;
        if (sessionStore == null || owner.directory() == null || !owner.canPersist()) return;
        var state = owner.snapshot();
        Path target = owner.directory();
        if (target.equals(queuedSessionDirectory) && state.equals(queuedSession)) return;
        queuedSessionDirectory = target; queuedSession = state;
        enqueue(() -> {
            try { sessionStore.write(target, state); }
            catch (java.io.IOException | RuntimeException failure) {
                LOGGER.warn("Cannot save editor state for {}", target, failure);
                ui.execute(() -> {
                    if (target.equals(queuedSessionDirectory) && state.equals(queuedSession)) queuedSession = null;
                });
            }
        });
    }

    public void remember(Path target) {
        if (sessionStore == null) return;
        enqueue(() -> {
            try { sessionStore.remember(target); }
            catch (java.io.IOException | RuntimeException failure) { LOGGER.warn("Cannot remember editor project", failure); }
        });
    }

    public void enqueue(Runnable write) {
        // 新 Fragment 也必须排在旧 Fragment 的最后一次写入之后。
        CompletableFuture<Void> previous;
        var completed = new CompletableFuture<Void>();
        synchronized (ProjectSessionCoordinator.class) {
            previous = sessionWrites;
            sessionWrites = completed;
        }
        try {
            // Enqueue immediately so shutting down this screen's executor still drains the final write.
            io.execute(() -> {
                previous.join();
                try { write.run(); } finally { completed.complete(null); }
            });
        } catch (RuntimeException failure) {
            completed.complete(null);
            LOGGER.warn("Cannot schedule editor state save", failure);
        }
    }

}
