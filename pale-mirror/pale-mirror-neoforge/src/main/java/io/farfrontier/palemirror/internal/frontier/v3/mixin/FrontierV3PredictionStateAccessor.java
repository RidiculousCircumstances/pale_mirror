package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only pilot witness that a predicted block has actually been acknowledged by the server. */
@Mixin(BlockStatePredictionHandler.class)
public interface FrontierV3PredictionStateAccessor {
    @Accessor("serverVerifiedStates") Long2ObjectOpenHashMap<?> frontierV3$knownServerStates();
}
