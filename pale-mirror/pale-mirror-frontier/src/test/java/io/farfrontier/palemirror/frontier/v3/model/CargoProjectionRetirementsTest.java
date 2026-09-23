package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CargoProjectionRetirementsTest {
    @Test void terminalAuthorizationSurvivesLatestTombstoneCompactionAndKeepsSeparateAttempts() {
        var first = obligation("first", 1);
        var second = obligation("second", 2);
        var pending = CargoProjectionRetirements.empty().retain(first).retain(second);
        var recovery = new FencedRecoveryState(Map.of(), Map.of(first.authorization().bindingId(), first.authorization()));
        recovery = recovery.retainCargoRetirement(first).compactTombstones(Set.of(first.authorization().bindingId()));
        assertTrue(recovery.tombstones().isEmpty());
        assertEquals(first, recovery.cargoRetirements().pending().get(first.entityId()));
        assertEquals(first, pending.pending().get(first.entityId()));
        assertEquals(second, pending.pending().get(second.entityId()));
        assertSame(pending, pending.retain(first), "exact duplicate is idempotent");
        var contradictory = new CargoProjectionRetirement(first.worldId(), first.leaseId(), first.cargoId(), first.entityId(),
                first.authorization(), CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY);
        assertThrows(IllegalArgumentException.class, () -> pending.retain(contradictory));
    }

    @Test void completeNominalIdentityIsRequiredEvenWithoutHistoricalScene() {
        var value = obligation("first", 1);
        assertThrows(IllegalArgumentException.class, () -> new CargoProjectionRetirement(value.worldId(), value.leaseId(),
                value.cargoId(), UUID.randomUUID(), value.authorization(), value.disposition()));
        assertThrows(IllegalArgumentException.class, () -> new CargoProjectionRetirement(value.worldId(), new SceneLeaseId("lease:foreign"),
                value.cargoId(), value.entityId(), value.authorization(), value.disposition()));
        assertThrows(IllegalArgumentException.class, () -> new CargoProjectionRetirement(value.worldId(), value.leaseId(),
                new SubjectId("cargo:foreign"), value.entityId(), value.authorization(), value.disposition()));
        assertThrows(IllegalArgumentException.class, () -> new CargoProjectionRetirement(new WorldId("frontier:foreign"), value.leaseId(),
                value.cargoId(), value.entityId(), value.authorization(), value.disposition()));
        assertThrows(IllegalArgumentException.class, () -> new CargoProjectionRetirements(Map.of(UUID.randomUUID(), value)));
    }

    @Test void boundedReservationsProtectExistingRetirementsWithoutEviction() {
        var pending = CargoProjectionRetirements.empty().retain(obligation("first", 1));
        assertTrue(pending.canAdmit(CargoProjectionRetirements.MAX_PENDING - 2));
        assertFalse(pending.canAdmit(CargoProjectionRetirements.MAX_PENDING - 1));
        assertFalse(pending.canAdmit(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> pending.canAdmit(-1));
        assertThrows(UnsupportedOperationException.class, () -> pending.pending().clear());
        assertEquals(1, pending.pending().size());
    }

    @Test void dispositionWireTagsAreClosedAndNeverOrdinalBased() {
        assertEquals(1, CargoProjectionRetirement.Disposition.REMOVE_PROJECTION.wireTag());
        assertEquals(2, CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY.wireTag());
        for (var value : CargoProjectionRetirement.Disposition.values()) {
            assertEquals(value, CargoProjectionRetirement.Disposition.fromWireTag(value.wireTag()));
        }
        assertThrows(IllegalArgumentException.class, () -> CargoProjectionRetirement.Disposition.fromWireTag(0));
        assertThrows(IllegalArgumentException.class, () -> CargoProjectionRetirement.Disposition.fromWireTag(3));
    }

    @Test void fullCleanupCapacityBlocksOnlyNewCargoAndPreservesAllObligations() {
        var pending = new LinkedHashMap<UUID, CargoProjectionRetirement>();
        for (int i = 0; i < CargoProjectionRetirements.MAX_PENDING; i++) {
            var value = obligation("full-" + i, i + 1L);
            pending.put(value.entityId(), value);
        }
        var recovery = new FencedRecoveryState(Map.of(), Map.of(), new CargoProjectionRetirements(pending));
        assertFalse(recovery.canAdmitCargoProjection());
        var incoming = obligation("new", 1);
        var proof = incoming.authorization();
        assertThrows(IllegalArgumentException.class, () -> recovery.prepare(FencedRecoveryBinding.prepared(
                proof.bindingId(), FencedRecoveryAsset.CARGO, proof.ownerId(), proof.ownerRevision(), 1, true)));
        var body = FencedRecoveryBinding.prepared(new SubjectId("recovery:unrelated-body"), FencedRecoveryAsset.BODY,
                new SubjectId("owner:unrelated"), 1, 1, true);
        var advanced = recovery.prepare(body).running(body.bindingId(), 1).observed(body.bindingId(), 1).confirm(body.bindingId(), 1);
        assertEquals(pending, advanced.cargoRetirements().pending());
        assertEquals(CargoProjectionRetirements.MAX_PENDING, recovery.cargoRetirements().pending().size());
    }

    @Test void compactedTombstoneCannotResetEpochOrReactivateTheSamePhysicalIdentity() {
        var old = obligation("old", 7);
        var proof = old.authorization();
        var recovery = new FencedRecoveryState(Map.of(), Map.of(proof.bindingId(), proof))
                .retainCargoRetirement(old).compactTombstones(Set.of(proof.bindingId()));
        assertEquals(8, recovery.nextEpoch(proof.bindingId()));
        var nextOwner = FrontierSceneLeaseStateSupport.recoveryOwner(new SceneLeaseId("lease:next"));
        assertThrows(IllegalArgumentException.class, () -> recovery.prepare(FencedRecoveryBinding.prepared(
                proof.bindingId(), FencedRecoveryAsset.CARGO, nextOwner, 8, 7, true)));
        assertThrows(IllegalArgumentException.class, () -> recovery.prepare(FencedRecoveryBinding.prepared(
                proof.bindingId(), FencedRecoveryAsset.CARGO, proof.ownerId(), 8, 8, true)));
        var successor = recovery.prepare(FencedRecoveryBinding.prepared(
                proof.bindingId(), FencedRecoveryAsset.CARGO, nextOwner, 8, 8, true));
        assertEquals(old, successor.cargoRetirements().pending().get(old.entityId()));
        assertEquals(nextOwner, successor.current().get(proof.bindingId()).ownerId());
    }

    @Test void historicalCompactionDoesNotPermitCrossWorldAuthorization() {
        var old = obligation("old", 1);
        var pending = CargoProjectionRetirements.empty().retain(old);
        assertDoesNotThrow(() -> pending.validateContext(old.worldId(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> pending.validateContext(new WorldId("frontier:foreign"), Map.of()));
    }

    @Test void acknowledgementMatchesTheEntireObligationAndDoesNotRetireANewerAttempt() {
        var old = obligation("old", 1);
        var next = obligation("next", 2);
        var recovery = new FencedRecoveryState(Map.of(), Map.of(), CargoProjectionRetirements.empty().retain(old).retain(next));
        var forged = new CargoProjectionRetirement(old.worldId(), old.leaseId(), old.cargoId(), old.entityId(),
                old.authorization(), CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY);
        assertThrows(IllegalArgumentException.class, () -> recovery.acknowledgeCargoCleanupSaved(forged));
        var acknowledged = recovery.acknowledgeCargoCleanupSaved(old);
        assertEquals(Map.of(next.entityId(), next), acknowledged.cargoRetirements().pending());
        assertThrows(IllegalArgumentException.class, () -> acknowledged.acknowledgeCargoCleanupSaved(old));
        assertEquals(2, recovery.cargoRetirements().pending().size());
    }

    private static CargoProjectionRetirement obligation(String suffix, long epoch) {
        var world = new WorldId("frontier:retirement");
        var lease = new SceneLeaseId("lease:" + suffix);
        var cargo = new SubjectId("cargo:shared");
        var proof = new FencedRecoveryTombstone(FrontierSceneLeaseStateSupport.cargoRecoveryBindingId(cargo),
                FencedRecoveryAsset.CARGO, FrontierSceneLeaseStateSupport.recoveryOwner(lease), epoch, epoch,
                FencedRecoveryDisposition.REJECT_STALE, "confirmed");
        return new CargoProjectionRetirement(world, lease, cargo, CargoCarrierIdentity.id(world, lease, cargo), proof,
                CargoProjectionRetirement.Disposition.REMOVE_PROJECTION);
    }
}
