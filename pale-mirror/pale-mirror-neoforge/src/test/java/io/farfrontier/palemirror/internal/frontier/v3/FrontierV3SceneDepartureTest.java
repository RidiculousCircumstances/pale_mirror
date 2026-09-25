package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3SceneDepartureTest {
    private static final SubjectId ACTOR = new SubjectId("resident:departure-test");

    private static FrontierV3SceneDeparture receipt(long epoch, long health) {
        var declaration = new FrontierV3ActorCarrierComposition.Declaration(ACTOR,
                FrontierV3ActorCarrierComposition.ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE,
                UUID.fromString("ef562345-8f47-37ec-af28-d12c259ab948"),
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 7, epoch);
        return new FrontierV3SceneDeparture(new FrontierV3AmbientCarrierLedger.Carrier(declaration, 7, 3),
                new SceneLeaseId("lease:departure-test"), 7,
                new SceneMemberPosition(ACTOR, new BodyPosition(12, 65, 10), FixedScalar.whole(health)), FixedScalar.whole(20));
    }

    @Test
    void finalHealthIdentityAndEpochSurviveSerializationWithoutGrantingCustody() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var receipt = receipt(4, 9);
        assertTrue(ledger.recordDeparture(receipt));
        assertFalse(ledger.hasCarrier(ACTOR), "evidence is not an authority transfer");
        assertFalse(ledger.savedDeparture(receipt), "unload alone does not prove an entity-region save");
        var recovered = FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null);
        assertEquals(receipt, recovered.departure(ACTOR).orElseThrow());
        assertFalse(recovered.savedDeparture(receipt));
        assertFalse(recovered.hasCarrier(ACTOR));
        assertFalse(recovered.isDirty(), "reading current evidence must not manufacture a mutation");
    }

    @Test
    void exactStorageConfirmationIsDurableAndOldFormatDoesNotInventIt() {
        var receipt = receipt(4, 9);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.recordDeparture(receipt));
        assertTrue(ledger.confirmSavedDeparture(receipt));
        var saved = ledger.save(new CompoundTag(), null);
        assertTrue(FrontierV3AmbientCarrierLedger.load(saved, null).savedDeparture(receipt));
        saved.putInt("format", 5);
        saved.remove("savedDepartures");
        assertFalse(FrontierV3AmbientCarrierLedger.load(saved, null).savedDeparture(receipt));
        saved.putInt("format", 6);
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(saved, null));
        ledger.forgetDeparture(ACTOR);
        assertFalse(ledger.savedDeparture(receipt));
    }

    @Test
    void changedSampleOrEpochCannotOverwriteAReceiptWithoutAnObservedReturn() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.recordDeparture(receipt(4, 9)));
        assertTrue(ledger.recordDeparture(receipt(4, 9)), "exact repeat is idempotent");
        assertFalse(ledger.recordDeparture(receipt(4, 20)));
        assertFalse(ledger.recordDeparture(receipt(5, 20)));
        assertEquals(FixedScalar.whole(9), ledger.departure(ACTOR).orElseThrow().observed().health());
        ledger.forgetDeparture(ACTOR);
        assertTrue(ledger.departure(ACTOR).isEmpty());
        assertTrue(ledger.recordDeparture(receipt(5, 18)));
    }

    @Test
    void missingHealthOrLeaseGenerationAndDuplicateRecoveryFailClosed() {
        CompoundTag missing = receipt(4, 9).save();
        missing.remove("health");
        assertThrows(IllegalStateException.class, () -> FrontierV3SceneDeparture.load(missing));
        CompoundTag missingRevision = receipt(4, 9).save();
        missingRevision.remove("sceneRevision");
        assertThrows(IllegalStateException.class, () -> FrontierV3SceneDeparture.load(missingRevision));
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        ledger.recordDeparture(receipt(4, 9));
        CompoundTag saved = ledger.save(new CompoundTag(), null);
        var rows = saved.getList("departures", Tag.TAG_COMPOUND);
        rows.add(rows.getCompound(0).copy());
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(saved, null));
        saved.putInt("format", 3);
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(saved, null),
                "old carrier evidence must not be silently promoted to final departure evidence");
    }

    @Test
    void contradictoryDepartureSurvivesRecoveryAndCannotConsumeTheFirstReceipt() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var first = receipt(4, 9);
        assertTrue(ledger.recordDeparture(first));
        assertTrue(ledger.fence(first.carrier().identity(), 7, 3));
        assertFalse(ledger.recordDeparture(receipt(4, 8)));
        assertFalse(ledger.recordDeparture(receipt(5, 7)));
        var saved = ledger.save(new CompoundTag(), null);
        assertEquals(1, saved.getList("departureConflicts", Tag.TAG_COMPOUND).size());
        var recovered = FrontierV3AmbientCarrierLedger.load(saved, null);
        assertTrue(recovered.hasDepartureConflict(ACTOR));
        assertFalse(recovered.recordDeparture(first));
        assertFalse(recovered.resumeDeparture(first));
        assertFalse(recovered.fence(first.carrier().identity(), 7, 3));
        var live = new FrontierV3ActorCarrierComposition.Declaration(ACTOR, first.carrier().identity().kind(),
                first.carrier().identity().owner(), first.carrier().identity().entityId(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 8, 5);
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.DEPARTURE_CONFLICT, recovered.reconciliation(live));
        assertFalse(recovered.adopt(FrontierV3ActorAdoptionFixture.binding(live)));
        assertEquals(first, recovered.departure(ACTOR).orElseThrow());
        recovered.forgetDeparture(ACTOR);
        assertFalse(recovered.hasDepartureConflict(ACTOR));
    }

    @Test
    void malformedOrOrphanConflictInventoryCannotDisappearDuringLoad() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var saved = ledger.save(new CompoundTag(), null);
        saved.getList("departureConflicts", Tag.TAG_COMPOUND).add(receipt(4, 8).save());
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(saved, null));
        var malformed = ledger.save(new CompoundTag(), null);
        var strings = new net.minecraft.nbt.ListTag();
        strings.add(net.minecraft.nbt.StringTag.valueOf("not evidence"));
        malformed.put("departures", strings);
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(malformed, null));
    }

    @Test
    void foreignObservedActorOrLeaseRevisionIsRejected() {
        var valid = receipt(4, 9);
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3SceneDeparture(valid.carrier(), valid.leaseId(), 8,
                valid.observed(), valid.canonicalHealthAtCapture()));
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3SceneDeparture(valid.carrier(), valid.leaseId(), 7,
                new SceneMemberPosition(new SubjectId("resident:foreign"), valid.observed().body(), valid.observed().health()),
                valid.canonicalHealthAtCapture()));
    }
}
