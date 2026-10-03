package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPayloads.*;
import static org.junit.jupiter.api.Assertions.*;

class FencedRecoveryStateTest {
    private static final SubjectId BODY = new SubjectId("body:recovery-a");
    private static final SubjectId CARGO = new SubjectId("cargo:recovery-a");
    private static final SubjectId CONTAINER = new SubjectId("container:recovery-a");
    private static final SubjectId EFFECT = new SubjectId("effect:recovery-a");
    private static final SubjectId OWNER = new SubjectId("operation:recovery-a");

    @Test void genericRecoveryCommandsCannotAdvanceOrRetireAnActorIncarnation() {
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:body-generic-recovery"), 91L);
        var actor = base.initialState().humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var state = ActorBodyAuthority.demand(base.initialState(), actor);
        var body = ActorBodyAuthority.current(state, actor);
        var binding = io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.recoveryBindingId(actor);
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                base.worldId(), state, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(),
                base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
        var engine = FrontierEngines.create(configuration);
        var before = engine.checkpoint();
        java.util.List<FrontierPayload> attempts = java.util.List.of(new Running(binding, body.physicalEpoch()),
                new Observed(binding, body.physicalEpoch()), new Confirmed(binding, body.physicalEpoch()),
                new RevokedToCold(binding, body.physicalEpoch()), new Abandoned(binding, body.physicalEpoch()),
                FencedRecoveryDiagnosticProducer.ambiguous(binding, body.physicalEpoch(), "wrong-protocol", FencedRecoveryDisposition.INSPECT));
        for (int index = 0; index < attempts.size(); index++) {
            var command = new CommandId("command:body-generic-recovery-" + index);
            var rejected = assertInstanceOf(CommandResult.Rejected.class, engine.submit(new FrontierCommand(
                    FrontierCommand.SCHEMA_VERSION, command, base.worldId(), before.revision(), before.instant(),
                    FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), attempts.get(index))));
            assertTrue(rejected.rejection().detail().contains("generic recovery cannot mutate actor-body authority"));
            assertEquals(before.revision(), engine.checkpoint().revision());
            assertArrayEquals(before.canonicalState(), engine.checkpoint().canonicalState());
        }
    }

    @Test
    void observedDeathCannotRetireCargoForeignOwnerRevisionOrEpoch() {
        var state = FencedRecoveryState.empty().prepare(binding(BODY, FencedRecoveryAsset.BODY, 1L, true))
                .prepare(binding(CARGO, FencedRecoveryAsset.CARGO, 1L, false));
        long revision = state.current().get(BODY).ownerRevision();
        assertThrows(IllegalArgumentException.class, () -> state.retireObservedBodyDeath(CARGO, 1L, OWNER, revision));
        assertThrows(IllegalArgumentException.class, () -> state.retireObservedBodyDeath(BODY, 2L, OWNER, revision));
        assertThrows(IllegalArgumentException.class, () -> state.retireObservedBodyDeath(BODY, 1L, CARGO, revision));
        assertThrows(IllegalArgumentException.class, () -> state.retireObservedBodyDeath(BODY, 1L, OWNER, revision + 1));
        var retired = state.retireObservedBodyDeath(BODY, 1L, OWNER, revision);
        assertEquals(state.current().get(CARGO), retired.current().get(CARGO));
        assertFalse(retired.current().containsKey(BODY));
    }

    @Test
    void noVisitRevocationFencesLateBodyWhileUnrelatedCargoRemainsCurrent() {
        FencedRecoveryState state = FencedRecoveryState.empty().prepare(binding(BODY, FencedRecoveryAsset.BODY, 1L, true))
                .running(BODY, 1L).revokeToCold(BODY, 1L)
                .prepare(binding(CARGO, FencedRecoveryAsset.CARGO, 1L, false)).running(CARGO, 1L);

        assertEquals(FencedRecoveryDisposition.REJECT_STALE, state.lateLoad(BODY, FencedRecoveryAsset.BODY, OWNER, 1L));
        assertEquals(FencedRecoveryDisposition.RECLAIM, state.lateLoad(CARGO, FencedRecoveryAsset.CARGO, OWNER, 1L));
        assertEquals(1L, state.tombstones().get(BODY).retiredEpoch());
        assertEquals(FencedRecoveryDisposition.RESUME_COLD, state.tombstones().get(BODY).disposition());
        assertThrows(IllegalArgumentException.class, () -> state.prepare(binding(BODY, FencedRecoveryAsset.BODY, 1L, true)));
        assertEquals(2L, state.prepare(binding(BODY, FencedRecoveryAsset.BODY, 2L, true)).current().get(BODY).authorityEpoch());
    }

    @Test
    void observedExternalCustodyNeverRollsBackAndAmbiguityStaysLocalUntilBoundedAbandonment() {
        FencedRecoveryState state = FencedRecoveryState.empty().prepare(binding(CONTAINER, FencedRecoveryAsset.CONTAINER, 1L, false))
                .running(CONTAINER, 1L).observed(CONTAINER, 1L);
        assertThrows(IllegalArgumentException.class, () -> state.revokeToCold(CONTAINER, 1L), "an external/container observation is not a reversible pose checkpoint");

        FencedRecoveryState ambiguous = state.ambiguous(CONTAINER, 1L, "player-custody-uninspectable", FencedRecoveryDisposition.INSPECT)
                .ambiguous(CONTAINER, 1L, "player-custody-uninspectable", FencedRecoveryDisposition.RETRY)
                .ambiguous(CONTAINER, 1L, "player-custody-uninspectable", FencedRecoveryDisposition.REPAIR);
        assertEquals(FencedRecoveryPhase.AMBIGUOUS, ambiguous.current().get(CONTAINER).phase());
        assertEquals(FencedRecoveryDisposition.ABANDON, ambiguous.current().get(CONTAINER).nextAction());
        FencedRecoveryState abandoned = ambiguous.abandon(CONTAINER, 1L);
        assertTrue(abandoned.current().isEmpty());
        assertEquals(FencedRecoveryDisposition.ABANDON, abandoned.tombstones().get(CONTAINER).disposition());
    }

    @Test
    void exactInspectionMayResolveExhaustedAttemptsUntilAbandonmentCommits() {
        var state = FencedRecoveryState.empty()
                .prepare(binding(EFFECT, FencedRecoveryAsset.EFFECT, 1L, false)).running(EFFECT, 1L);
        for (int attempt = 0; attempt < FencedRecoveryBinding.MAX_RECOVERY_ATTEMPTS; attempt++) {
            state = state.ambiguous(EFFECT, 1L, "missing-witness", FencedRecoveryDisposition.INSPECT);
        }
        var exhausted = state;
        assertEquals(FencedRecoveryDisposition.ABANDON, exhausted.current().get(EFFECT).nextAction());
        var confirmed = exhausted.inspectedObserved(EFFECT, 1L).confirm(EFFECT, 1L);
        assertEquals(FencedRecoveryDisposition.REJECT_STALE, confirmed.tombstones().get(EFFECT).disposition());
        var abandoned = exhausted.abandon(EFFECT, 1L);
        assertThrows(IllegalArgumentException.class, () -> abandoned.inspectedObserved(EFFECT, 1L));
        assertThrows(IllegalArgumentException.class, () -> exhausted.inspectedObserved(EFFECT, 2L));
    }

    @Test
    void conflictResolutionCanOnlyFenceTheExactAmbiguousAuthorityBeforePreparingItsSuccessor() {
        FencedRecoveryState ambiguous = FencedRecoveryState.empty().prepare(binding(BODY, FencedRecoveryAsset.BODY, 1L, true))
                .running(BODY, 1L).ambiguous(BODY, 1L, "local-obstruction", FencedRecoveryDisposition.INSPECT);
        FencedRecoveryState successor = ambiguous.supersedeAmbiguous(binding(BODY, FencedRecoveryAsset.BODY, 2L, true), "conflict-resolved");

        assertEquals(2L, successor.current().get(BODY).authorityEpoch());
        assertEquals(FencedRecoveryDisposition.REJECT_STALE, successor.lateLoad(BODY, FencedRecoveryAsset.BODY, OWNER, 1L));
        assertThrows(IllegalArgumentException.class, () -> FencedRecoveryState.empty().supersedeAmbiguous(binding(BODY, FencedRecoveryAsset.BODY, 1L, true), "forged"));
    }

    @Test
    void allFourFamiliesRoundTripSnapshotAndWalReducerRejectsStaleEpochsBeforeMutation() {
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:fenced-recovery"), 91L));
        var actor = baseline.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        baseline = ActorBodyAuthority.demand(baseline, actor);
        FencedRecoveryState recovery = baseline.fencedRecovery()
                .prepare(binding(CARGO, FencedRecoveryAsset.CARGO, 1L, false))
                .prepare(binding(CONTAINER, FencedRecoveryAsset.CONTAINER, 1L, false))
                .prepare(binding(EFFECT, FencedRecoveryAsset.EFFECT, 1L, false));
        FrontierWorldState changed = baseline.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(recovery));
        FrontierWorldStateCodec codec = new FrontierWorldStateCodec();
        byte[] encoded = codec.encode(changed);
        assertArrayEquals(encoded, codec.encode(codec.decode(encoded)));
        assertEquals(recovery, codec.decode(encoded).fencedRecovery());

        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:fenced-recovery-wal"), 91L));
        submit(engine, new Prepared(binding(EFFECT, FencedRecoveryAsset.EFFECT, 1L, false)));
        submit(engine, new Running(EFFECT, 1L));
        assertInstanceOf(CommandResult.Rejected.class, submit(engine, new Observed(EFFECT, 2L)));
        assertEquals(FencedRecoveryPhase.RUNNING, new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState()).fencedRecovery().current().get(EFFECT).phase());
        submit(engine, new Observed(EFFECT, 1L));
        submit(engine, new Confirmed(EFFECT, 1L));
        assertEquals(FencedRecoveryDisposition.REJECT_STALE, new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState()).fencedRecovery().tombstones().get(EFFECT).disposition());
    }

    @Test
    void ambiguityRequiresReplicaCustodyAuthorityAndRetainsItAcrossWalAndSnapshotRecovery() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:fenced-recovery-diagnostic"), 91L));
        submit(engine, new Prepared(binding(EFFECT, FencedRecoveryAsset.EFFECT, 1L, false)));
        submit(engine, new Running(EFFECT, 1L));

        DiagnosticTuple foreign = new DiagnosticTuple(DiagnosticReason.FENCED_RECOVERY_AMBIGUOUS,
                DiagnosticCategory.RECOVERY_UNKNOWN,
                new DiagnosticOwner(DiagnosticOwnerKind.REPLICA_CUSTODY, BODY),
                new DiagnosticSubject(DiagnosticSubjectKind.PHYSICAL_EFFECT, BODY), DiagnosticDisposition.INSPECT);
        assertThrows(IllegalArgumentException.class, () -> new Ambiguous(EFFECT, 1L, "uninspectable-physical-effect",
                FencedRecoveryDisposition.INSPECT, foreign), "the reducer must never accept a tuple for another recovery binding");

        Ambiguous admitted = FencedRecoveryDiagnosticProducer.ambiguous(EFFECT, 1L, "uninspectable-physical-effect", FencedRecoveryDisposition.INSPECT);
        assertEquals(admitted, FrontierWorldRuntimeDefinition.payloadCodecs().decode(admitted.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(admitted)),
                "the installed WAL registry must retain the producer-stamped ambiguity tuple");
        submit(engine, admitted);
        FrontierWorldState changed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        DiagnosticIncident incident = changed.diagnosticIncidents().why(admitted.diagnostic().subject()).orElseThrow();
        assertEquals(admitted.diagnostic(), incident.diagnostic());
        assertEquals(FencedRecoveryPhase.AMBIGUOUS, changed.fencedRecovery().current().get(EFFECT).phase());

        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(changed));
        assertEquals(admitted.diagnostic(), recovered.diagnosticIncidents().why(admitted.diagnostic().subject()).orElseThrow().diagnostic());
        assertEquals(FencedRecoveryPhase.AMBIGUOUS, recovered.fencedRecovery().current().get(EFFECT).phase());
    }

    @Test
    void boundedTombstoneRetentionCompactsOnlyForANewDurableAuthorityAndStillRejectsUnknownLateProjection() {
        java.util.Map<SubjectId, FencedRecoveryTombstone> retained = new java.util.LinkedHashMap<>();
        for (int index = 0; index < FencedRecoveryState.MAX_BINDINGS; index++) {
            SubjectId id = new SubjectId("body:retention-" + String.format("%04d", index));
            retained.put(id, new FencedRecoveryTombstone(id, FencedRecoveryAsset.BODY, OWNER, 7L, 1L,
                    FencedRecoveryDisposition.REJECT_STALE, "confirmed"));
        }
        FencedRecoveryState full = new FencedRecoveryState(Map.of(), retained);
        SubjectId successor = new SubjectId("body:retention-successor");
        FencedRecoveryState next = full.prepare(binding(successor, FencedRecoveryAsset.BODY, 1L, true));

        assertEquals(FencedRecoveryState.MAX_BINDINGS, next.current().size() + next.tombstones().size());
        assertTrue(next.current().containsKey(successor));
        assertFalse(next.tombstones().containsKey(new SubjectId("body:retention-0000")),
                "compaction is deterministic rather than depending on map iteration or a later visitor");
        assertEquals(FencedRecoveryDisposition.REJECT_STALE,
                next.lateLoad(new SubjectId("body:retention-0000"), FencedRecoveryAsset.BODY, OWNER, 1L),
                "compaction never converts a forgotten projection into authority");
    }

    private static FencedRecoveryBinding binding(SubjectId id, FencedRecoveryAsset asset, long epoch, boolean reversible) {
        return FencedRecoveryBinding.prepared(id, asset, OWNER, 7L, epoch, reversible);
    }
    private static CommandResult submit(FrontierEngine<FrontierWorldProjection> engine, FrontierPayload payload) {
        var canonical = engine.checkpoint(); CommandId id = new CommandId("command:fenced-recovery-" + canonical.revision().value());
        return engine.submit(new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, canonical.worldId(), canonical.revision(), canonical.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload));
    }
}
