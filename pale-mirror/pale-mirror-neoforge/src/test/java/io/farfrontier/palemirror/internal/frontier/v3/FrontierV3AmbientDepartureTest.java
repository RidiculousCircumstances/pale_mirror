package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3AmbientDepartureTest {
    private static final SubjectId ACTOR = new SubjectId("resident:1-1");

    private static FrontierWorldState hot() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:ambient-departure"), 91L));
        state = AmbientLeaseStateProcess.prepare(state, AmbientActorProcess.nextLease(state, ACTOR, SimInstant.ZERO));
        return AmbientLeaseStateProcess.transition(state, ACTOR, AmbientLeaseStatus.HOT);
    }

    private static FrontierV3AmbientDeparture receipt(FrontierWorldState state) {
        var location = state.actorLocations().get(ACTOR);
        long revision = state.ambientLeases().get(ACTOR).revision();
        var declaration = FrontierV3ActorCarrierComposition.fromCanonical(state, ACTOR,
                FrontierV3ActorCarrierComposition.ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
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
