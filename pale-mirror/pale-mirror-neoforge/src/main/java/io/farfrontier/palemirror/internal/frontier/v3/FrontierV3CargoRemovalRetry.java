package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.CargoCarrierIdentity;
import io.farfrontier.palemirror.frontier.v3.model.CargoProjectionRetirement;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.Optional;

/** Unsaved physical observations. Entity writes must wait until these are durable. */
final class FrontierV3CargoRemovalRetry {
    static final int MAX_PENDING = 4096;
    static final int MAX_WRITES_PER_PASS = 8;
    private final LinkedHashSet<Removal> pending = new LinkedHashSet<>();
    private boolean lostEvidence;

    record Removal(WorldId world, SceneLeaseId lease, SubjectId cargo, UUID entity,
                   long revision, long epoch, long chunk, Optional<FrontierV3CargoRetirementFootprint> declaration) {
        Removal {
            java.util.Objects.requireNonNull(declaration);
            if (!CargoCarrierIdentity.id(world, lease, cargo).equals(entity) || revision < 0 || epoch < 1)
                throw new IllegalArgumentException("invalid cargo removal identity");
        }
        static Removal from(FrontierV3CargoRetirementFootprint declaration, long chunk) {
            return new Removal(declaration.world(), declaration.lease(), declaration.cargo(), declaration.entity(),
                    declaration.sceneRevision(), declaration.authorityEpoch(), chunk, Optional.of(declaration));
        }
        static Removal from(CargoProjectionRetirement retirement, long chunk) {
            return new Removal(retirement.worldId(), retirement.leaseId(), retirement.cargoId(), retirement.entityId(),
                    retirement.authorization().ownerRevision(), retirement.authorization().retiredEpoch(), chunk, Optional.empty());
        }
        void persist(FrontierV3CargoFootprintArchive archive) throws IOException {
            var retained = archive.read(entity, epoch).orElseThrow(() -> new IOException("final cargo removal lacks birth footprint"));
            if (!world.equals(retained.world()) || !lease.equals(retained.lease()) || !cargo.equals(retained.cargo())
                    || revision != retained.sceneRevision()) throw new IOException("final cargo removal identity mismatch");
            if (declaration.isPresent() && !retained.extendsEvidence(declaration.orElseThrow()))
                throw new IOException("final cargo removal declaration disagrees with birth history");
            try { archive.retain(retained.removedAt(chunk)); }
            catch (IllegalArgumentException invalid) {
                throw new IOException("cargo removal exceeds retained footprint bounds", invalid);
            }
        }
    }

    @FunctionalInterface interface Writer { void write(Removal removal) throws IOException; }

    void retain(Removal removal) throws IOException {
        if (pending.contains(removal)) return;
        if (pending.size() >= MAX_PENDING) {
            lostEvidence = true;
            throw new IOException("cargo removal retry capacity exhausted; entity saves fenced until restart");
        }
        pending.add(removal);
    }

    void fence() { lostEvidence = true; }

    void flush(Writer writer) throws IOException {
        if (lostEvidence) throw new IOException("cargo removal evidence lost; entity saves fenced until restart");
        int remaining = MAX_WRITES_PER_PASS;
        var iterator = pending.iterator();
        while (iterator.hasNext() && remaining-- > 0) {
            writer.write(iterator.next());
            iterator.remove(); // Only a durable write consumes an observation.
        }
        if (!pending.isEmpty()) throw new IOException("cargo removal observations still pending; entity save deferred");
    }
}
