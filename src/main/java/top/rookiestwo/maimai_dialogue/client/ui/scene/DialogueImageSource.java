package top.rookiestwo.maimai_dialogue.client.ui.scene;

import icyllis.modernui.graphics.Image;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Consumer;

/** Images borrowed by one dialogue Fragment. Calls and completion callbacks belong to the UI thread. */
public interface DialogueImageSource extends AutoCloseable {
    @SuppressWarnings("deprecation")
    DialogueImageSource RESOURCES = (id, ready) -> ready.accept(Image.create(id.getNamespace(), id.getPath()));

    void load(ResourceLocation id, Consumer<Image> ready);

    /** A child scene may release its images before the Fragment ends. Shared providers need no separate ownership. */
    default DialogueImageSource fork() { return this::load; }

    /** Called after the owning Fragment releases its Views; shared game resources are never closed here. */
    @Override default void close() {}
}
