package top.rookiestwo.maimai_dialogue_editor.client;

import icyllis.modernui.core.Core;
import net.minecraft.client.Minecraft;
import top.rookiestwo.maimai_dialogue.client.bootstrap.ClientServices;
import top.rookiestwo.maimai_dialogue.client.resource.ClientContentSnapshot;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Client snapshot -> workspace IO. Request validity and ownership remain with each caller. */
public final class EditorContentPreparation {
    @FunctionalInterface public interface Operation<T> {
        T prepare(ClientContentSnapshot external) throws IOException;
    }

    private EditorContentPreparation() {}

    /** Sessions already marshal completions to UI; keep their existing scheduling boundary. */
    public static <T> CompletableFuture<T> prepare(ProjectWorkspace workspace, Operation<T> operation) {
        return onClientSnapshot(external -> workspace.prepare(() -> {
            try { return operation.prepare(external); }
            catch (IOException failure) { throw new CompletionException(failure); }
        }));
    }

    // 缓存命中可直接返回；配置捕获仍在 client thread，实际内容准备由调用者交给 IO。
    public static <T> CompletableFuture<T> onClientSnapshot(Function<ClientContentSnapshot, CompletableFuture<T>> preparation) {
        var result = new CompletableFuture<T>();
        try {
            Minecraft.getInstance().execute(() -> {
                try {
                    var external = ClientServices.get().content().current();
                    preparation.apply(external).whenComplete((value, failure) -> {
                        if (failure == null) result.complete(value);
                        else result.completeExceptionally(cause(failure));
                    });
                } catch (RuntimeException failure) {
                    // The workspace executor can close while the client task is queued.
                    result.completeExceptionally(cause(failure));
                }
            });
        } catch (RuntimeException failure) {
            result.completeExceptionally(cause(failure));
        }
        return result;
    }

    /** Property/canvas consumers receive both results and failures on UI. */
    public static <T> void prepare(ProjectWorkspace workspace, Operation<T> operation, BiConsumer<T, Throwable> completed) {
        prepare(workspace, operation).whenComplete((value, failure) ->
                Core.getUiHandler().post(() -> completed.accept(value, failure)));
    }

    private static Throwable cause(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }
}
