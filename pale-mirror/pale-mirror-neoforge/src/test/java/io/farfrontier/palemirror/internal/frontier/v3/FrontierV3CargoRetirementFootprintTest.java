package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.CargoCarrierIdentity;
import io.farfrontier.palemirror.frontier.v3.model.CargoProjectionRetirement;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryTombstone;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryAsset;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryDisposition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLeaseStateSupport;
import org.junit.jupiter.api.Test;
import java.util.OptionalLong;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3CargoRetirementFootprintTest {
    @Test void missingOrWrongEpochBirthCannotAcknowledgeEitherTerminalDisposition() {
        var birth = initial();
        var tombstone = new FencedRecoveryTombstone(
                FrontierSceneLeaseStateSupport.cargoRecoveryBindingId(birth.cargo()), FencedRecoveryAsset.CARGO,
                FrontierSceneLeaseStateSupport.recoveryOwner(birth.lease()), birth.sceneRevision(),
                birth.authorityEpoch(), FencedRecoveryDisposition.REJECT_STALE, "test retirement");
        var foreignEpoch = new FrontierV3CargoRetirementFootprint(birth.world(), birth.lease(), birth.cargo(),
                birth.entity(), birth.sceneRevision(), birth.authorityEpoch() + 1, birth.chunks(), birth.removalChunk());
        for (var disposition : CargoProjectionRetirement.Disposition.values()) {
            var retirement = new CargoProjectionRetirement(birth.world(), birth.lease(), birth.cargo(),
                    birth.entity(), tombstone, disposition);
            assertFalse(FrontierV3CargoCleanupPersistence.hasExactFootprint(retirement, java.util.List.of()));
            assertFalse(FrontierV3CargoCleanupPersistence.hasExactFootprint(retirement, java.util.List.of(foreignEpoch)));
            assertTrue(FrontierV3CargoCleanupPersistence.hasExactFootprint(retirement, java.util.List.of(birth)));
        }
    }

    @Test void strictCodecRetainsEvidenceAndRejectsMissingOrContradictoryDeclarations() {
        for (var value : java.util.List.of(initial(), initial().include(22).removedAt(33))) {
            assertEquals(value, FrontierV3CargoRetirementFootprint.load(value.save()));
            for (var key : value.save().getAllKeys()) {
                var missing = value.save(); missing.remove(key);
                assertThrows(IllegalArgumentException.class, () -> FrontierV3CargoRetirementFootprint.load(missing), key);
            }
        }
        var duplicate = initial().save(); duplicate.putLongArray("chunks", new long[]{11, 11});
        assertThrows(IllegalArgumentException.class, () -> FrontierV3CargoRetirementFootprint.load(duplicate));
        var foreign = initial().save(); foreign.putUUID("entity", new java.util.UUID(0, 9));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3CargoRetirementFootprint.load(foreign));
        var unknown = initial().save(); unknown.putInt("removal", 2);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3CargoRetirementFootprint.load(unknown));
        var undeclared = initial().save(); undeclared.putLong("removalChunk", 11);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3CargoRetirementFootprint.load(undeclared));
    }

    @Test void persistenceCannotRegressFootprintChangeEpochOrEraseObservedRemoval() {
        var first = initial(); var moved = first.include(22); var removed = moved.removedAt(33);
        assertTrue(moved.extendsEvidence(first)); assertTrue(removed.extendsEvidence(moved));
        assertFalse(first.extendsEvidence(moved)); assertFalse(moved.extendsEvidence(removed));
        assertTrue(removed.extendsEvidence(removed));
        assertTrue(removed.include(44).extendsEvidence(removed));
        assertFalse(removed.extendsEvidence(removed.include(44)));
        var foreign = new FrontierV3CargoRetirementFootprint(first.world(), first.lease(), first.cargo(), first.entity(),
                first.sceneRevision(), first.authorityEpoch() + 1, first.chunks(), first.removalChunk());
        assertFalse(foreign.extendsEvidence(first));
    }

    private static FrontierV3CargoRetirementFootprint initial() {
        var world = new WorldId("frontier:footprint");
        var lease = new SceneLeaseId("lease:footprint");
        var cargo = new SubjectId("cargo:footprint");
        return new FrontierV3CargoRetirementFootprint(world, lease, cargo, CargoCarrierIdentity.id(world, lease, cargo),
                7, 2, Set.of(11L), OptionalLong.empty());
    }

    @Test void newChunkAbsenceCannotEraseOldChunkOrReplaceFinalRemovalEvidence() {
        var moving = initial().include(22L);
        assertFalse(moving.coversRemovedFootprint(Set.of(11L, 22L)));
        var removed = moving.removedAt(33L);
        assertFalse(removed.coversRemovedFootprint(Set.of(33L)));
        assertFalse(removed.coversRemovedFootprint(Set.of(22L, 33L)));
        assertTrue(removed.coversRemovedFootprint(Set.of(11L, 22L, 33L)));
        assertEquals(Set.of(11L), initial().chunks());
        assertEquals(removed, removed.removedAt(33L));
        var returned = removed.include(44L);
        assertEquals(removed.removalChunk(), returned.removalChunk(), "natural return never erases prior observation");
        assertEquals(returned, removed.removedAt(44L), "later removal retains both columns");
        assertFalse(returned.coversRemovedFootprint(Set.of(11L, 22L, 33L)));
        assertTrue(returned.coversRemovedFootprint(Set.of(11L, 22L, 33L, 44L)));
    }

    @Test void capacityRejectsWithoutEvictingHistoricalChunks() {
        var first = initial();
        var chunks = new java.util.HashSet<Long>();
        for (long i = 0; i < FrontierV3CargoRetirementFootprint.MAX_CHUNKS; i++) chunks.add(i);
        var full = new FrontierV3CargoRetirementFootprint(first.world(), first.lease(), first.cargo(), first.entity(),
                first.sceneRevision(), first.authorityEpoch(), chunks, OptionalLong.empty());
        assertThrows(IllegalArgumentException.class, () -> full.include(Long.MAX_VALUE));
        assertEquals(FrontierV3CargoRetirementFootprint.MAX_CHUNKS, full.chunks().size());
        assertThrows(UnsupportedOperationException.class, () -> full.chunks().clear());
    }
}
