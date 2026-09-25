package io.farfrontier.palemirror.internal.frontier.v3;

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
                                                                               FrontierV3ActorCarrierComposition.Representation representation, long revision, long epoch) {
        return new FrontierV3ActorCarrierComposition.Declaration(ACTOR, FrontierV3ActorCarrierComposition.ActorKind.RESIDENT, owner, UUID_A, representation, revision, epoch);
    }
    @Test void fencedCarrierPermitsOnlyOneNewerSameUuidAdoption() {
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var inactive = declaration(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 7L, 3L);
        var live = declaration(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 8L, 4L);
        assertTrue(ledger.canFence(inactive, 7L, 7L)); assertTrue(ledger.fence(inactive, 7L, 7L));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.READY, ledger.reconciliation(live)); assertEquals(4L, ledger.reconstructionEpoch(ACTOR));
        assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(live))); assertEquals(0, ledger.inactiveCount());
        assertTrue(ledger.pendingAdoption(ACTOR).orElseThrow().matches(FrontierV3ActorAdoptionFixture.binding(live)));
        assertFalse(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(live)));
    }
    @Test void adoptionSurvivesSerializationAndOnlyExactAcknowledgementRetiresIt() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var inactive = declaration(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 7L, 3L);
        var live = inactive.liveBody(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, 8L, 4L);
        assertTrue(ledger.fence(inactive, 7L, 7L)); assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(live)));
        var restored = FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null);
        var pending = restored.pendingAdoption(ACTOR).orElseThrow();
        assertEquals(inactive, pending.predecessor().identity());
        assertFalse(restored.hasCarrier(ACTOR), "a recovery obligation is not concurrent inactive custody");
        assertFalse(restored.acknowledgeAdoption(pending, FrontierV3ActorAdoptionFixture.binding(live.liveBody(live.owner(), 9L, 4L))));
        assertFalse(restored.acknowledgeAdoption(pending, FrontierV3ActorAdoptionFixture.binding(live.liveBody(live.owner(), 8L, 5L))));
        assertEquals(pending, restored.pendingAdoption(ACTOR).orElseThrow());
        assertTrue(restored.acknowledgeAdoption(pending, FrontierV3ActorAdoptionFixture.binding(live)));
        assertFalse(restored.acknowledgeAdoption(pending, FrontierV3ActorAdoptionFixture.binding(live)));
        assertTrue(restored.pendingAdoptions().isEmpty());
    }
    @Test void sameBodySuccessorFenceSupersedesAdoptionButLateSaveCannotEraseIt() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var inactive = declaration(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 7L, 3L);
        var live = inactive.liveBody(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, 8L, 4L);
        assertTrue(ledger.fence(inactive, 7L, 7L)); assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(live)));
        var pending = ledger.pendingAdoption(ACTOR).orElseThrow();
        var successor = live.liveBody(FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, 17L, 4L).inactiveCarrier();
        var staleBody = live.liveBody(FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, 17L, 3L).inactiveCarrier();
        assertFalse(ledger.canFence(staleBody, 17L, 8L));
        assertFalse(ledger.fence(staleBody, 17L, 8L));
        assertTrue(ledger.fence(successor, 17L, 8L));
        assertTrue(ledger.pendingAdoptions().isEmpty());
        assertFalse(ledger.acknowledgeAdoption(pending, FrontierV3ActorAdoptionFixture.binding(live)));
        assertTrue(ledger.matchesCarrier(successor, 17L, 8L));
    }
    @Test void persistedDuplicateAndConcurrentAdoptionAreRejected() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var inactive = declaration(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 7L, 3L);
        var live = inactive.liveBody(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, 8L, 4L);
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
        var inactive = declaration(FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 7L, 1L);
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER, ledger.reconciliation(declaration(FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 8L, 2L)));
        assertTrue(ledger.fence(inactive, 7L, 0L));
        var foreign = new FrontierV3ActorCarrierComposition.Declaration(ACTOR, FrontierV3ActorCarrierComposition.ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, UUID.randomUUID(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 8L, 2L);
        var wrongKind = new FrontierV3ActorCarrierComposition.Declaration(ACTOR, FrontierV3ActorCarrierComposition.ActorKind.BIOFORM,
                FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, UUID_A, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 8L, 2L);
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.UUID_MISMATCH, ledger.reconciliation(foreign));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.KIND_MISMATCH, ledger.reconciliation(wrongKind));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.REPRESENTATION_MISMATCH, ledger.reconciliation(inactive));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.STALE_REVISION, ledger.reconciliation(declaration(FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 7L, 2L)));
        assertFalse(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(foreign))); assertEquals(1, ledger.inactiveCount());
    }
    @Test void carrierCannotBeReplacedDuplicatedOrConcurrentWithALiveBody() {
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var fenced = declaration(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 7L, 3L);
        var forgedOwner = declaration(FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 7L, 3L);
        var live = declaration(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 8L, 4L);
        assertTrue(ledger.fence(fenced, 7L, 7L));
        assertFalse(ledger.canFence(forgedOwner, 7L, 7L));
        assertFalse(ledger.matchesCarrier(forgedOwner, 7L, 7L));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.CONCURRENT_CUSTODY,
                ledger.reconciliation(live, true));
        assertEquals(1, ledger.inactiveCount());
    }
    @Test void closedSceneReturnNeedsRetainedGenerationAndCannotManufactureItFromAbsence() {
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var closedScene = declaration(FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 17L, 5L);
        var nextAmbient = declaration(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 9L, 6L);
        assertEquals(FrontierV3SceneExecutor.ClosedSceneReturnRecovery.CONFLICT,
                FrontierV3SceneExecutor.retainedClosedReturnAdmission(ledger, nextAmbient));
        assertEquals(0, ledger.inactiveCount(), "absence must not manufacture evidence");
        assertTrue(ledger.fence(closedScene, 17L, 8L));
        var before = ledger.save(new CompoundTag(), null);
        assertEquals(FrontierV3SceneExecutor.ClosedSceneReturnRecovery.FENCED,
                FrontierV3SceneExecutor.retainedClosedReturnAdmission(ledger, nextAmbient));
        for (var invalid : java.util.List.of(nextAmbient.liveBody(nextAmbient.owner(), 9L, 2L),
                nextAmbient.liveBody(nextAmbient.owner(), 8L, 6L),
                nextAmbient.liveBody(FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, 18L, 6L))) {
            assertEquals(FrontierV3SceneExecutor.ClosedSceneReturnRecovery.CONFLICT,
                    FrontierV3SceneExecutor.retainedClosedReturnAdmission(ledger, invalid));
        }
        assertEquals(before, ledger.save(new CompoundTag(), null), "return inspection must be read-only");
    }
    @Test void legacySchemaAndDuplicatePersistedCarrierFailBeforeRecovery() {
        CompoundTag legacy = new CompoundTag(); legacy.putInt("format", 2);
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(legacy, null));

        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var fenced = declaration(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 7L, 3L);
        assertTrue(ledger.fence(fenced, 7L, 7L));
        CompoundTag persisted = ledger.save(new CompoundTag(), null);
        ListTag duplicate = persisted.getList("carriers", net.minecraft.nbt.Tag.TAG_COMPOUND);
        duplicate.add(duplicate.getCompound(0).copy());
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(persisted, null));
    }
}
