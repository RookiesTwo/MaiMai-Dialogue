package top.rookiestwo.maimai_dialogue_editor.mixin;

import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import top.rookiestwo.maimai_dialogue_editor.client.EditorSoundAccess;

@Mixin(SoundEngine.class)
public abstract class EditorSoundEngineAccess implements EditorSoundAccess.Engine {
    @Shadow @Final private ChannelAccess channelAccess;
    @Shadow private boolean loaded;
    @Override public ChannelAccess maimaiEditor$channels() { return channelAccess; }
    @Override public boolean maimaiEditor$loaded() { return loaded; }
}
