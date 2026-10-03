package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3AmbientCarrierRecognitionTest {
    @Test void closedStatusAloneCannotAuthorizeDeletingReturnedBody() {
        var prepared = FrontierV3OfflineActorRecoveryPlanTest.prepared();
        var actor = new SubjectId("resident:1-1");
        var draining = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.transition(prepared, actor,
                io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.DRAINING);
        var closed = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.release(draining,
                new io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseReleased(actor,
                        draining.actorLocations().get(actor).body(), draining.actorLocations().get(actor).condition().health()));
        long revision = closed.ambientLeases().get(actor).revision();
        var id = FrontierV3AmbientActorExecutor.entityId(closed, actor);
        var observed = new FrontierV3AmbientCarrierRecognition.ManagedCarrier(id, actor.value(), false, false,
                "RESIDENT", "AMBIENT_LEASE", "LIVE_BODY", revision, 3L);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertFalse(FrontierV3AmbientCarrierRecognition.retainedClosedRelease(closed, observed, ledger));
        var fence = FrontierV3AmbientActorExecutor.carrierDeclaration(closed, actor,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, id,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, revision, 3L);
        assertTrue(ledger.fence(fence, revision, revision));
        assertTrue(FrontierV3AmbientCarrierRecognition.retainedClosedRelease(closed, observed, ledger));
        assertFalse(FrontierV3AmbientCarrierRecognition.retainedClosedRelease(prepared, observed, ledger));
        var anotherGeneration = new FrontierV3AmbientCarrierRecognition.ManagedCarrier(id, actor.value(), false, false,
                "RESIDENT", "AMBIENT_LEASE", "LIVE_BODY", revision, 2L);
        assertFalse(FrontierV3AmbientCarrierRecognition.retainedClosedRelease(closed, anotherGeneration, ledger));
        var anotherOwner = new FrontierV3AmbientCarrierRecognition.ManagedCarrier(id, actor.value(), false, false,
                "RESIDENT", "SCENE_LEASE", "LIVE_BODY", revision, 3L);
        assertFalse(FrontierV3AmbientCarrierRecognition.retainedClosedRelease(closed, anotherOwner, ledger));
        assertTrue(ledger.matchesCarrier(fence, revision, revision), "inspection cannot consume the release proof");
    }
    @Test void retainedInactiveCustodyAndDifferentPendingGenerationCannotBecomeHot() {
        var state = FrontierV3OfflineActorRecoveryPlanTest.prepared();
        var actor = new SubjectId("resident:1-1");
        var id = FrontierV3AmbientActorExecutor.entityId(state, actor);
        long revision = state.ambientLeases().get(actor).revision();
        var observed = new FrontierV3AmbientCarrierRecognition.ManagedCarrier(id, actor.value(), false, false,
                "RESIDENT", "AMBIENT_LEASE", "LIVE_BODY", revision, 2L);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(FrontierV3AmbientCarrierRecognition.recoverableOwnership(state, observed, ledger));
        var old = new FrontierV3ActorCarrierComposition.Declaration(actor,
                ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                id, FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, revision - 1, 1L);
        assertTrue(ledger.fence(old, revision - 1, revision - 1));
        assertFalse(FrontierV3AmbientCarrierRecognition.recoverableOwnership(state, observed, ledger));
        assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(old.liveBody(old.owner(), revision, 2L))));
        assertTrue(FrontierV3AmbientCarrierRecognition.recoverableOwnership(state, observed, ledger));
        var wrongEpoch = new FrontierV3AmbientCarrierRecognition.ManagedCarrier(id, actor.value(), false, false,
                "RESIDENT", "AMBIENT_LEASE", "LIVE_BODY", revision, 3L);
        assertFalse(FrontierV3AmbientCarrierRecognition.recoverableOwnership(state, wrongEpoch, ledger));
        assertTrue(ledger.pendingAdoption(actor).isPresent(), "recognition never acknowledges persistence");
    }
    @Test void stableUuidAloneDoesNotRecoverStaleOrForeignAuthority() {
        var state = FrontierV3OfflineActorRecoveryPlanTest.prepared();
        var actor = new SubjectId("resident:1-1");
        var id = FrontierV3AmbientActorExecutor.entityId(state, actor);
        long revision = state.ambientLeases().get(actor).revision();
        var valid = new FrontierV3AmbientCarrierRecognition.ManagedCarrier(id, actor.value(), false, false,
                "RESIDENT", "AMBIENT_LEASE", "LIVE_BODY", revision, 2L);
        assertTrue(FrontierV3AmbientCarrierRecognition.recognizesOwnership(state, valid));
        for (var invalid : java.util.List.of(
                new FrontierV3AmbientCarrierRecognition.ManagedCarrier(id, actor.value(), false, false,
                        "RESIDENT", "AMBIENT_LEASE", "LIVE_BODY", revision - 1, 2L),
                new FrontierV3AmbientCarrierRecognition.ManagedCarrier(id, actor.value(), false, false,
                        "RESIDENT", "AMBIENT_LEASE", "LIVE_BODY", revision + 1, 2L),
                new FrontierV3AmbientCarrierRecognition.ManagedCarrier(id, actor.value(), false, false,
                        "RESIDENT", "SCENE_LEASE", "LIVE_BODY", revision, 2L),
                new FrontierV3AmbientCarrierRecognition.ManagedCarrier(id, actor.value(), false, false,
                        "RESIDENT", "", "LIVE_BODY", revision, 2L),
                new FrontierV3AmbientCarrierRecognition.ManagedCarrier(id, actor.value(), false, false,
                        "RESIDENT", "AMBIENT_LEASE", "INACTIVE_CARRIER", revision, 2L),
                new FrontierV3AmbientCarrierRecognition.ManagedCarrier(id, actor.value(), false, false,
                        "RESIDENT", "AMBIENT_LEASE", "", revision, 2L),
                new FrontierV3AmbientCarrierRecognition.ManagedCarrier(id, actor.value(), false, false,
                        "RESIDENT", "AMBIENT_LEASE", "LIVE_BODY", revision, 0L))) {
            assertFalse(FrontierV3AmbientCarrierRecognition.recognizesOwnership(state, invalid), invalid.toString());
        }
    }
}
