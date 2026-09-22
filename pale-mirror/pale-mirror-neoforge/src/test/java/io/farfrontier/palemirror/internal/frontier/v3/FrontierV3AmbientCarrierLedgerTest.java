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
        assertTrue(ledger.adopt(live)); assertEquals(0, ledger.inactiveCount());
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
        assertFalse(ledger.adopt(foreign)); assertEquals(1, ledger.inactiveCount());
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
    @Test void observedAbsentClosedSceneMayFenceOnlyItsExactNextAmbientReturn() {
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var closedScene = declaration(FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 17L, 1L);
        var nextAmbient = declaration(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 9L, 2L);
        assertFalse(ledger.fenceObservedAbsentClosedScene(closedScene, 17L, 8L, false),
                "an unobserved closed scene may not manufacture an inactive carrier");
        assertTrue(ledger.fenceObservedAbsentClosedScene(closedScene, 17L, 8L, true),
                "one naturally observed absent exact scene body fences the closed scene authority");
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.READY, ledger.reconciliation(nextAmbient),
                "only the newer exact ambient lease may reconstruct the fenced scene actor");
        assertFalse(ledger.fenceObservedAbsentClosedScene(declaration(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 18L, 1L), 18L, 9L, true),
                "an ambient or foreign owner cannot impersonate the closed-scene recovery edge");
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
