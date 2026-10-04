package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3AmbientCarrierLedgerTest {
    private static final SubjectId ACTOR = new SubjectId("resident:7-31");
    private static final UUID UUID_A = UUID.fromString("ef562345-8f47-37ec-af28-d12c259ab948");
    private static FrontierV3ActorCarrierComposition.Declaration declaration(FrontierV3ActorCarrierComposition.Owner owner,
                                                                               FrontierV3ActorCarrierComposition.Representation representation, long epoch) {
        return new FrontierV3ActorCarrierComposition.Declaration(ACTOR, ActorKind.RESIDENT, owner, UUID_A, representation, 0L, epoch);
    }
    @Test void fencedCarrierPermitsOnlyOneNewerSameUuidAdoption() {
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var inactive = declaration(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 3L);
        var live = declaration(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 4L);
        assertTrue(ledger.canFence(inactive, 7L, 7L)); assertTrue(ledger.fence(inactive, 7L, 7L));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.READY, ledger.reconciliation(live)); assertEquals(4L, ledger.reconstructionEpoch(ACTOR));
        assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(live))); assertEquals(0, ledger.inactiveCount());
        assertTrue(ledger.pendingAdoption(ACTOR).orElseThrow().matches(FrontierV3ActorAdoptionFixture.binding(live)));
        assertFalse(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(live)));
    }
    @Test void adoptionSurvivesSerializationAndOnlyExactAcknowledgementRetiresIt() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var inactive = declaration(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 3L);
        var live = inactive.liveBody(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, 0L, 4L);
        assertTrue(ledger.fence(inactive, 7L, 7L)); assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(live)));
        var restored = FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null);
        var pending = restored.pendingAdoption(ACTOR).orElseThrow();
        assertEquals(inactive, pending.predecessor().identity());
        assertFalse(restored.hasCarrier(ACTOR), "a recovery obligation is not concurrent inactive custody");
        assertFalse(restored.acknowledgeAdoption(pending, FrontierV3ActorAdoptionFixture.binding(live.liveBody(live.owner(), 0L, 3L))));
        assertFalse(restored.acknowledgeAdoption(pending, FrontierV3ActorAdoptionFixture.binding(live.liveBody(live.owner(), 0L, 5L))));
        assertEquals(pending, restored.pendingAdoption(ACTOR).orElseThrow());
        assertTrue(restored.acknowledgeAdoption(pending, FrontierV3ActorAdoptionFixture.binding(live)));
        assertFalse(restored.acknowledgeAdoption(pending, FrontierV3ActorAdoptionFixture.binding(live)));
        assertTrue(restored.pendingAdoptions().isEmpty());
    }
    @Test void sameBodySuccessorFenceSupersedesAdoptionButLateSaveCannotEraseIt() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var inactive = declaration(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 3L);
        var live = inactive.liveBody(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, 0L, 4L);
        assertTrue(ledger.fence(inactive, 7L, 7L)); assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(live)));
        var pending = ledger.pendingAdoption(ACTOR).orElseThrow();
        var successor = live.liveBody(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, 0L, 4L).inactiveCarrier();
        var staleBody = live.liveBody(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, 0L, 3L).inactiveCarrier();
        assertFalse(ledger.canFence(staleBody, 17L, 8L));
        assertFalse(ledger.fence(staleBody, 17L, 8L));
        assertTrue(ledger.fence(successor, 17L, 8L));
        assertTrue(ledger.pendingAdoptions().isEmpty());
        assertFalse(ledger.acknowledgeAdoption(pending, FrontierV3ActorAdoptionFixture.binding(live)));
        assertTrue(ledger.matchesCarrier(successor, 17L, 8L));
    }
    @Test void persistedDuplicateAndConcurrentAdoptionAreRejected() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var inactive = declaration(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 3L);
        var live = inactive.liveBody(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, 0L, 4L);
        assertTrue(ledger.fence(inactive, 7L, 7L)); assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(live)));
        var duplicate = ledger.save(new CompoundTag(), null);
        var rows = duplicate.getList("pendingAdoptions", net.minecraft.nbt.Tag.TAG_COMPOUND);
        rows.add(rows.getCompound(0).copy());
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(duplicate, null));
        var concurrent = ledger.save(new CompoundTag(), null);
        concurrent.getList("carriers", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .add(new FrontierV3AmbientCarrierLedger.Carrier(inactive, 7L, 7L).save());
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(concurrent, null));
    }
    @Test void missingForeignStaleKindAndRepresentationAllFailClosed() {
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var inactive = declaration(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 1L);
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER, ledger.reconciliation(declaration(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 2L)));
        assertTrue(ledger.fence(inactive, 7L, 0L));
        var foreign = new FrontierV3ActorCarrierComposition.Declaration(ACTOR, ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, UUID.randomUUID(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, 2L);
        var wrongKind = new FrontierV3ActorCarrierComposition.Declaration(ACTOR, ActorKind.BIOFORM,
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, UUID_A, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, 2L);
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.UUID_MISMATCH, ledger.reconciliation(foreign));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.KIND_MISMATCH, ledger.reconciliation(wrongKind));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.REPRESENTATION_MISMATCH, ledger.reconciliation(inactive));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.STALE_REVISION, ledger.reconciliation(declaration(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 1L)));
        assertFalse(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(foreign))); assertEquals(1, ledger.inactiveCount());
    }
    @Test void carrierCannotBeReplacedDuplicatedOrConcurrentWithALiveBody() {
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var fenced = declaration(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 3L);
        var forgedOwner = new FrontierV3ActorCarrierComposition.Declaration(ACTOR, ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, new UUID(17L, 8L),
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 0L, 3L);
        var live = declaration(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 4L);
        assertTrue(ledger.fence(fenced, 7L, 7L));
        assertFalse(ledger.canFence(forgedOwner, 7L, 7L));
        assertFalse(ledger.matchesCarrier(forgedOwner, 7L, 7L));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.CONCURRENT_CUSTODY,
                ledger.reconciliation(live, true));
        assertEquals(1, ledger.inactiveCount());
    }
    @Test void reconstructionNeedsRetainedIncarnationAndCannotManufactureItFromAbsence() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var inactive = declaration(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 5L);
        var live = declaration(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 6L);
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER, ledger.reconciliation(live));
        assertEquals(0, ledger.inactiveCount(), "absence must not manufacture evidence");
        assertTrue(ledger.fence(inactive, 17L, 8L));
        var before = ledger.save(new CompoundTag(), null);
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.READY, ledger.reconciliation(live));
        for (long epoch : java.util.List.of(2L, 5L)) {
            assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.STALE_REVISION,
                    ledger.reconciliation(live.liveBody(live.owner(), 0L, epoch)));
        }
        assertEquals(before, ledger.save(new CompoundTag(), null), "return inspection must be read-only");
    }
    @Test void legacySchemaAndDuplicatePersistedCarrierFailBeforeRecovery() {
        CompoundTag legacy = new CompoundTag(); legacy.putInt("format", 2);
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(legacy, null));

        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var fenced = declaration(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 3L);
        assertTrue(ledger.fence(fenced, 7L, 7L));
        CompoundTag persisted = ledger.save(new CompoundTag(), null);
        ListTag duplicate = persisted.getList("carriers", net.minecraft.nbt.Tag.TAG_COMPOUND);
        duplicate.add(duplicate.getCompound(0).copy());
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(persisted, null));
    }
}
