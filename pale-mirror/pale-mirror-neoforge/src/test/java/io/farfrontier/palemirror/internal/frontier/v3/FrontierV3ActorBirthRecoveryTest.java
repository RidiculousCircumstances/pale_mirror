package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;

class FrontierV3ActorBirthRecoveryTest {
    private static final FrontierWorldState STATE = FrontierWorldState.initial(
            FrontierBootstrapper.create(new WorldId("frontier:unpublished-birth"), 91L));
    private static FrontierV3ActorFirstAdmission permit(String id) {
        var actor = new SubjectId(id);
        return FrontierV3ActorFirstAdmission.neverCreated(new FrontierV3ActorFirstAdmission.Identity(actor,
                ActorKind.RESIDENT, SceneLease.deterministicEntityId(STATE.bootstrap().worldId(), actor)));
    }
    @Test void removesOnlyUnpublishedUnusedPermissionsAndNeverMintsFromAbsence() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var published = permit("resident:1-1"); var unborn = permit("resident:unpublished");
        assertTrue(ledger.registerFirstAdmission(published)); assertTrue(ledger.registerFirstAdmission(unborn));
        var saves = new AtomicInteger();
        assertEquals(1, FrontierV3ActorBirthRecovery.retireUnpublished(STATE, ledger, saves::incrementAndGet));
        assertEquals(1, saves.get());
        assertEquals(java.util.List.of(published), ledger.firstAdmissions());
        assertEquals(0, FrontierV3ActorBirthRecovery.retireUnpublished(STATE, ledger, () -> fail("nothing changed")));
        assertTrue(ledger.firstAdmission(unborn.identity().actorId()).isEmpty());
        // A subsequent explicit canonical birth can issue anew; reconciliation itself did not.
        assertTrue(ledger.registerFirstAdmission(unborn));
    }
    @Test void missingCanonicalActorCannotErasePendingOrEstablishedPhysicalHistory() {
        for (boolean established : new boolean[]{false, true}) {
            var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); var permission = permit("resident:unpublished");
            ledger.registerFirstAdmission(permission);
            var id = permission.identity();
            var binding = FrontierV3ActorOwnerBinding.ambient(new Declaration(id.actorId(), id.kind(), Owner.AMBIENT_LEASE,
                    id.entityId(), Representation.LIVE_BODY, 1, 1));
            assertTrue(ledger.beginFirstAdmission(binding));
            if (established) assertTrue(ledger.acknowledgeFirstAdmission(ledger.firstAdmission(id.actorId()).orElseThrow(), binding));
            var before = ledger.save(new CompoundTag(), null);
            assertFalse(ledger.retireUnbornPermission(STATE, permission), "stale unused receipt cannot delete newer history");
            assertEquals(0, FrontierV3ActorBirthRecovery.retireUnpublished(STATE, ledger, () -> fail("no history write")));
            assertEquals(before, ledger.save(new CompoundTag(), null));
        }
    }
    @Test void foreignWorldIdentityIsNotAnUnpublishedBirthInThisWorld() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); var actor = new SubjectId("resident:unpublished");
        var foreign = FrontierV3ActorFirstAdmission.neverCreated(new FrontierV3ActorFirstAdmission.Identity(actor,
                ActorKind.RESIDENT, SceneLease.deterministicEntityId(new WorldId("frontier:foreign"), actor)));
        assertTrue(ledger.registerFirstAdmission(foreign));
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorBirthRecovery.retireUnpublished(STATE, ledger,
                () -> fail("foreign custody must not be persisted as a repair")));
        assertEquals(foreign, ledger.firstAdmission(actor).orElseThrow());
    }
    @Test void laterContradictionCannotPartiallyRetireEarlierUnusedBirth() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var valid = permit("resident:a-unpublished");
        var foreignActor = new SubjectId("resident:z-unpublished");
        var foreign = FrontierV3ActorFirstAdmission.neverCreated(new FrontierV3ActorFirstAdmission.Identity(
                foreignActor, ActorKind.RESIDENT,
                SceneLease.deterministicEntityId(new WorldId("frontier:foreign"), foreignActor)));
        assertTrue(ledger.registerFirstAdmission(valid));
        assertTrue(ledger.registerFirstAdmission(foreign));
        var before = ledger.save(new CompoundTag(), null);
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorBirthRecovery.retireUnpublished(STATE, ledger,
                () -> fail("contradictory batch must never be persisted")));
        assertEquals(before, ledger.save(new CompoundTag(), null));
        assertEquals(java.util.List.of(valid, foreign), ledger.firstAdmissions());
    }
    @Test void severalUnusedBirthsRetireTogetherWithOnePersistence() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.registerFirstAdmission(permit("resident:a-unpublished")));
        assertTrue(ledger.registerFirstAdmission(permit("resident:b-unpublished")));
        var saves = new AtomicInteger();
        assertEquals(2, FrontierV3ActorBirthRecovery.retireUnpublished(STATE, ledger, saves::incrementAndGet));
        assertEquals(1, saves.get());
        assertTrue(ledger.firstAdmissions().isEmpty());
    }
    @Test void failedPersistencePropagatesAndOldDiskImageCanRepeatCleanup() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); ledger.registerFirstAdmission(permit("resident:unpublished"));
        var original = ledger.save(new CompoundTag(), null);
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorBirthRecovery.retireUnpublished(STATE, ledger,
                () -> { throw new IllegalStateException("storage failed"); }));
        var reloaded = FrontierV3AmbientCarrierLedger.load(original, null);
        assertEquals(1, FrontierV3ActorBirthRecovery.retireUnpublished(STATE, reloaded, () -> {}));
        assertTrue(reloaded.firstAdmissions().isEmpty());
    }
}
