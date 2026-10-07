package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;

/** Bridge to vanilla residency, not a body remover or a canonical custody writer. */
final class FrontierV3NativeBodyResidence {
    private FrontierV3NativeBodyResidence() { }

    /**
     * A resident can remain indexed in the non-ticking, terrain-visible halo, or after
     * its terrain holder became inaccessible. Loaded blocks are not a ticking body.
     * Queue the ordinary native store/unload pass again; its existing write, sync and
     * removal observations remain the only evidence that can release common custody.
     * The visible halo is eligible only outside every observer's HOT radius and only
     * when vanilla grants no entity ticks. This hides entity residency, not terrain;
     * vanilla restores it on the ordinary ENTITY_TICKING status transition.
     */
    static boolean reconcile(ServerLevel level, Mob body) {
        ChunkPos column = body.chunkPosition();
        var source = level.getChunkSource();
        var holder = source.chunkMap.getVisibleChunkIfPresent(column.toLong());
        FullChunkStatus status = holder == null ? FullChunkStatus.INACCESSIBLE
                : ChunkLevel.fullStatus(holder.getTicketLevel());
        boolean terrainPresent = source.getChunkNow(column.x, column.z) != null;
        boolean entityTicking = level.isPositionEntityTicking(body.blockPosition());
        boolean observed = FrontierV3SceneDemand.observerWithinColumn(level, column,
                FrontierV3SceneDemand.RADIUS_BLOCKS);
        if (!needsNativeUnload(terrainPresent, status, entityTicking, observed)) return false;
        var manager = ((io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3ServerEntityManagerAccessor) level)
                .frontierV3$getEntityManager();
        io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info(
                "PMV3_NATIVE_RESIDENCY_HANDOFF entity={} column={} terrainPresent={} holderStatus={} entityTicking={} observed={}",
                body.getUUID(), column, terrainPresent, status, entityTicking, observed);
        manager.updateChunkStatus(column, FullChunkStatus.INACCESSIBLE);
        return true;
    }

    static boolean needsNativeUnload(boolean terrainPresent, FullChunkStatus holderStatus) {
        return !terrainPresent && holderStatus == FullChunkStatus.INACCESSIBLE;
    }

    static boolean needsNativeUnload(boolean terrainPresent, FullChunkStatus holderStatus,
                                     boolean entityTicking, boolean observed) {
        return needsNativeUnload(terrainPresent, holderStatus)
                || terrainPresent && holderStatus != FullChunkStatus.ENTITY_TICKING && !entityTicking && !observed;
    }
}
