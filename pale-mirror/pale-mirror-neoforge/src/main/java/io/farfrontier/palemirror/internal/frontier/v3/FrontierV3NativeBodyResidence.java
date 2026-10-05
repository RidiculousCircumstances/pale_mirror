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
     * A late resident can remain indexed after its terrain holder became inaccessible.
     * Queue the ordinary native store/unload pass again; its existing write, sync and
     * removal observations remain the only evidence that can release common custody.
     * Neither observer absence nor presentation closure can authorize this operation.
     */
    static boolean reconcile(ServerLevel level, Mob body) {
        ChunkPos column = body.chunkPosition();
        var source = level.getChunkSource();
        var holder = source.chunkMap.getVisibleChunkIfPresent(column.toLong());
        FullChunkStatus status = holder == null ? FullChunkStatus.INACCESSIBLE
                : ChunkLevel.fullStatus(holder.getTicketLevel());
        if (!needsNativeUnload(source.getChunkNow(column.x, column.z) != null, status)) return false;
        var manager = ((io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3ServerEntityManagerAccessor) level)
                .frontierV3$getEntityManager();
        manager.updateChunkStatus(column, FullChunkStatus.INACCESSIBLE);
        return true;
    }

    static boolean needsNativeUnload(boolean terrainPresent, FullChunkStatus holderStatus) {
        return !terrainPresent && holderStatus == FullChunkStatus.INACCESSIBLE;
    }
}
