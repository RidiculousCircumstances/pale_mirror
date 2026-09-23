package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhysicalProjectionCustodyTest {
    @Test
    void projectionMismatchRetainsActualEvidenceAndEpochThroughRegisteredCommandAndRecovery() {
        var world = new WorldId("frontier:projection-conflict-command");
        var configuration = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(configuration);
        SubjectId object = new SubjectId("container:projection-conflict"), scope = new SubjectId("scope:projection-conflict");
        var expected = PhysicalReplicaRecord.expected(object, "container.depot", 10L, "sha256:projection", "owned:projection");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, "declare-conflict", new PhysicalReplicaCustodyPayloads.ReplicaDeclared(expected)));
        var lease = new PhysicalCustodyLease(scope, object, new SubjectId("provider:projection-test"), 1L, 10L, 1L,
                PhysicalCustodyLeaseStatus.PREPARING, null);
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, "prepare-conflict", new PhysicalReplicaCustodyPayloads.ProjectionCustodyPrepared(lease)));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, submit(engine, "false-conflict",
                ReplicaCustodyDiagnosticProducer.projectionConflict(scope, 1L, 10L, 1L, expected.fingerprint(), expected.provenance())));
        var mismatch = ReplicaCustodyDiagnosticProducer.projectionConflict(scope, 1L, 10L, 1L, "sha256:actual-player-change", "foreign:player");
        DiagnosticProducerContract.requireAdmitted(mismatch);
        assertEquals(java.util.Optional.of(mismatch.diagnostic()), DiagnosticIncidentExtractor.tuple(mismatch));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, submit(engine, "conflict", mismatch));
        var recovered = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.recover(configuration,
                new io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage(world,
                        java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord(engine.checkpoint(), 3L)), java.util.List.of()));
        var state = new FrontierWorldStateCodec().decode(recovered.checkpoint().canonicalState());
        var actual = state.replicaCustody().replicas().get(object);
        assertEquals(expected.fingerprint(), actual.fingerprint());
        assertEquals("sha256:actual-player-change", actual.observedFingerprint().orElseThrow());
        assertEquals("foreign:player", actual.observedProvenance().orElseThrow());
        assertEquals(PhysicalReplicaConflictReason.FINGERPRINT_AND_PROVENANCE_MISMATCH, actual.conflictReason().orElseThrow());
        var unresolved = state.replicaCustody().custodyByScope().get(scope);
        assertEquals(PhysicalCustodyLeaseStatus.UNRESOLVED, unresolved.status());
        assertEquals(1L, unresolved.authorityEpoch());
        assertEquals(actual.replicaRevision(), unresolved.expectedReplicaRevision());
        assertEquals(mismatch.diagnostic(), state.replicaCustody().diagnostics().get(scope));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class,
                submit(recovered, "release-conflict", new PhysicalReplicaCustodyPayloads.CustodyReleased(scope, 1L, 10L, actual.replicaRevision())));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, submit(recovered, "repeat-conflict", mismatch));
        // A local conflict does not quarantine unrelated canonical commands.
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, submit(recovered, "unrelated",
                new PhysicalReplicaCustodyPayloads.ReplicaDeclared(PhysicalReplicaRecord.expected(new SubjectId("container:unrelated-projection"),
                        "container.depot", 10L, "sha256:other", "owned:other"))));
        var beforeRecovery = recovered.checkpoint();
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, submit(recovered, "stale-recovery",
                new PhysicalReplicaCustodyPayloads.ProjectionCustodyConfirmed(scope, 1L, 10L, 1L, expected.fingerprint(), expected.provenance())));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, submit(recovered, "still-conflicted",
                new PhysicalReplicaCustodyPayloads.ProjectionCustodyConfirmed(scope, 1L, 10L, actual.replicaRevision(), "sha256:actual-player-change", "foreign:player")));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, submit(recovered, "foreign-recovery-epoch",
                new PhysicalReplicaCustodyPayloads.ProjectionCustodyConfirmed(scope, 2L, 10L, actual.replicaRevision(), expected.fingerprint(), expected.provenance())));
        assertArrayEquals(beforeRecovery.canonicalState(), recovered.checkpoint().canonicalState());
        var confirmed = new PhysicalReplicaCustodyPayloads.ProjectionCustodyConfirmed(scope, 1L, 10L, actual.replicaRevision(),
                expected.fingerprint(), expected.provenance());
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, submit(recovered, "observed-restored-result", confirmed));
        var resolved = new FrontierWorldStateCodec().decode(recovered.checkpoint().canonicalState());
        assertEquals(PhysicalCustodyLeaseStatus.ACQUIRED, resolved.replicaCustody().custodyByScope().get(scope).status());
        assertEquals(1L, resolved.replicaCustody().custodyByScope().get(scope).authorityEpoch());
        assertEquals(PhysicalReplicaState.OBSERVED_CURRENT, resolved.replicaCustody().replicas().get(object).state());
        assertFalse(resolved.replicaCustody().diagnostics().containsKey(scope));
        assertEquals(state.inventory(), resolved.inventory(), "an observation cannot repair or reinterpret resource accounting");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, submit(recovered, "duplicate-recovery", confirmed));
    }

    @Test
    void registeredProjectionCommandsRetainPreparationAcrossEngineRecovery() {
        var world = new WorldId("frontier:projection-command");
        var configuration = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(configuration);
        SubjectId object = new SubjectId("container:projection-test"), scope = new SubjectId("scope:projection-test");
        var expected = PhysicalReplicaRecord.expected(object, "container.depot", 10L, "sha256:projection", "owned:projection");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, "declare", new PhysicalReplicaCustodyPayloads.ReplicaDeclared(expected)));
        var lease = new PhysicalCustodyLease(scope, object, new SubjectId("provider:projection-test"), 1L, 10L, 1L,
                PhysicalCustodyLeaseStatus.PREPARING, null);
        var prepared = new PhysicalReplicaCustodyPayloads.ProjectionCustodyPrepared(lease);
        assertTrue(prepared.requiresDurableBeforeEffect());
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, submit(engine, "prepare", prepared));
        var image = new io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage(world,
                java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord(engine.checkpoint(), 2L)), java.util.List.of());
        var recovered = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.recover(configuration, image);
        var before = recovered.checkpoint();
        assertEquals(lease, new FrontierWorldStateCodec().decode(before.canonicalState()).replicaCustody().custodyByScope().get(scope));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, submit(recovered, "wrong-epoch",
                new PhysicalReplicaCustodyPayloads.ProjectionCustodyConfirmed(scope, 2L, 10L, 1L, expected.fingerprint(), expected.provenance())));
        assertArrayEquals(before.canonicalState(), recovered.checkpoint().canonicalState());
        assertEquals(before.revision(), recovered.checkpoint().revision());
        var confirmed = new PhysicalReplicaCustodyPayloads.ProjectionCustodyConfirmed(scope, 1L, 10L, 1L, expected.fingerprint(), expected.provenance());
        assertTrue(confirmed.requiresDurableBeforeEffect());
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, submit(recovered, "confirm", confirmed));
        var after = new FrontierWorldStateCodec().decode(recovered.checkpoint().canonicalState());
        assertEquals(PhysicalCustodyLeaseStatus.ACQUIRED, after.replicaCustody().custodyByScope().get(scope).status());
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, submit(recovered, "repeat-confirm", confirmed));
    }

    private static io.farfrontier.palemirror.frontier.v3.api.CommandResult submit(
            io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine, String suffix,
            io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        var checkpoint = engine.checkpoint();
        var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:projection-" + suffix);
        return engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(),
                checkpoint.instant(), io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id), payload));
    }

    @Test
    void beforeWriteFenceSurvivesSnapshotAndOnlyExactObservedResultPromotesIt() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:projection-fence"), 91L));
        SubjectId depot = FrontierWorldState.depotId(state.bootstrap().settlements().getFirst().id());
        SubjectId scope = ReferenceContainerCustody.scopeId(depot);
        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(depot, ReferenceContainerCustody.semanticKind(state, depot),
                10L, ReferenceContainerCustody.canonicalFingerprint(state, depot), ReferenceContainerCustody.provenance(depot));
        PhysicalCustodyLease preparing = new PhysicalCustodyLease(scope, depot, ReferenceContainerCustody.PROVIDER_ID,
                1L, 10L, 1L, PhysicalCustodyLeaseStatus.PREPARING, null);
        PhysicalReplicaCustodyState held = PhysicalReplicaCustodyState.empty().declare(expected).prepareProjection(preparing);
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(held));
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(held, recovered.replicaCustody());
        assertTrue(ReferenceContainerCustody.hasLiveCustody(recovered, depot), "COLD cannot compete with the pending write");
        assertFalse(ReferenceContainerCustody.hasOperationalCustody(recovered, depot), "a prepared write is not observed HOT permission");
        assertThrows(IllegalArgumentException.class, () -> held.prepareProjection(preparing));
        assertThrows(IllegalArgumentException.class, () -> held.release(scope, 1L, 10L, 1L));
        assertThrows(IllegalArgumentException.class, () -> held.checkpoint(scope, 1L, 10L, 1L));
        assertThrows(IllegalArgumentException.class, () -> held.confirmProjection(scope, 2L, 10L, 1L, expected.fingerprint(), expected.provenance()));
        assertThrows(IllegalArgumentException.class, () -> held.confirmProjection(scope, 1L, 11L, 1L, expected.fingerprint(), expected.provenance()));
        assertThrows(IllegalArgumentException.class, () -> held.confirmProjection(scope, 1L, 10L, 2L, expected.fingerprint(), expected.provenance()));
        assertThrows(IllegalArgumentException.class, () -> held.confirmProjection(scope, 1L, 10L, 1L, "changed", expected.provenance()));
        assertThrows(IllegalArgumentException.class, () -> held.confirmProjection(scope, 1L, 10L, 1L, expected.fingerprint(), "foreign"));
        assertEquals(preparing, held.custodyByScope().get(scope), "invalid evidence must not release or rewrite the retained fence");
        PhysicalReplicaCustodyState confirmed = recovered.replicaCustody().confirmProjection(scope, 1L, 10L, 1L,
                expected.fingerprint(), expected.provenance());
        assertEquals(PhysicalCustodyLeaseStatus.ACQUIRED, confirmed.custodyByScope().get(scope).status());
        assertEquals(2L, confirmed.custodyByScope().get(scope).expectedReplicaRevision());
        assertEquals(1L, confirmed.custodyByScope().get(scope).authorityEpoch());
        assertThrows(IllegalArgumentException.class, () -> confirmed.confirmProjection(scope, 1L, 10L, 1L, expected.fingerprint(), expected.provenance()));
        PhysicalReplicaCustodyState released = confirmed.checkpoint(scope, 1L, 10L, 2L).release(scope, 1L, 10L, 2L);
        FrontierWorldState after = recovered.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(released));
        assertFalse(ReferenceContainerCustody.hasLiveCustody(after, depot));
        assertEquals(recovered.inventory(), after.inventory(), "custody bookkeeping owns no stock or economic transformation");
    }
}
