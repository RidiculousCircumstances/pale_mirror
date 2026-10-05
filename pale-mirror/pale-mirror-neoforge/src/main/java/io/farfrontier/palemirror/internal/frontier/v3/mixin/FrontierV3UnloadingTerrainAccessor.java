package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Positive no-load access for the final body observation, never route planning or chunk admission. */
@Mixin(ChunkMap.class)
public interface FrontierV3UnloadingTerrainAccessor {
    @Accessor("pendingUnloads") Long2ObjectLinkedOpenHashMap<ChunkHolder> frontierV3$getPendingUnloads();
}
