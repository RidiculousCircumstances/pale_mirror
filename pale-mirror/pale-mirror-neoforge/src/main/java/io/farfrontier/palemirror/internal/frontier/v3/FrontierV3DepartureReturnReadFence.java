package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;
import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** A saved departure cannot be reused after vanilla begins reloading its body. */
final class FrontierV3DepartureReturnReadFence {
    /** Pending vanilla entity reads, including the interval before their durable return fence. */
    private static final ReadReservations<ServerLevel> PENDING_READS = new ReadReservations<>();
    private FrontierV3DepartureReturnReadFence() { }

    static CompletableFuture<Optional<CompoundTag>> observeRead(ServerLevel level, ChunkPos chunk,
                                                                 CompletableFuture<Optional<CompoundTag>> source) {
        if (!FrontierV3PhysicalWorld.isPhysical(level)) return source;
        PENDING_READS.reserve(level, chunk);
        var result = new CompletableFuture<Optional<CompoundTag>>();
        source.whenComplete((raw, failure) -> level.getServer().execute(() -> {
            // EntityStorage deserializes only after this dependent future completes.
            try {
                if (failure != null) {
                    result.completeExceptionally(failure);
                    return;
                }
                try {
                    fenceBeforeVanillaLoad(level, chunk, raw.orElse(null));
                    result.complete(raw);
                } catch (RuntimeException publicationFailure) {
                    PaleMirrorMod.LOGGER.error("Entity return-read fence could not be published chunk={}; vanilla load held", chunk, publicationFailure);
                    result.completeExceptionally(publicationFailure);
                }
            } finally {
                PENDING_READS.release(level, chunk);
            }
        }));
        return result;
    }

    static boolean readPending(ServerLevel level, ChunkPos chunk) {
        return PENDING_READS.pending(level, chunk);
    }

    static boolean readPending(ServerLevel level, io.farfrontier.palemirror.frontier.v3.model.BodyPosition body) {
        return readPending(level, new ChunkPos(Math.floorDiv(body.x(), 16), Math.floorDiv(body.z(), 16)));
    }

    static boolean anyReadPending(ServerLevel level) {
        return PENDING_READS.anyPending(level);
    }

    static final class ReadReservations<K> {
        private final Map<K, Map<Long, Integer>> counts = new WeakHashMap<>();

        synchronized boolean pending(K owner, ChunkPos chunk) {
            return counts.getOrDefault(owner, Map.of()).getOrDefault(chunk.toLong(), 0) > 0;
        }

        synchronized boolean anyPending(K owner) {
            return !counts.getOrDefault(owner, Map.of()).isEmpty();
        }

        synchronized void reserve(K owner, ChunkPos chunk) {
            counts.computeIfAbsent(owner, ignored -> new HashMap<>()).merge(chunk.toLong(), 1, Integer::sum);
        }

        synchronized void release(K owner, ChunkPos chunk) {
            var chunks = counts.get(owner);
            if (chunks == null) throw new IllegalStateException("entity read reservation is missing");
            chunks.compute(chunk.toLong(), (ignored, count) -> {
                if (count == null || count < 1) throw new IllegalStateException("entity read count is invalid");
                return count == 1 ? null : count - 1;
            });
            if (chunks.isEmpty()) counts.remove(owner);
        }
    }

    static void fenceBeforeVanillaLoad(ServerLevel level, ChunkPos chunk, CompoundTag raw) {
        var world = FrontierV3PhysicalWorld.WORLD_ID;
        var actors = FrontierV3AmbientCarrierLedger.get(level, world);
        boolean changed = fenceStoredInventory(chunk, raw, actors);
        // A failed publication fails the dependent vanilla read: otherwise an
        // unjournaled returned body could move while an old saved departure
        // still looks eligible to a later no-load recovery after a crash.
        if (changed) actors.persist(level, world);
    }

    static boolean fenceStoredInventory(ChunkPos chunk, CompoundTag raw,
                                          FrontierV3AmbientCarrierLedger actors) {
        var actorReceipts = actors.departures().stream().filter(receipt ->
                inChunk(receipt.observed().body().x(), receipt.observed().body().z(), chunk)).toList();
        var bodyReceipts = actors.bodyDepartures().stream().filter(receipt ->
                inChunk(receipt.observed().body().x(), receipt.observed().body().z(), chunk)).toList();
        var ambientReceipts = actors.ambientDepartures().stream().filter(receipt ->
                inChunk(receipt.observed().body().x(), receipt.observed().body().z(), chunk)).toList();
        if (actorReceipts.isEmpty() && bodyReceipts.isEmpty() && ambientReceipts.isEmpty())
            return false;
        if (!FrontierV3StoredEntityInventory.matchesStoredChunk(raw, chunk))
            throw new IllegalStateException("misplaced stored entity chunk during return fencing");
        var entities = FrontierV3StoredEntityInventory.serializedEntities(raw)
                .orElseThrow(() -> new IllegalStateException("invalid stored entity inventory during return fencing"));
        boolean actorReturn = false;
        for (var receipt : bodyReceipts) {
            if (entities.containsKey(receipt.identity().entityId())) actorReturn |= actors.markBodyReturnRead(receipt);
        }
        for (var receipt : ambientReceipts) {
            if (entities.containsKey(receipt.carrier().identity().entityId())) actorReturn |= actors.markAmbientReturnRead(receipt);
        }
        for (var receipt : actorReceipts) {
            if (entities.containsKey(receipt.carrier().identity().entityId()))
                actorReturn |= actors.markReturnRead(receipt);
        }
        return actorReturn;
    }

    private static boolean inChunk(int x, int z, ChunkPos chunk) {
        return Math.floorDiv(x, 16) == chunk.x && Math.floorDiv(z, 16) == chunk.z;
    }
}
