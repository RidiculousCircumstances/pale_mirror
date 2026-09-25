package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3EntityPermanentStorageAccessor;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3ServerEntityManagerAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Bounded no-load census of all saved entity columns for named UUIDs. Evidence only. */
final class FrontierV3StoredEntityCensus {
    private static final int MAX_TARGETS = 256, BATCH = 64;
    private FrontierV3StoredEntityCensus() { }

    static CompletableFuture<Result> scan(ServerLevel level, Set<UUID> targets) {
        Objects.requireNonNull(level); Objects.requireNonNull(targets);
        if (!FrontierV3PhysicalWorld.isPhysical(level) || targets.isEmpty() || targets.size() > MAX_TARGETS)
            throw new IllegalArgumentException("invalid bounded entity census");
        var storage = ((FrontierV3EntityPermanentStorageAccessor)
                ((FrontierV3ServerEntityManagerAccessor) level).frontierV3$getEntityManager()).frontierV3$getPermanentStorage();
        if (!(storage instanceof FrontierV3StoredEntityInventoryAccess access))
            throw new IllegalStateException("entity storage does not expose no-load reads");
        var before = FrontierV3EntityWriteEpochs.globalStamp(level);
        if (before.isEmpty() || FrontierV3DepartureReturnReadFence.anyReadPending(level))
            return CompletableFuture.failedFuture(new IllegalStateException("entity census has concurrent IO"));
        Path directory = DimensionType.getStorageFolder(level.dimension(),
                level.getServer().getWorldPath(LevelResource.ROOT)).resolve("entities");
        Set<UUID> selected = Set.copyOf(targets);
        return access.frontierV3$synchronizeStoredEntities()
                .thenCompose(ignored -> CompletableFuture.supplyAsync(() -> {
                    try { return FrontierV3SavedEntityColumns.enumerate(directory); }
                    catch (IOException failure) { throw new UncheckedIOException(failure); }
                }, net.minecraft.Util.ioPool()))
                .thenCompose(columns -> scanBatch(access, columns, selected, 0, new HashMap<>()))
                .thenApplyAsync(sightings -> new Result(sightings, before.orElseThrow()), level.getServer());
    }

    static CompletableFuture<Map<UUID, Set<ChunkPos>>> scanBatch(FrontierV3StoredEntityInventoryAccess access,
            List<ChunkPos> columns, Set<UUID> targets, int offset, Map<UUID, Set<ChunkPos>> sightings) {
        if (offset == columns.size()) return CompletableFuture.completedFuture(Map.copyOf(sightings));
        int end = Math.min(columns.size(), offset + BATCH);
        var reads = new ArrayList<CompletableFuture<Column>>(end - offset);
        for (int index = offset; index < end; index++) {
            ChunkPos chunk = columns.get(index);
            reads.add(access.frontierV3$readStoredEntityChunkAfterSync(chunk)
                    .thenApplyAsync(raw -> select(raw, chunk, targets), net.minecraft.Util.backgroundExecutor()));
        }
        return CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new)).thenCompose(ignored -> {
            for (var read : reads) {
                var column = read.join();
                for (var id : column.targets()) {
                    var current = sightings.computeIfAbsent(id, unused -> new HashSet<>());
                    if (current.size() < 2) current.add(column.chunk());
                }
            }
            if (sightings.values().stream().anyMatch(positions -> positions.size() > 1))
                return CompletableFuture.completedFuture(Map.copyOf(sightings));
            return scanBatch(access, columns, targets, end, sightings);
        });
    }

    static Column select(Optional<CompoundTag> raw, ChunkPos chunk, Set<UUID> targets) {
        var data = raw.orElse(null);
        if (!FrontierV3CargoCleanupPersistence.matchesStoredChunk(data, chunk))
            throw new IllegalStateException("misplaced stored entity column in identity census");
        var entities = FrontierV3CargoCleanupPersistence.serializedEntities(data)
                .orElseThrow(() -> new IllegalStateException("invalid stored entity column in identity census"));
        var present = new HashSet<UUID>();
        for (var id : targets) if (entities.containsKey(id)) present.add(id);
        return new Column(chunk, Set.copyOf(present));
    }

    record Column(ChunkPos chunk, Set<UUID> targets) {
        Column { targets = Set.copyOf(targets); }
    }

    record Result(Map<UUID, Set<ChunkPos>> sightings, long writeEpoch) {
        Result {
            var copy = new HashMap<UUID, Set<ChunkPos>>();
            sightings.forEach((id, chunks) -> copy.put(id, Set.copyOf(chunks)));
            sightings = Map.copyOf(copy);
        }

        boolean uniqueAt(UUID entity, ChunkPos expected) {
            return sightings.getOrDefault(entity, Set.of()).equals(Set.of(expected));
        }

        boolean stillCurrent(ServerLevel level) {
            var current = FrontierV3EntityWriteEpochs.globalStamp(level);
            return current.isPresent() && current.orElseThrow() == writeEpoch
                    && !FrontierV3DepartureReturnReadFence.anyReadPending(level);
        }
    }
}
