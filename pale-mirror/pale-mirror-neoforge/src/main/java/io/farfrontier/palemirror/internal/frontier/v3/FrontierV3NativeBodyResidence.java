package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;

/** Bridge to vanilla residency, not a body remover or a canonical custody writer. */
final class FrontierV3NativeBodyResidence {
    /** Ephemeral native visibility overrides only; neither body nor COLD permission is retained here. */
    private static final java.util.Map<ServerLevel, java.util.Set<Long>> HIDDEN_COLUMNS = new java.util.WeakHashMap<>();
    private FrontierV3NativeBodyResidence() { }

    /** Tracking stops before vanilla's queued store pass emits its departure receipt. */
    static boolean departurePending(ServerLevel level, java.util.UUID entityId) {
        var manager = ((io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3ServerEntityManagerAccessor) level)
                .frontierV3$getEntityManager();
        var storage = (io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3EntityPermanentStorageAccessor) manager;
        var columns = storage.frontierV3$getChunksToUnload().iterator();
        while (columns.hasNext()) {
            long column = columns.nextLong();
            if (storage.frontierV3$getSectionStorage().getExistingSectionsInChunk(column)
                    .flatMap(section -> section.getEntities())
                    .anyMatch(entity -> entity.getUUID().equals(entityId) && !entity.isRemoved() && entity.shouldBeSaved()))
                return true;
        }
        // Neither an old visibility override nor a queued empty column proves a pending body.
        return false;
    }

    /**
     * A resident can remain indexed in the non-ticking, terrain-visible halo, or after
     * its terrain holder became inaccessible. Loaded blocks are not a ticking body.
     * Queue the ordinary native store/unload pass again; its existing write, sync and
     * removal observations remain the only evidence that can release common custody.
     * The visible halo is eligible only outside every observer's HOT radius and only
     * when vanilla's independent simulation-distance tracker grants no entity ticks.
     * Holder status alone is insufficient: view tickets can retain ENTITY_TICKING
     * outside that range. This hides entity residency, not terrain. Restore handles
     * the symmetric return even when the holder never changes its nominal status.
     */
    static boolean reconcile(ServerLevel level, Mob body) {
        ChunkPos column = body.chunkPosition();
        var source = level.getChunkSource();
        var holder = source.chunkMap.getVisibleChunkIfPresent(column.toLong());
        FullChunkStatus status = holder == null ? FullChunkStatus.INACCESSIBLE
                : ChunkLevel.fullStatus(holder.getTicketLevel());
        boolean terrainPresent = source.getChunkNow(column.x, column.z) != null;
        boolean entityTicking = source.chunkMap.getDistanceManager().inEntityTickingRange(column.toLong());
        boolean observed = FrontierV3SceneDemand.observerWithinColumn(level, column,
                FrontierV3SceneDemand.RADIUS_BLOCKS);
        if (!needsNativeUnload(terrainPresent, status, entityTicking, observed)) return false;
        var hidden = HIDDEN_COLUMNS.computeIfAbsent(level, ignored -> new java.util.HashSet<>());
        if (hidden.size() >= FrontierV3AmbientPendingAdmissions.MAX_ENTRIES && !hidden.contains(column.toLong()))
            throw new IllegalStateException("native residency override capacity exceeded");
        hidden.add(column.toLong());
        var manager = ((io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3ServerEntityManagerAccessor) level)
                .frontierV3$getEntityManager();
        io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info(
                "PMV3_NATIVE_RESIDENCY_HANDOFF entity={} column={} terrainPresent={} holderStatus={} entityTicking={} observed={}",
                body.getUUID(), column, terrainPresent, status, entityTicking, observed);
        manager.updateChunkStatus(column, FullChunkStatus.INACCESSIBLE);
        return true;
    }

    /** Native return can precede UUID indexing; never depend on finding the unloaded body. */
    static void restore(ServerLevel level) {
        var hidden = HIDDEN_COLUMNS.get(level);
        if (hidden == null) return;
        var source = level.getChunkSource();
        var manager = ((io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3ServerEntityManagerAccessor) level)
                .frontierV3$getEntityManager();
        var storage = (io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3EntityPermanentStorageAccessor) manager;
        var sections = storage.frontierV3$getSectionStorage();
        hidden.removeIf(encoded -> {
            var column = new ChunkPos(encoded);
            var holder = source.chunkMap.getVisibleChunkIfPresent(encoded);
            var status = holder == null ? FullChunkStatus.INACCESSIBLE : ChunkLevel.fullStatus(holder.getTicketLevel());
            boolean present = source.getChunkNow(column.x, column.z) != null;
            boolean ticking = source.chunkMap.getDistanceManager().inEntityTickingRange(encoded);
            var lateBodies = sections.getExistingSectionsInChunk(encoded).flatMap(section -> section.getEntities())
                    .filter(entity -> !entity.isRemoved() && entity.shouldBeSaved()).toList();
            if (needsNativeRedrain(!lateBodies.isEmpty(), ticking,
                    FrontierV3SceneDemand.observerWithinColumn(level, column, FrontierV3SceneDemand.RADIUS_BLOCKS))) {
                // Moving into an already-hidden section stops tracking, but vanilla's
                // onMove does not requeue a column whose previous unload completed.
                // A UUID-index-only probe cannot discover this retained object.
                if (!storage.frontierV3$getChunksToUnload().contains(encoded)) {
                    lateBodies.forEach(entity ->
                        io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info(
                                "PMV3_NATIVE_RESIDENCY_LATE_ARRIVAL entity={} column={} position={} queuedForNativeStore=true",
                                entity.getUUID(), column, entity.position()));
                    manager.updateChunkStatus(column, FullChunkStatus.INACCESSIBLE);
                }
                return false;
            }
            // Vanilla now owns the inaccessible column and its next ordinary visibility transition.
            if (!present && status == FullChunkStatus.INACCESSIBLE) return true;
            if (!needsNativeRestore(present, status, ticking))
                return false;
            manager.updateChunkStatus(column, status);
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info("PMV3_NATIVE_RESIDENCY_RETURN column={} holderStatus={} entityTickingRange=true", column, status);
            return true;
        });
        if (hidden.isEmpty()) HIDDEN_COLUMNS.remove(level);
    }

    static boolean needsNativeRestore(boolean terrainPresent, FullChunkStatus holderStatus, boolean entityTickingRange) {
        return terrainPresent && holderStatus == FullChunkStatus.ENTITY_TICKING && entityTickingRange;
    }

    static boolean needsNativeRedrain(boolean retainedBodies, boolean entityTickingRange, boolean observed) {
        return retainedBodies && !entityTickingRange && !observed;
    }

    static boolean needsNativeUnload(boolean terrainPresent, FullChunkStatus holderStatus) {
        return !terrainPresent && holderStatus == FullChunkStatus.INACCESSIBLE;
    }

    static boolean needsNativeUnload(boolean terrainPresent, FullChunkStatus holderStatus,
                                     boolean entityTicking, boolean observed) {
        return needsNativeUnload(terrainPresent, holderStatus)
                || terrainPresent && !entityTicking && !observed;
    }
}
