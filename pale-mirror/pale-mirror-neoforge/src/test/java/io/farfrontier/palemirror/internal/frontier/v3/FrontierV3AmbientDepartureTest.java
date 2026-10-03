package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3AmbientDepartureTest {
    private static final SubjectId ACTOR = new SubjectId("resident:1-1");

    @Test void semanticGoalDepartureReleasesFromActualBodyNotOldAdmissionAnchor() {
        var state = hot();
        var lease = state.ambientLeases().get(ACTOR);
        var start = state.actorLocations().get(ACTOR).body();
        var moved = new BodyPosition(start.x() + 1, start.y(), start.z());
        state = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.retarget(
                state, ACTOR, AmbientGoalKind.WORK, moved);
        var original = receipt(state);
        var departure = new FrontierV3AmbientDeparture(original.carrier(),
                new SceneMemberPosition(ACTOR, moved, original.observed().health()),
                original.canonicalBodyAtCapture(), original.canonicalHealthAtCapture());
        assertTrue(departure.current(state));
        assertNotEquals(lease.handoffBody(), moved);
        assertTrue(FrontierV3AmbientActorExecutor.unloadedReleaseEligible(state,
                new AmbientLeaseReleased(ACTOR, moved, departure.observed().health())));
        var released = AmbientLeaseStateProcess.release(AmbientLeaseStateProcess.transition(
                state, ACTOR, AmbientLeaseStatus.DRAINING),
                new AmbientLeaseReleased(ACTOR, moved, departure.observed().health()));
        assertEquals(moved, released.actorLocations().get(ACTOR).body());
        assertFalse(departure.current(released));
        // The persisted unload witness survives release until exact successor
        // adoption, then retires without leaving a stale admission blocker.
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.recordAmbientDeparture(departure));
        var carrier = departure.carrier();
        assertTrue(ledger.fence(carrier.identity(), carrier.physicalRevision(), carrier.ambientRevision()));
        ledger = FrontierV3AmbientCarrierLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        var successor = carrier.identity().liveBody(carrier.identity().owner(),
                carrier.ambientRevision() + 1L, ledger.reconstructionEpoch(ACTOR));
        assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(successor)));
        assertTrue(ledger.ambientDeparture(ACTOR).isEmpty());
        assertFalse(ledger.hasCarrier(ACTOR));
        assertTrue(ledger.pendingAdoption(ACTOR).isPresent());
    }

    private static FrontierWorldState hot() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:ambient-departure"), 91L));
        state = AmbientLeaseStateProcess.prepare(state, AmbientActorProcess.nextLease(state, ACTOR, SimInstant.ZERO));
        return AmbientLeaseStateProcess.transition(state, ACTOR, AmbientLeaseStatus.HOT);
    }

    private static FrontierV3AmbientDeparture receipt(FrontierWorldState state) {
        var location = state.actorLocations().get(ACTOR);
        long revision = state.ambientLeases().get(ACTOR).revision();
        var declaration = FrontierV3ActorCarrierComposition.fromCanonical(state, ACTOR,
                ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                SceneLease.deterministicEntityId(state.bootstrap().worldId(), ACTOR),
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, revision, 1L);
        return new FrontierV3AmbientDeparture(new FrontierV3AmbientCarrierLedger.Carrier(declaration, revision, revision),
                new SceneMemberPosition(ACTOR, location.body(), FixedScalar.whole(7L)), location.body(), location.condition().health());
    }

    @Test void finalDamageSurvivesEncodingAndRequiresUnchangedCanonicalBaseline() {
        var state = hot(); var receipt = receipt(state);
        assertTrue(receipt.current(state));
        assertEquals(receipt, FrontierV3AmbientDeparture.load(receipt.save()));
        assertEquals(FixedScalar.whole(7L), receipt.observed().health());
        var body = state.actorLocations().get(ACTOR).body();
        assertFalse(receipt.current(state.withActorBody(ACTOR, new BodyPosition(body.x() + 1, body.y(), body.z()))));
        assertTrue(receipt.current(AmbientLeaseStateProcess.transition(state, ACTOR, AmbientLeaseStatus.UNKNOWN_AFTER_RESTART)));
    }

    @Test void closedAndNewAuthorityCannotConsumeAnOlderDeparture() {
        var state = hot(); var receipt = receipt(state);
        state = AmbientLeaseStateProcess.transition(state, ACTOR, AmbientLeaseStatus.DRAINING);
        assertTrue(receipt.current(state));
        state = AmbientLeaseStateProcess.release(state, new AmbientLeaseReleased(ACTOR, receipt.observed().body(), receipt.observed().health()));
        assertFalse(receipt.current(state));
        state = AmbientLeaseStateProcess.prepare(state, AmbientActorProcess.nextLease(state, ACTOR, new SimInstant(20L)));
        state = AmbientLeaseStateProcess.transition(state, ACTOR, AmbientLeaseStatus.HOT);
        assertFalse(receipt.current(state));
    }

    @Test void missingPositionOrHealthIsNotZeroFilledRecoveryEvidence() {
        var saved = receipt(hot()).save(); saved.remove("observedY");
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientDeparture.load(saved));
        var missingHealth = receipt(hot()).save(); missingHealth.remove("canonicalHealth");
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientDeparture.load(missingHealth));
    }

    @Test void persistedEvidenceAndConflictDoNotGrantCustodyOrOverwriteTheFirstObservation() {
        var receipt = receipt(hot()); var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.recordAmbientDeparture(receipt));
        assertFalse(ledger.hasCarrier(ACTOR));
        var changed = new FrontierV3AmbientDeparture(receipt.carrier(),
                new SceneMemberPosition(ACTOR, receipt.observed().body(), FixedScalar.whole(8L)),
                receipt.canonicalBodyAtCapture(), receipt.canonicalHealthAtCapture());
        assertFalse(ledger.recordAmbientDeparture(changed));
        var recovered = FrontierV3AmbientCarrierLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertEquals(receipt, recovered.ambientDeparture(ACTOR).orElseThrow());
        assertTrue(recovered.hasDepartureConflict(ACTOR));
        assertFalse(recovered.resumeAmbientDeparture(receipt));
        assertFalse(recovered.hasCarrier(ACTOR));
    }

    @Test void duplicateSavedWitnessIsRejectedAndLegacyAbsenceRemainsAbsence() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); ledger.recordAmbientDeparture(receipt(hot()));
        var saved = ledger.save(new net.minecraft.nbt.CompoundTag(), null);
        var rows = saved.getList("ambientDepartures", net.minecraft.nbt.Tag.TAG_COMPOUND);
        rows.add(rows.getCompound(0).copy());
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(saved, null));
        saved.remove("ambientDepartures"); saved.remove("ambientDepartureConflicts");
        assertTrue(FrontierV3AmbientCarrierLedger.load(saved, null).ambientDeparture(ACTOR).isEmpty());
    }
}
