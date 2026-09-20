package top.rookiestwo.maimai_dialogue_editor.client;

import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;

/** Read-only bridge implemented by editor mixins; normal code never loads a Mixin implementation class. */
public interface EditorSoundAccess {
    SoundEngine maimaiEditor$soundEngine();
    interface Engine {
        ChannelAccess maimaiEditor$channels();
        boolean maimaiEditor$loaded();
    }
}
