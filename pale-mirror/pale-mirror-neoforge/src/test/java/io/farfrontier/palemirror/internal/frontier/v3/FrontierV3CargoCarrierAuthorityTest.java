package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3CargoCarrierAuthorityTest {
    private static final WorldId WORLD = new WorldId("world:cargo-authority");
    private static final SubjectId ACTOR = new SubjectId("actor:cargo-authority");
    private static final SubjectId CARGO = new SubjectId("cargo:authority");
    private static final BlockPosition FLOOR = new BlockPosition(0, 64, 0);
    private static final SceneLease LEASE = SceneLease.atExactPositions(new SceneLeaseId("lease:cargo-authority"), WORLD,
            new SubjectId("operation:cargo-authority"), CARGO, FLOOR, FLOOR, SimInstant.ZERO, 7, SceneLeaseStatus.PREPARED,
            Optional.empty(), List.of(new SceneMember(ACTOR, SceneLease.deterministicEntityId(WORLD, ACTOR))),
            Map.of(ACTOR, BodyPosition.aboveSupportCell(FLOOR)));
    private static final SubjectId BINDING = FrontierSceneLeaseStateSupport.cargoRecoveryBindingId(CARGO);
    private static final SubjectId OWNER = FrontierSceneLeaseStateSupport.recoveryOwner(LEASE);

    private static FencedRecoveryState prepared(FencedRecoveryAsset asset, SubjectId owner, long revision, long epoch) {
        return FencedRecoveryState.empty().prepare(FencedRecoveryBinding.prepared(BINDING, asset, owner, revision, epoch, true));
    }

    @Test
    void theSameLeaseRevisionCannotReuseAPreviousAttemptEpoch() {
        var first = prepared(FencedRecoveryAsset.CARGO, OWNER, 7, 1);
        var successor = prepared(FencedRecoveryAsset.CARGO, OWNER, 7, 2);
        assertEquals(1, FrontierV3CargoCarrierAuthority.currentEpoch(first, LEASE).orElseThrow());
        assertTrue(FrontierV3CargoCarrierAuthority.matches(first, LEASE, 1));
        assertFalse(FrontierV3CargoCarrierAuthority.matches(successor, LEASE, 1));
        assertTrue(FrontierV3CargoCarrierAuthority.matches(successor, LEASE, 2));
        assertFalse(FrontierV3CargoCarrierAuthority.matches(successor, LEASE, 0));
    }

    @Test
    void missingForeignOrWronglyTypedAuthorityNeverSuppliesAnEpoch() {
        var invalid = List.of(FencedRecoveryState.empty(),
                prepared(FencedRecoveryAsset.BODY, OWNER, 7, 1),
                prepared(FencedRecoveryAsset.CARGO, new SubjectId("owner:foreign"), 7, 1),
                prepared(FencedRecoveryAsset.CARGO, OWNER, 8, 1));
        for (var recovery : invalid) {
            assertTrue(FrontierV3CargoCarrierAuthority.currentEpoch(recovery, LEASE).isEmpty());
            assertFalse(FrontierV3CargoCarrierAuthority.matches(recovery, LEASE, 1));
        }
    }

    @Test
    void cleanupUsesOnlyTheExactRetiredAttemptNotAnAbsentOrNewerTombstone() {
        var closed = LEASE.withStatus(SceneLeaseStatus.CLOSED);
        var first = prepared(FencedRecoveryAsset.CARGO, OWNER, 7, 1).running(BINDING, 1).observed(BINDING, 1).confirm(BINDING, 1);
        assertTrue(FrontierV3CargoCarrierAuthority.matches(first, closed, 1));
        assertFalse(FrontierV3CargoCarrierAuthority.matches(first, LEASE, 1), "retired evidence cannot authorize live work");
        assertFalse(FrontierV3CargoCarrierAuthority.matches(first, closed, 2));
        assertFalse(FrontierV3CargoCarrierAuthority.matches(FencedRecoveryState.empty(), closed, 1));
        var successor = first.prepare(FencedRecoveryBinding.prepared(BINDING, FencedRecoveryAsset.CARGO, OWNER, 7, 2, true));
        assertFalse(FrontierV3CargoCarrierAuthority.matches(successor, LEASE, 1));
        assertTrue(FrontierV3CargoCarrierAuthority.matches(successor, LEASE, 2));
    }

    @Test void retainedObligationSurvivesTombstoneCompactionAndValidatesEveryDeclarationField() {
        var closed = LEASE.withStatus(SceneLeaseStatus.CLOSED);
        var terminal = prepared(FencedRecoveryAsset.CARGO, OWNER, 7, 1)
                .running(BINDING, 1).observed(BINDING, 1).confirm(BINDING, 1);
        var retirement = CargoProjectionRetirement.confirmed(closed, terminal.tombstones().get(BINDING),
                CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY);
        var retained = terminal.retainCargoRetirement(retirement);
        var compacted = new FencedRecoveryState(Map.of(), Map.of(), retained.cargoRetirements());
        assertTrue(FrontierV3CargoCarrierAuthority.matches(compacted, closed, 1));
        assertFalse(FrontierV3CargoCarrierAuthority.matches(compacted, closed, 2));
        assertFalse(FrontierV3CargoCarrierAuthority.matches(compacted, LEASE, 1));
        var declaration = new net.minecraft.nbt.CompoundTag();
        declaration.putString(FrontierV3CargoCarrierExecutor.LEASE_KEY, closed.id().value());
        declaration.putString(FrontierV3CargoCarrierExecutor.CARGO_KEY, CARGO.value());
        declaration.putLong(FrontierV3CargoCarrierExecutor.REVISION_KEY, closed.revision());
        declaration.putLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY, 1);
        assertTrue(FrontierV3CargoCarrierExecutor.matchesRetiredDeclaration(retirement, retirement.entityId(), declaration));
        for (String key : List.of(FrontierV3CargoCarrierExecutor.LEASE_KEY, FrontierV3CargoCarrierExecutor.CARGO_KEY,
                FrontierV3CargoCarrierExecutor.REVISION_KEY, FrontierV3CargoCarrierExecutor.EPOCH_KEY)) {
            var missing = declaration.copy(); missing.remove(key);
            assertFalse(FrontierV3CargoCarrierExecutor.matchesRetiredDeclaration(retirement, retirement.entityId(), missing));
            var forged = declaration.copy(); forged.putString(key, "foreign");
            assertFalse(FrontierV3CargoCarrierExecutor.matchesRetiredDeclaration(retirement, retirement.entityId(), forged));
        }
        assertFalse(FrontierV3CargoCarrierExecutor.matchesRetiredDeclaration(retirement, new java.util.UUID(0, 42), declaration));
    }
}
