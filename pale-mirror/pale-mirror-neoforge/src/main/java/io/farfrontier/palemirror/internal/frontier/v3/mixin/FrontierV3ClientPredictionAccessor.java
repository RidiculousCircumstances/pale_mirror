package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only access to the native client's prediction witness for the ordinary packet pilot. */
@Mixin(ClientLevel.class)
public interface FrontierV3ClientPredictionAccessor {
    @Invoker("getBlockStatePredictionHandler") BlockStatePredictionHandler frontierV3$predictionHandler();
}
