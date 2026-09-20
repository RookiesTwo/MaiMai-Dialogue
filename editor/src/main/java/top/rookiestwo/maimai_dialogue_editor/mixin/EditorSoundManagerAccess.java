package top.rookiestwo.maimai_dialogue_editor.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import top.rookiestwo.maimai_dialogue_editor.client.EditorSoundAccess;

@Mixin(SoundManager.class)
public abstract class EditorSoundManagerAccess implements EditorSoundAccess {
    @Shadow @Final private SoundEngine soundEngine;
    @Override public SoundEngine maimaiEditor$soundEngine() { return soundEngine; }
}
