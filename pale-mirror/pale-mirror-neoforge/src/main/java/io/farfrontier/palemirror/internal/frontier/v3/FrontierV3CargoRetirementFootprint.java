package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.CargoCarrierIdentity;
import io.farfrontier.palemirror.frontier.v3.model.CargoProjectionRetirement;
import java.util.HashSet;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Immutable provider evidence, never permission to spawn, delete or restore inventory. */
record FrontierV3CargoRetirementFootprint(WorldId world, SceneLeaseId lease, SubjectId cargo,
        UUID entity, long sceneRevision, long authorityEpoch, Set<Long> chunks, OptionalLong removalChunk) {
    static final int MAX_CHUNKS = FrontierV3EntitySaveBatch.MAX_CHUNKS;

    FrontierV3CargoRetirementFootprint {
        Objects.requireNonNull(world); Objects.requireNonNull(lease); Objects.requireNonNull(cargo);
        Objects.requireNonNull(entity); Objects.requireNonNull(removalChunk);
        chunks = Set.copyOf(chunks);
        if (!entity.equals(CargoCarrierIdentity.id(world, lease, cargo)) || sceneRevision < 0 || authorityEpoch < 1
                || chunks.isEmpty() || chunks.size() > MAX_CHUNKS
                || removalChunk.isPresent() && !chunks.contains(removalChunk.getAsLong())) {
            throw new IllegalArgumentException("invalid exact cargo retirement footprint");
        }
    }

    FrontierV3CargoRetirementFootprint include(long chunk) {
        if (chunks.contains(chunk)) return this;
        var next = new HashSet<>(chunks); next.add(chunk);
        return new FrontierV3CargoRetirementFootprint(world, lease, cargo, entity, sceneRevision, authorityEpoch, next, removalChunk);
    }

    /** Records the first actual removal plus every later removal column; never durable absence. */
    FrontierV3CargoRetirementFootprint removedAt(long chunk) {
        if (removalChunk.isPresent()) {
            // A crash before the entity-region save may naturally return the old cart.
            // A later observed removal adds coverage; it does not erase the earlier fact.
            return include(chunk);
        }
        var next = include(chunk);
        return new FrontierV3CargoRetirementFootprint(world, lease, cargo, entity, sceneRevision, authorityEpoch,
                next.chunks, OptionalLong.of(chunk));
    }

    boolean matches(CargoProjectionRetirement retirement) {
        return retirement.disposition() == CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY
                && matchesIdentity(retirement);
    }

    boolean matchesIdentity(CargoProjectionRetirement retirement) {
        return world.equals(retirement.worldId()) && lease.equals(retirement.leaseId())
                && cargo.equals(retirement.cargoId()) && entity.equals(retirement.entityId())
                && sceneRevision == retirement.authorization().ownerRevision()
                && authorityEpoch == retirement.authorization().retiredEpoch();
    }

    /** Pure completeness predicate; caller still owes current successful write+sync evidence. */
    boolean coversRemovedFootprint(Set<Long> observedAbsentChunks) {
        return removalChunk.isPresent() && observedAbsentChunks.containsAll(chunks);
    }

    /** Persistence may append evidence, never replace identity or forget an old column. */
    boolean extendsEvidence(FrontierV3CargoRetirementFootprint prior) {
        return world.equals(prior.world) && lease.equals(prior.lease) && cargo.equals(prior.cargo)
                && entity.equals(prior.entity) && sceneRevision == prior.sceneRevision && authorityEpoch == prior.authorityEpoch
                && chunks.containsAll(prior.chunks)
                && (prior.removalChunk.isEmpty() || removalChunk.equals(prior.removalChunk));
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("format", 1); tag.putString("world", world.value()); tag.putString("lease", lease.value());
        tag.putString("cargo", cargo.value()); tag.putUUID("entity", entity);
        tag.putLong("revision", sceneRevision); tag.putLong("epoch", authorityEpoch);
        tag.putLongArray("chunks", chunks.stream().sorted().mapToLong(Long::longValue).toArray());
        tag.putInt("removal", removalChunk.isPresent() ? 1 : 0);
        removalChunk.ifPresent(chunk -> tag.putLong("removalChunk", chunk));
        return tag;
    }

    static FrontierV3CargoRetirementFootprint load(CompoundTag tag) {
        if (!tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != 1
                || !tag.contains("world", Tag.TAG_STRING) || !tag.contains("lease", Tag.TAG_STRING)
                || !tag.contains("cargo", Tag.TAG_STRING) || !tag.hasUUID("entity")
                || !tag.contains("revision", Tag.TAG_LONG) || !tag.contains("epoch", Tag.TAG_LONG)
                || !tag.contains("chunks", Tag.TAG_LONG_ARRAY) || !tag.contains("removal", Tag.TAG_INT)) {
            throw new IllegalArgumentException("incomplete cargo retirement footprint");
        }
        int removal = tag.getInt("removal");
        if (removal != 0 && removal != 1 || (removal == 1 && !tag.contains("removalChunk", Tag.TAG_LONG))
                || removal == 0 && tag.contains("removalChunk")) {
            throw new IllegalArgumentException("invalid cargo removal declaration");
        }
        long[] columns = tag.getLongArray("chunks");
        if (columns.length < 1 || columns.length > MAX_CHUNKS) throw new IllegalArgumentException("invalid footprint extent");
        var chunks = new HashSet<Long>();
        for (long column : columns) {
            if (!chunks.add(column)) throw new IllegalArgumentException("duplicate cargo footprint column");
        }
        return new FrontierV3CargoRetirementFootprint(new WorldId(tag.getString("world")), new SceneLeaseId(tag.getString("lease")),
                new SubjectId(tag.getString("cargo")), tag.getUUID("entity"), tag.getLong("revision"), tag.getLong("epoch"),
                chunks, removal == 1 ? OptionalLong.of(tag.getLong("removalChunk")) : OptionalLong.empty());
    }
}
