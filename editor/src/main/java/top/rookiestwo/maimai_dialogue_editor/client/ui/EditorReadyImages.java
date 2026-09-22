package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.graphics.Image;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.ui.scene.DialogueImageSource;
import java.util.LinkedHashMap;
import java.util.Map;

/** A mounted preview owns cloned, already prepared handles; each Scene View gets its own fork. */
final class EditorReadyImages implements DialogueImageSource {
    private final Map<ResourceLocation, Image> images = new LinkedHashMap<>();
    EditorReadyImages(Map<ResourceLocation, Image> source) { source.forEach((id, image) -> images.put(id, image.clone())); }
    @Override public DialogueImageSource fork() { return new EditorReadyImages(images); }
    @Override public void load(ResourceLocation id, java.util.function.Consumer<Image> ready) { ready.accept(images.get(id)); }
    @Override public void close() { images.values().forEach(Image::close); images.clear(); }
}
