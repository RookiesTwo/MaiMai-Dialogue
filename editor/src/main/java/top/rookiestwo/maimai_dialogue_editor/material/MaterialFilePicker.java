package top.rookiestwo.maimai_dialogue_editor.material;

import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Source selection only; project destinations remain managed by ProjectStore. Empty results mean cancellation. */
@FunctionalInterface
public interface MaterialFilePicker {
    record Request(Path directory, ResourceKind replacementKind) {
        public boolean multiple() { return replacementKind == null; }
    }
    CompletableFuture<List<Path>> choose(Request request);
}
