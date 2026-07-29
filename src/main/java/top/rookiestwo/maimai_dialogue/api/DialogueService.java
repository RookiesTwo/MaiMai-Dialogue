package top.rookiestwo.maimai_dialogue.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletionStage;

public interface DialogueService {
    CompletionStage<DialogueOpenResult> open(
            ServerPlayer player,
            ResourceLocation dialogueId
    );
}
