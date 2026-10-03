package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3OfflineActorRecoveryPlanTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    private static final SubjectId ACTOR = new SubjectId("resident:1-1");
    static FrontierWorldState prepared() {
        return prepared(new WorldId("frontier:offline-plan"));
    }
    static FrontierWorldState prepared(WorldId world) {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        state = AmbientLeaseStateProcess.prepare(state, AmbientActorProcess.nextLease(state, ACTOR, SimInstant.ZERO));
        state = AmbientLeaseStateProcess.transition(state, ACTOR, AmbientLeaseStatus.DRAINING);
        var actor = state.actorLocations().get(ACTOR);
        state = AmbientLeaseStateProcess.release(state, new AmbientLeaseReleased(ACTOR, actor.body(), actor.condition().health()));
        return AmbientLeaseStateProcess.prepare(state, AmbientActorProcess.nextLease(state, ACTOR, new SimInstant(10L)));
    }
    private static FrontierV3OfflineActorAbsence.Proof proof(FrontierWorldState state) {
        return new FrontierV3OfflineActorAbsence.Proof(Path.of("/test-only-offline-world"),
                SceneLease.deterministicEntityId(state.bootstrap().worldId(), ACTOR), 1,
                List.of(new FrontierV3OfflineActorAbsence.FileProof("entities/r.0.0.mca", "test-digest")));
    }
    @Test void cancelsOnlyPreparedGenerationAndEnablesNextOrdinaryAdmission() {
        var state = prepared(); var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var plan = FrontierV3OfflineActorRecoveryPlan.create(state, ACTOR, proof(state), ledger);
        assertEquals(2L, plan.cancelledRevision());
        assertEquals(AmbientLeaseStatus.CLOSED, plan.closed().ambientLeases().get(ACTOR).status());
        assertEquals(state, plan.closed().withChanges(FrontierWorldStateUpdate.begin().ambientLeases(state.ambientLeases())));
        assertEquals(2, plan.commands().size());
        var carrier = plan.recoveryCarrier();
        assertTrue(ledger.fence(carrier.identity(), carrier.physicalRevision(), carrier.ambientRevision()));
        var next = AmbientActorProcess.nextLease(plan.closed(), ACTOR, new SimInstant(20L));
        assertEquals(3L, next.revision());
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.READY,
                ledger.reconciliation(carrier.identity().liveBody(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                        next.revision(), ledger.reconstructionEpoch(ACTOR))));
    }
    @Test void hotAuthorityAndForeignAbsenceCannotBeRepaired() {
        var state = prepared(); var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var hot = AmbientLeaseStateProcess.transition(state, ACTOR, AmbientLeaseStatus.HOT);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3OfflineActorRecoveryPlan.create(hot, ACTOR, proof(state), ledger));
        var foreign = new FrontierV3OfflineActorAbsence.Proof(Path.of("/test-only"), UUID.randomUUID(), 1, List.of());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3OfflineActorRecoveryPlan.create(state, ACTOR, foreign, ledger));
    }

    @Test void unresolvedAdoptionIsNotLegacyMissingCustody() {
        var state = prepared(); var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var old = FrontierV3AmbientActorExecutor.carrierDeclaration(state, ACTOR,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3AmbientActorExecutor.entityId(state, ACTOR),
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 1L, 1L);
        assertTrue(ledger.fence(old, 1L, 1L));
        assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(old.liveBody(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, 2L, 2L))));
        assertThrows(IllegalArgumentException.class,
                () -> FrontierV3OfflineActorRecoveryPlan.create(state, ACTOR, proof(state), ledger));
        assertTrue(ledger.pendingAdoption(ACTOR).isPresent());
    }

    @Test void firstAdmissionHistoryIsNeverLegacyMissingCustody() {
        var state = prepared();
        var runtime = FrontierV3ServerRuntime.start(configuration(state), new FrontierFileStore(directory,
                io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs()), 1000);
        var empty = FrontierV3AmbientCarrierLedger.emptyForTest();
        var oldPlan = FrontierV3OfflineActorRecoveryPlan.create(state, ACTOR, proof(state), empty);
        var revision = runtime.checkpointImage().orElseThrow().revision();
        for (var phase : FrontierV3ActorFirstAdmission.Phase.values()) {
            var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
            var identity = new FrontierV3ActorFirstAdmission.Identity(ACTOR,
                    ActorKind.RESIDENT,
                    FrontierV3AmbientActorExecutor.entityId(state, ACTOR));
            assertTrue(ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(identity)));
            var target = FrontierV3ActorOwnerBinding.ambient(FrontierV3AmbientActorExecutor.carrierDeclaration(
                    state, ACTOR, FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, identity.entityId(),
                    FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 2L, 1L));
            if (phase != FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED) {
                assertTrue(ledger.beginFirstAdmission(target));
                if (phase == FrontierV3ActorFirstAdmission.Phase.ESTABLISHED)
                    assertTrue(ledger.acknowledgeFirstAdmission(ledger.firstAdmission(ACTOR).orElseThrow(), target));
                if (phase == FrontierV3ActorFirstAdmission.Phase.PROVEN_ABSENT)
                    assertTrue(ledger.rearmFirstAdmissionAfterAbsence(ledger.firstAdmission(ACTOR).orElseThrow(), "a".repeat(64)));
            }
            assertEquals(phase, ledger.firstAdmission(ACTOR).orElseThrow().phase());
            var before = ledger.save(new net.minecraft.nbt.CompoundTag(), null);
            assertThrows(IllegalArgumentException.class,
                    () -> FrontierV3OfflineActorRecoveryPlan.create(state, ACTOR, proof(state), ledger), phase.name());
            // A previously prepared plan must also reject newly discovered history before any write.
            assertThrows(IllegalStateException.class, () -> oldPlan.apply(runtime, ledger), phase.name());
            assertEquals(before, ledger.save(new net.minecraft.nbt.CompoundTag(), null));
            assertEquals(state, runtime.decodedState().orElseThrow());
            assertEquals(revision, runtime.checkpointImage().orElseThrow().revision());
        }
        runtime.shutdown();
    }

    @Test void registeredCommandsPersistCancellationAndRepeatWithoutAnotherMutation() {
        var state = prepared();
        var configuration = configuration(state);
        var store = new FrontierFileStore(directory, io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs());
        var runtime = FrontierV3ServerRuntime.start(configuration, store, 1000);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var plan = FrontierV3OfflineActorRecoveryPlan.create(state, ACTOR, proof(state), ledger);
        plan.apply(runtime, ledger);
        assertEquals(plan.closed(), runtime.decodedState().orElseThrow());
        var revision = runtime.checkpointImage().orElseThrow().revision();
        plan.apply(runtime, ledger);
        assertEquals(revision, runtime.checkpointImage().orElseThrow().revision());
        runtime.shutdown();
        var recovered = FrontierV3ServerRuntime.start(configuration, new FrontierFileStore(directory,
                io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs()), 1000);
        assertEquals(plan.closed(), recovered.decodedState().orElseThrow());
        assertEquals(new SimInstant(10L), recovered.checkpointImage().orElseThrow().instant());
        var restoredLedger = FrontierV3AmbientCarrierLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        plan.apply(recovered, restoredLedger);
        assertEquals(revision, recovered.checkpointImage().orElseThrow().revision());
        assertTrue(restoredLedger.hasCarrier(ACTOR));
        recovered.shutdown();
    }

    @Test void resumesInterruptedDrainingButRejectsForeignCarrierBeforeCanonicalWrite() {
        var state = prepared();
        var configuration = configuration(state);
        var runtime = FrontierV3ServerRuntime.start(configuration, new FrontierFileStore(directory,
                io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs()), 1000);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var plan = FrontierV3OfflineActorRecoveryPlan.create(state, ACTOR, proof(state), ledger);
        var carrier = plan.recoveryCarrier();
        assertTrue(ledger.fence(carrier.identity(), carrier.physicalRevision() + 1, carrier.ambientRevision()));
        var initialRevision = runtime.checkpointImage().orElseThrow().revision();
        assertThrows(IllegalStateException.class, () -> plan.apply(runtime, ledger));
        assertEquals(state, runtime.decodedState().orElseThrow());
        assertEquals(initialRevision, runtime.checkpointImage().orElseThrow().revision());
        FrontierV3CommandSubmission.submit(runtime, "test-interrupted-offline-draining", ACTOR.value(), plan.commands().getFirst());
        assertEquals(plan.draining(), runtime.decodedState().orElseThrow());
        runtime.shutdown();
        var recovered = FrontierV3ServerRuntime.start(configuration, new FrontierFileStore(directory,
                io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs()), 1000);
        var emptyRecoveredLedger = FrontierV3AmbientCarrierLedger.emptyForTest();
        plan.apply(recovered, emptyRecoveredLedger);
        assertEquals(plan.closed(), recovered.decodedState().orElseThrow());
        assertTrue(emptyRecoveredLedger.matchesCarrier(carrier.identity(), carrier.physicalRevision(), carrier.ambientRevision()));
        recovered.shutdown();
    }

    static io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> configuration(FrontierWorldState state) {
        var base = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 91L);
        return new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(), state,
                new SimInstant(10L), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(),
                io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateTransitionValidator.INSTANCE);
    }
}
