package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;

class FrontierV3ActorFirstAdmissionLedgerTest {
    private static final Declaration BODY = new Declaration(new SubjectId("resident:1-1"), ActorKind.RESIDENT,
            Owner.AMBIENT_LEASE, new UUID(0, 1), Representation.LIVE_BODY, 1L, 1L);
    private static final FrontierV3ActorOwnerBinding TARGET = FrontierV3ActorOwnerBinding.ambient(BODY);
    private static FrontierV3ActorFirstAdmission permit() {
        return FrontierV3ActorFirstAdmission.neverCreated(new FrontierV3ActorFirstAdmission.Identity(BODY.actorId(), BODY.kind(), BODY.entityId()));
    }
    private static FrontierV3AmbientCarrierLedger reload(FrontierV3AmbientCarrierLedger ledger) {
        return FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null);
    }
    @Test void pendingRestartRequiresExactLateBodyForBothKindsAndInitialOwners() {
        for (var kind : ActorKind.values()) for (var owner : Owner.values()) {
            var body = new Declaration(new SubjectId("actor:first-admission"), kind, owner,
                    new UUID(7, 31), Representation.LIVE_BODY, 11L, 1L);
            var target = owner == Owner.AMBIENT_LEASE
                    ? FrontierV3ActorOwnerBinding.ambient(body)
                    : FrontierV3ActorOwnerBinding.scene(body, new SceneLeaseId("lease:first"));
            var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
            ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                    new FrontierV3ActorFirstAdmission.Identity(body.actorId(), kind, body.entityId())));
            var persisted = new java.util.concurrent.atomic.AtomicReference<CompoundTag>();
            assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmissionBoundary.admit(
                    ledger, target, () -> persisted.set(ledger.save(new CompoundTag(), null)),
                    () -> { throw new IllegalStateException("crash with unknown insertion outcome"); }));
            var recovered = FrontierV3AmbientCarrierLedger.load(persisted.get(), null);
            var pending = recovered.firstAdmission(body.actorId()).orElseThrow();
            assertEquals(FrontierV3ActorFirstAdmission.Phase.PENDING, pending.phase());
            assertFalse(FrontierV3ActorFirstAdmissionBoundary.admit(recovered, target,
                    () -> fail("restart must not reissue creation"),
                    () -> { fail("local absence must not produce a duplicate"); return true; }));
            var foreign = owner == Owner.AMBIENT_LEASE
                    ? FrontierV3ActorOwnerBinding.ambient(body.liveBody(owner, 12L, 1L))
                    : FrontierV3ActorOwnerBinding.scene(body, new SceneLeaseId("lease:foreign"));
            assertFalse(recovered.acknowledgeFirstAdmission(pending, foreign));
            assertEquals(pending, reload(recovered).firstAdmission(body.actorId()).orElseThrow());
            assertTrue(recovered.acknowledgeFirstAdmission(pending, target));
            var established = reload(recovered);
            assertEquals(FrontierV3ActorFirstAdmission.Phase.ESTABLISHED,
                    established.firstAdmission(body.actorId()).orElseThrow().phase());
            assertTrue(established.permitsRecordedOwner(target));
            assertFalse(established.beginFirstAdmission(target));
            assertFalse(established.rejectUncreatedFirstAdmission(pending));
        }
    }
    @Test void proofBackedRearmPreservesIdentityAndCannotBecomeUnbornHistory() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.registerFirstAdmission(permit()));
        assertTrue(ledger.beginFirstAdmission(TARGET));
        var pending = ledger.firstAdmission(BODY.actorId()).orElseThrow();
        String proof = "a".repeat(64);
        assertTrue(ledger.rearmFirstAdmissionAfterAbsence(pending, proof));
        var rearmed = reload(ledger);
        assertEquals(FrontierV3ActorFirstAdmission.Phase.PROVEN_ABSENT,
                rearmed.firstAdmission(BODY.actorId()).orElseThrow().phase());
        assertEquals(java.util.Optional.of(proof), rearmed.firstAdmission(BODY.actorId()).orElseThrow().absenceReceipt());
        assertFalse(rearmed.permitsRecordedOwner(TARGET), "a proved-absent body is not a live owner");
        assertFalse(rearmed.rearmFirstAdmissionAfterAbsence(pending, "b".repeat(64)));
        assertFalse(rearmed.rejectUncreatedFirstAdmission(pending));
        var foreign = FrontierV3ActorOwnerBinding.ambient(BODY.liveBody(Owner.AMBIENT_LEASE, 2L, 1L));
        assertThrows(IllegalStateException.class, () -> rearmed.firstAdmission(BODY.actorId()).orElseThrow().begin(foreign));
        assertTrue(rearmed.beginFirstAdmission(TARGET));
        var attemptedAgain = reload(rearmed).firstAdmission(BODY.actorId()).orElseThrow();
        assertEquals(FrontierV3ActorFirstAdmission.Phase.PENDING, attemptedAgain.phase());
        assertEquals(java.util.Optional.of(proof), attemptedAgain.absenceReceipt());
        assertFalse(rearmed.beginFirstAdmission(TARGET));
        assertTrue(rearmed.rejectUncreatedFirstAdmission(attemptedAgain));
        assertEquals(FrontierV3ActorFirstAdmission.Phase.PENDING,
                reload(rearmed).firstAdmission(BODY.actorId()).orElseThrow().phase());
    }
    @Test void oldHistoryLoadsButCannotForgeAProofBackedPermission() {
        var old = permit().save(); old.putInt("format", 1);
        assertEquals(permit(), FrontierV3ActorFirstAdmission.load(old));
        old.putString("phase", "absence_proven");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmission.load(old));
        var forged = permit().save(); forged.putString("phase", "absence_proven");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmission.load(forged));
        forged.putString("absenceReceipt", "not-a-sha256");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmission.load(forged));
    }
    @Test void refusedProofBackedInsertionNeedsANewOfflineProof() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.registerFirstAdmission(permit()));
        assertTrue(ledger.beginFirstAdmission(TARGET));
        assertTrue(ledger.rearmFirstAdmissionAfterAbsence(
                ledger.firstAdmission(BODY.actorId()).orElseThrow(), "b".repeat(64)));
        ledger = reload(ledger);
        var exact = ledger;
        assertFalse(FrontierV3ActorFirstAdmissionBoundary.admit(exact, TARGET,
                () -> assertNotNull(reload(exact).firstAdmission(BODY.actorId()).orElseThrow()), () -> false));
        assertEquals(FrontierV3ActorFirstAdmission.Phase.PENDING,
                reload(exact).firstAdmission(BODY.actorId()).orElseThrow().phase());
        assertFalse(exact.beginFirstAdmission(TARGET));
    }
    @Test void existingBodyCannotBypassUnusedPermitOrAnotherPendingSceneOwner() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); ledger.registerFirstAdmission(permit());
        var scene = FrontierV3ActorOwnerBinding.scene(BODY.liveBody(Owner.SCENE_LEASE, 17L, 1L), new SceneLeaseId("lease:first-scene"));
        assertFalse(ledger.permitsRecordedOwner(scene));
        assertTrue(ledger.beginFirstAdmission(TARGET));
        assertTrue(ledger.permitsRecordedOwner(TARGET));
        assertFalse(ledger.permitsRecordedOwner(scene));
        assertTrue(ledger.prepareHandoff(TARGET, scene));
        assertTrue(ledger.permitsRecordedOwner(scene));
        assertFalse(ledger.permitsRecordedOwner(TARGET));
        assertFalse(ledger.permitsRecordedOwner(FrontierV3ActorOwnerBinding.scene(scene.declaration(), new SceneLeaseId("lease:impostor"))));
        assertTrue(reload(ledger).permitsRecordedOwner(scene));
    }
    @Test void creationRunsOnlyAfterPersistenceAndFalseRestoresPermitDurably() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); ledger.registerFirstAdmission(permit());
        var sequence = new java.util.ArrayList<String>();
        assertFalse(FrontierV3ActorFirstAdmissionBoundary.admit(ledger, TARGET,
                () -> sequence.add(reload(ledger).firstAdmission(BODY.actorId()).orElseThrow().phase().name()),
                () -> { sequence.add("explicit-no-insertion"); return false; }));
        assertEquals(java.util.List.of("PENDING", "explicit-no-insertion", "NEVER_CREATED"), sequence);
        sequence.clear();
        assertTrue(FrontierV3ActorFirstAdmissionBoundary.admit(ledger, TARGET,
                () -> sequence.add("persisted"), () -> { assertEquals(java.util.List.of("persisted"), sequence); return true; }));
        assertEquals(FrontierV3ActorFirstAdmission.Phase.PENDING, ledger.firstAdmission(BODY.actorId()).orElseThrow().phase());
    }
    @Test void failedPersistencePreventsEffectAndThrowingEffectRetainsAmbiguity() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); ledger.registerFirstAdmission(permit());
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmissionBoundary.admit(ledger, TARGET,
                () -> { throw new IllegalStateException("write failed"); }, () -> { fail("no effect before durable intent"); return true; }));
        assertEquals(FrontierV3ActorFirstAdmission.Phase.PENDING, ledger.firstAdmission(BODY.actorId()).orElseThrow().phase());
        var another = FrontierV3AmbientCarrierLedger.emptyForTest(); another.registerFirstAdmission(permit());
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorFirstAdmissionBoundary.admit(another, TARGET,
                () -> {}, () -> { throw new IllegalStateException("unknown insertion result"); }));
        assertEquals(FrontierV3ActorFirstAdmission.Phase.PENDING, reload(another).firstAdmission(BODY.actorId()).orElseThrow().phase());
        assertFalse(FrontierV3ActorFirstAdmissionBoundary.admit(another, TARGET,
                () -> fail("cannot retry ambiguous insertion"), () -> { fail("cannot create another body"); return true; }));
    }
    @Test void missingHistoryIsNotAPermitAndSavedHistoryNeverBecomesFresh() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertFalse(ledger.beginFirstAdmission(TARGET));
        assertTrue(ledger.registerFirstAdmission(permit()));
        assertTrue(ledger.registerFirstAdmission(permit()), "issuance retry before admission is idempotent");
        assertTrue(ledger.beginFirstAdmission(TARGET));
        var pending = ledger.firstAdmission(BODY.actorId()).orElseThrow();
        assertFalse(ledger.beginFirstAdmission(TARGET));
        ledger = reload(ledger);
        assertEquals(pending, ledger.firstAdmission(BODY.actorId()).orElseThrow());
        assertTrue(ledger.acknowledgeFirstAdmission(pending, TARGET));
        ledger = reload(ledger);
        assertEquals(FrontierV3ActorFirstAdmission.Phase.ESTABLISHED, ledger.firstAdmission(BODY.actorId()).orElseThrow().phase());
        assertFalse(ledger.beginFirstAdmission(TARGET));
        assertFalse(ledger.registerFirstAdmission(permit()));
        assertFalse(ledger.rejectUncreatedFirstAdmission(pending));
        var old = ledger.save(new CompoundTag(), null); old.remove("firstAdmissions");
        assertTrue(FrontierV3AmbientCarrierLedger.load(old, null).firstAdmission(BODY.actorId()).isEmpty());
        assertFalse(FrontierV3AmbientCarrierLedger.load(old, null).beginFirstAdmission(TARGET));
    }
    @Test void explicitNoCreationRestoresPermitForNextLeaseNotTheOldAttempt() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); ledger.registerFirstAdmission(permit());
        assertTrue(ledger.beginFirstAdmission(TARGET));
        var old = ledger.firstAdmission(BODY.actorId()).orElseThrow();
        assertTrue(ledger.rejectUncreatedFirstAdmission(old));
        var next = FrontierV3ActorOwnerBinding.ambient(BODY.liveBody(Owner.AMBIENT_LEASE, 2L, 1L));
        assertTrue(ledger.beginFirstAdmission(next));
        assertFalse(ledger.rejectUncreatedFirstAdmission(old));
        assertFalse(ledger.acknowledgeFirstAdmission(old, TARGET));
        assertEquals(next, reload(ledger).firstAdmission(BODY.actorId()).orElseThrow().attempt().orElseThrow());
    }
    @Test void handoffLatestSaveSettlesBirthWithoutRestoringFirstCreation() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); ledger.registerFirstAdmission(permit());
        ledger.beginFirstAdmission(TARGET); var pending = ledger.firstAdmission(BODY.actorId()).orElseThrow();
        var scene = FrontierV3ActorOwnerBinding.scene(BODY.liveBody(Owner.SCENE_LEASE, 17L, 1L), new SceneLeaseId("lease:successor"));
        assertTrue(ledger.prepareHandoff(TARGET, scene));
        assertFalse(ledger.acknowledgeFirstAdmission(pending, TARGET));
        assertFalse(ledger.rejectUncreatedFirstAdmission(pending));
        ledger = reload(ledger);
        assertTrue(ledger.acknowledgeHandoff(ledger.pendingHandoff(BODY.actorId()).orElseThrow(), scene.declaration()));
        assertEquals(FrontierV3ActorFirstAdmission.Phase.ESTABLISHED, reload(ledger).firstAdmission(BODY.actorId()).orElseThrow().phase());
        assertFalse(ledger.beginFirstAdmission(TARGET));
    }
    @Test void exactFenceSupersedesFirstBodyAndLateAckCannotEraseIt() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); ledger.registerFirstAdmission(permit());
        assertFalse(ledger.fence(BODY.inactiveCarrier(), 1L, 1L), "never-created permit is not a real physical predecessor");
        ledger.beginFirstAdmission(TARGET); var pending = ledger.firstAdmission(BODY.actorId()).orElseThrow();
        assertFalse(ledger.fence(BODY.liveBody(Owner.AMBIENT_LEASE, 2L, 1L).inactiveCarrier(), 2L, 2L));
        assertTrue(ledger.fence(BODY.inactiveCarrier(), 1L, 1L));
        assertFalse(ledger.acknowledgeFirstAdmission(pending, TARGET));
        assertFalse(ledger.rejectUncreatedFirstAdmission(pending));
        ledger = reload(ledger);
        assertTrue(ledger.hasCarrier(BODY.actorId()));
        assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(BODY.liveBody(Owner.AMBIENT_LEASE, 2L, 2L))));
        assertEquals(FrontierV3ActorFirstAdmission.Phase.ESTABLISHED, reload(ledger).firstAdmission(BODY.actorId()).orElseThrow().phase());
    }
}
