package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Shared positive disk evidence. Never loads a chunk or grants custody from absence. */
final class FrontierV3StoredBodyEvidence {
    private FrontierV3StoredBodyEvidence() { }
    static CompletableFuture<Proof> inspect(ServerLevel level, Set<UUID> ids) {
        return FrontierV3StoredEntityCensus.scan(level, ids).thenComposeAsync(census -> {
            Map<ChunkPos, Set<UUID>> columns = new HashMap<>();
            for (UUID id : ids) {
                var matches = census.sightings().getOrDefault(id, Set.of());
                if (matches.size() != 1) continue; // Independent bodies do not inherit another target's ambiguity.
                columns.computeIfAbsent(matches.iterator().next(), ignored -> new HashSet<>()).add(id);
            }
            Map<ChunkPos, CompletableFuture<FrontierV3StoredEntityInspection.StampedSnapshot>> reads = new HashMap<>();
            columns.forEach((column, selected) -> reads.put(column,
                    FrontierV3StoredEntityInspection.inspect(level, column, selected)));
            return CompletableFuture.allOf(reads.values().toArray(CompletableFuture[]::new))
                    .thenApplyAsync(ignored -> {
                        Map<ChunkPos, FrontierV3StoredEntityInspection.StampedSnapshot> snapshots = new HashMap<>();
                        reads.forEach((column, future) -> snapshots.put(column, future.join()));
                        return new Proof(census, columns, snapshots, ids);
                    }, level.getServer());
        }, level.getServer());
    }

    static record Proof(FrontierV3StoredEntityCensus.Result census, Map<ChunkPos, Set<UUID>> columns,
                                  Map<ChunkPos, FrontierV3StoredEntityInspection.StampedSnapshot> snapshots,
                                  Set<UUID> ids) {
        Proof {
            Map<ChunkPos, Set<UUID>> copied = new HashMap<>();
            columns.forEach((column, members) -> copied.put(column, Set.copyOf(members)));
            columns = Map.copyOf(copied); snapshots = Map.copyOf(snapshots); ids = Set.copyOf(ids);
        }
        boolean complete() {
            if (columns.isEmpty() || !columns.keySet().equals(snapshots.keySet())) return false;
            Set<UUID> covered = new HashSet<>();
            for (var entry : columns.entrySet()) {
                for (UUID id : entry.getValue()) {
                    if (!covered.add(id) || !census.uniqueAt(id, entry.getKey())) return false;
                }
            }
            return covered.equals(ids);
        }
        boolean current(ServerLevel level) {
            return complete() && census.stillCurrent(level)
                    && snapshots.entrySet().stream().allMatch(entry -> entry.getValue().stillCurrent(level, entry.getKey()));
        }
        FrontierV3SceneDeparturePersistence.SavedBody body(UUID id) {
            ChunkPos column = census.sightings().getOrDefault(id, Set.of()).stream().findFirst().orElse(null);
            if (column == null) return null;
            var snapshot = snapshots.get(column);
            return census.uniqueAt(id, column) && snapshot != null ? snapshot.snapshot().actors().get(id) : null;
        }
    }

}
