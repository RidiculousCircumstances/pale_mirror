package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.CargoProjectionRetirement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.util.OptionalLong;
import java.util.Set;

/** Provider evidence from birth, actual save columns and final removal; never custody authority. */
final class FrontierV3CargoFootprintObserver {
    static final String KEY = "pale_mirror_frontier_v3_cargo_cleanup_identity";
    private static final java.util.Map<ServerLevel, FrontierV3CargoRemovalRetry> REMOVALS = new java.util.WeakHashMap<>();
    private FrontierV3CargoFootprintObserver() { }

    static boolean prepareBirth(ServerLevel level, SceneLease lease, long epoch, MinecartChest cart) {
        var footprint = new FrontierV3CargoRetirementFootprint(lease.worldId(), lease.id(),
                FrontierSceneBehaviors.logistics(lease).cargoId(), cart.getUUID(), lease.revision(), epoch,
                Set.of(cart.chunkPosition().toLong()), OptionalLong.empty());
        try {
            var archive = FrontierV3CargoFootprintArchive.at(level, lease.worldId());
            var previous = archive.read(cart.getUUID(), epoch);
            if (previous.isPresent()) {
                // Retry of failed physical admission may reuse the exact birth, never reset
                // a final removal or forget a column already submitted to entity storage.
                if (!previous.get().extendsEvidence(footprint) || previous.get().removalChunk().isPresent()) return false;
            }
            archive.retain(previous.orElse(footprint));
            cart.getPersistentData().put(KEY, footprint.save());
            return true;
        } catch (IOException failure) {
            PaleMirrorMod.LOGGER.error("Cargo birth footprint not durable entity={}; admission deferred", cart.getUUID(), failure);
            return false;
        }
    }

    /** Must complete before vanilla queues the corresponding entity write. */
    static void beforeWrite(ServerLevel level, ChunkPos chunk, CompoundTag data) throws IOException {
        flushRemovals(level);
        var entities = FrontierV3CargoCleanupPersistence.serializedEntities(data)
                .orElseThrow(() -> new IOException("malformed cargo footprint entity inventory"));
        WorldId world = null;
        for (var entity : entities.values()) {
            var persistent = entity.getCompound("NeoForgeData");
            if (!persistent.contains(KEY)) continue;
            try {
                if (!persistent.contains(KEY, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("malformed cleanup identity");
                var declared = FrontierV3CargoRetirementFootprint.load(persistent.getCompound(KEY));
                if (world != null && !world.equals(declared.world())) throw new IOException("mixed cargo footprint worlds");
                world = declared.world();
            } catch (RuntimeException invalid) { throw new IOException("malformed cleanup identity", invalid); }
        }
        // A provider write may occur after the canonical runtime has shut down. Identity
        // comes from the exact birth declaration; an existing archive must still validate it.
        if (world != null) beforeWrite(FrontierV3CargoFootprintArchive.at(level, world), world, chunk, data);
    }

    static void flushRemovals(ServerLevel level) throws IOException {
        var removals = REMOVALS.get(level);
        if (removals != null) removals.flush(removal -> {
            removal.persist(FrontierV3CargoFootprintArchive.at(level, removal.world()));
            FrontierV3CargoCleanupPersistence.witnessPublished(level);
        });
    }

    static void beforeWrite(FrontierV3CargoFootprintArchive archive, WorldId world, ChunkPos chunk, CompoundTag data) throws IOException {
        if (!FrontierV3CargoCleanupPersistence.matchesStoredChunk(data, chunk)) throw new IOException("cargo footprint save column mismatch");
        var entities = FrontierV3CargoCleanupPersistence.serializedEntities(data)
                .orElseThrow(() -> new IOException("malformed cargo footprint entity inventory"));
        for (var entry : entities.entrySet()) {
            var entity = entry.getValue();
            var persistent = entity.getCompound("NeoForgeData");
            if (!persistent.contains(KEY)) continue;
            try {
                if (!entity.getString("id").equals("minecraft:chest_minecart") || !persistent.contains(KEY, Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("invalid cargo footprint carrier declaration");
                }
                var declared = FrontierV3CargoRetirementFootprint.load(persistent.getCompound(KEY));
                if (!world.equals(declared.world()) || !entry.getKey().equals(declared.entity())) throw new IllegalArgumentException("foreign footprint identity");
                var retained = archive.read(declared.entity(), declared.authorityEpoch())
                        .orElseThrow(() -> new IOException("cargo save lacks its durable birth footprint"));
                if (!retained.extendsEvidence(declared)) throw new IOException("cargo save disagrees with retained footprint");
                archive.retain(retained.include(chunk.toLong()));
            } catch (RuntimeException invalid) {
                throw new IOException("invalid cargo footprint before entity write", invalid);
            }
        }
    }

    /** Terminal owner action, not an observational repair of inventory or player changes. */
    static boolean retireIdentity(ServerLevel level, CargoProjectionRetirement retirement, MinecartChest cart) {
        if (!retirement.entityId().equals(cart.getUUID())
                || retirement.disposition() != CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY) return false;
        if (!cart.getPersistentData().contains(KEY)) return true;
        try {
            var declared = FrontierV3CargoRetirementFootprint.load(cart.getPersistentData().getCompound(KEY));
            if (!declared.matches(retirement)) return false;
            var archive = FrontierV3CargoFootprintArchive.at(level, retirement.worldId());
            var retained = archive.read(cart.getUUID(), declared.authorityEpoch())
                    .orElseThrow(() -> new IOException("retiring cargo identity lacks birth footprint"));
            if (!retained.extendsEvidence(declared)) return false;
            archive.retain(retained.include(cart.chunkPosition().toLong()));
            cart.getPersistentData().remove(KEY);
            FrontierV3CargoCleanupPersistence.witnessPublished(level);
            return true;
        } catch (IOException | RuntimeException failure) {
            PaleMirrorMod.LOGGER.error("Cargo cleanup identity could not retire entity={}; evidence retained", cart.getUUID(), failure);
            return false;
        }
    }

    static void observeRemoval(ServerLevel level, WorldId world, Entity entity, CargoProjectionRetirement retirement) {
        if (!(entity instanceof MinecartChest)
                || entity.getRemovalReason() != Entity.RemovalReason.KILLED
                && entity.getRemovalReason() != Entity.RemovalReason.DISCARDED) return;
        var retry = REMOVALS.computeIfAbsent(level, ignored -> new FrontierV3CargoRemovalRetry());
        try {
            FrontierV3CargoRemovalRetry.Removal observed;
            if (entity.getPersistentData().contains(KEY)) {
                var declared = FrontierV3CargoRetirementFootprint.load(entity.getPersistentData().getCompound(KEY));
                observed = FrontierV3CargoRemovalRetry.Removal.from(declared, entity.chunkPosition().toLong());
            } else {
                // The exact terminal obligation outlives metadata retirement and closes
                // destruction between stripping the tag and its first clean entity save.
                if (retirement == null || !retirement.worldId().equals(world)
                        || !retirement.entityId().equals(entity.getUUID())) return;
                observed = FrontierV3CargoRemovalRetry.Removal.from(retirement, entity.chunkPosition().toLong());
            }
            if (!world.equals(observed.world()) || !entity.getUUID().equals(observed.entity())) throw new IllegalArgumentException("foreign final cargo footprint");
            retry.retain(observed);
            flushRemovals(level);
        } catch (IOException failure) {
            PaleMirrorMod.LOGGER.error("Cargo final removal evidence unavailable entity={}; cleanup obligation retained", entity.getUUID(), failure);
        } catch (RuntimeException failure) {
            retry.fence();
            PaleMirrorMod.LOGGER.error("Invalid cargo final removal entity={}; entity saves fenced", entity.getUUID(), failure);
        }
    }
}
