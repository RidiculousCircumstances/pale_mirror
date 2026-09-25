package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3EntityPermanentStorageAccessor;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3ServerEntityManagerAccessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Bounded no-load inspection of exactly named saved scene custodians. Evidence only. */
final class FrontierV3StoredEntityInspection {
    private static final int MAX_TARGETS = 4_096;
    private FrontierV3StoredEntityInspection() { }

    static CompletableFuture<StampedSnapshot> inspect(ServerLevel level, ChunkPos chunk, Set<UUID> targets) {
        Objects.requireNonNull(level); Objects.requireNonNull(chunk); Objects.requireNonNull(targets);
        if (!FrontierV3PhysicalWorld.isPhysical(level) || targets.isEmpty() || targets.size() > MAX_TARGETS)
            throw new IllegalArgumentException("invalid bounded physical entity inspection");
        var storage = ((FrontierV3EntityPermanentStorageAccessor)
                ((FrontierV3ServerEntityManagerAccessor) level).frontierV3$getEntityManager()).frontierV3$getPermanentStorage();
        if (!(storage instanceof FrontierV3StoredEntityInventoryAccess access))
            throw new IllegalStateException("entity storage does not provide no-load inspection");
        var stamp = FrontierV3EntityWriteEpochs.stamp(level, chunk);
        if (stamp.isEmpty()) throw new IllegalStateException("entity-write index overflow prevents no-load inspection");
        Set<UUID> selected = Set.copyOf(targets);
        return access.frontierV3$readStoredEntityChunk(chunk)
                .thenApplyAsync(raw -> new StampedSnapshot(
                        select(raw.orElse(null), chunk, selected, level.registryAccess()), stamp.orElseThrow()), level.getServer());
    }

    static Snapshot select(CompoundTag raw, ChunkPos chunk, Set<UUID> targets, HolderLookup.Provider registries) {
        if (!FrontierV3CargoCleanupPersistence.matchesStoredChunk(raw, chunk))
            throw new IllegalStateException("misplaced stored entity chunk");
        var entities = FrontierV3CargoCleanupPersistence.serializedEntities(raw)
                .orElseThrow(() -> new IllegalStateException("invalid stored entity inventory"));
        var actors = new HashMap<UUID, FrontierV3SceneDeparturePersistence.SavedBody>();
        var cargo = new HashMap<UUID, FrontierV3CargoDeparturePersistence.SavedCart>();
        var present = new java.util.HashSet<UUID>();
        for (var id : targets) {
            var entity = entities.get(id);
            if (entity == null) continue;
            present.add(id);
            FrontierV3SceneDeparturePersistence.SavedBody.from(entity).ifPresent(body -> actors.put(id, body));
            FrontierV3CargoDeparturePersistence.SavedCart.from(entity, registries)
                    .ifPresent(cart -> cargo.put(id, cart));
        }
        return new Snapshot(Set.copyOf(present), Map.copyOf(actors), Map.copyOf(cargo));
    }

    record Snapshot(Set<UUID> present,
                    Map<UUID, FrontierV3SceneDeparturePersistence.SavedBody> actors,
                    Map<UUID, FrontierV3CargoDeparturePersistence.SavedCart> cargo) {
        Snapshot {
            present = Set.copyOf(present); actors = Map.copyOf(actors); cargo = Map.copyOf(cargo);
        }

        boolean matches(FrontierV3SceneDeparture receipt) {
            var id = receipt.carrier().identity().entityId();
            var body = actors.get(id);
            return present.contains(id) && body != null && body.matches(receipt);
        }

        boolean matches(FrontierV3CargoDeparture receipt) {
            var id = receipt.entityId();
            var cart = cargo.get(id);
            return present.contains(id) && cart != null && cart.matches(receipt);
        }
    }

    record StampedSnapshot(Snapshot snapshot, long writeEpoch) {
        StampedSnapshot { Objects.requireNonNull(snapshot); }

        boolean stillCurrent(ServerLevel level, ChunkPos chunk) {
            var current = FrontierV3EntityWriteEpochs.stamp(level, chunk);
            return current.isPresent() && current.orElseThrow() == writeEpoch
                    && !FrontierV3DepartureReturnReadFence.readPending(level, chunk);
        }
    }
}
