package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only pilot access to vanilla's exact player-ticket and holder state. */
@Mixin(ChunkMap.class)
public interface FrontierV3PilotChunkMapAccessor {
    @Accessor("distanceManager")
    DistanceManager paleMirror$distanceManager();

    /** Complete current holder membership; copied by the observer and never mutated. */
    @Accessor("updatingChunkMap")
    Long2ObjectMap<ChunkHolder> paleMirror$updatingChunks();
}
