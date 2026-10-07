package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;

class FrontierV3AmbientCarrierRecognitionTest {
    @Test void packAnimalRequiresItsExactProducerAndPhysicalTypeNotTheNonBioformFallback() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:pack-recognition"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1")));
        var asset = state.transportFleet().assets().values().iterator().next();
        state = ActorBodyAuthority.demand(state, asset.actorId());
        var body = FrontierV3AmbientActorExecutor.carrierDeclaration(state, asset.actorId(),
                FrontierV3AmbientActorExecutor.entityId(state, asset.actorId()), Representation.LIVE_BODY,
                ActorBodyAuthority.current(state, asset.actorId()).physicalEpoch());
        var observed = new FrontierV3AmbientCarrierRecognition.ManagedCarrier(body.entityId(), body.actorId().value(),
                false, body.kind().name(), body.owner().name(), body.representation().name(),
                body.authorityRevision(), body.epoch(), "minecraft:donkey");
        assertTrue(FrontierV3AmbientCarrierRecognition.recognizesOwnership(state, observed));
        assertFalse(FrontierV3AmbientCarrierRecognition.recognizesOwnership(state,
                new FrontierV3AmbientCarrierRecognition.ManagedCarrier(body.entityId(), body.actorId().value(),
                        false, body.kind().name(), body.owner().name(), body.representation().name(),
                        body.authorityRevision(), body.epoch(), "minecraft:villager")));
        var retained = state;
        assertThrows(IllegalArgumentException.class, () -> fromCanonical(retained, asset.actorId(), ActorKind.RESIDENT,
                Owner.ACTOR_BODY, body.entityId(), Representation.LIVE_BODY, 0, body.epoch()));
    }
    private static final SubjectId ACTOR = new SubjectId("resident:1-1");
    private static Declaration declaration(FrontierWorldState state) {
        return FrontierV3AmbientActorExecutor.carrierDeclaration(state, ACTOR,
                FrontierV3AmbientActorExecutor.entityId(state, ACTOR), Representation.LIVE_BODY,
                ActorBodyAuthority.current(state, ACTOR).physicalEpoch());
    }
    private static FrontierV3AmbientCarrierRecognition.ManagedCarrier observed(Declaration body) {
        return observed(body, body.owner().name(), body.authorityRevision(), body.epoch(), body.representation().name());
    }
    private static FrontierV3AmbientCarrierRecognition.ManagedCarrier observed(Declaration body, String owner,
                                                                             long revision, long epoch, String representation) {
        return new FrontierV3AmbientCarrierRecognition.ManagedCarrier(body.entityId(), body.actorId().value(), false,
                body.kind().name(), owner, representation, revision, epoch, FrontierV3ActorCarrierFactory.entityType(body.kind()));
    }

    @Test void closingActivityCannotAuthorizeRetiringItsPhysicalBody() {
        var prepared = FrontierV3OfflineActorRecoveryPlanTest.prepared();
        var body = declaration(prepared);
        var draining = AmbientLeaseStateProcess.transition(prepared, ACTOR, AmbientLeaseStatus.DRAINING);
        var location = draining.actorLocations().get(ACTOR);
        var closed = AmbientLeaseStateProcess.release(draining,
                new AmbientLeaseReleased(ACTOR, location.body(), location.condition().health()));
        assertTrue(FrontierV3AmbientCarrierRecognition.recognizesOwnership(closed, observed(body)));
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertFalse(ledger.fencesBody(body), "closed activity without exact physical evidence is not retirement authority");
        FrontierV3ActorAdoptionFixture.establishPhysicalHistory(ledger, body);
        assertTrue(ledger.fence(body.inactiveCarrier(), body.epoch(), 0L));
        assertTrue(ledger.fencesBody(body));
        assertFalse(ledger.fencesBody(body.liveBody(body.owner(), 0L, body.epoch() + 1L)));
        assertTrue(ledger.matchesCarrier(body.inactiveCarrier(), body.epoch(), 0L), "inspection cannot consume the proof");
    }

    @Test void retainedInactiveCustodyAndDifferentPendingGenerationCannotBecomeHot() {
        var state = FrontierV3OfflineActorRecoveryPlanTest.prepared();
        var old = declaration(state);
        state = ActorBodyAuthority.demand(ActorBodyAuthority.released(state, ActorBodyAuthority.current(state, ACTOR)), ACTOR);
        var current = declaration(state);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertFalse(FrontierV3AmbientCarrierRecognition.recoverableOwnership(state, observed(current), ledger),
                "canonical identity alone cannot fabricate physical history");
        FrontierV3ActorAdoptionFixture.establishPhysicalHistory(ledger, old);
        assertTrue(FrontierV3AmbientCarrierRecognition.recoverableOwnership(state, observed(current), ledger));
        assertTrue(ledger.fence(old.inactiveCarrier(), old.epoch(), 0L));
        assertFalse(FrontierV3AmbientCarrierRecognition.recoverableOwnership(state, observed(current), ledger));
        assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(current)));
        assertTrue(FrontierV3AmbientCarrierRecognition.recoverableOwnership(state, observed(current), ledger));
        assertFalse(FrontierV3AmbientCarrierRecognition.recoverableOwnership(state,
                observed(current, "ACTOR_BODY", 0L, current.epoch() + 1L, "LIVE_BODY"), ledger));
        assertTrue(ledger.pendingAdoption(ACTOR).isPresent(), "recognition never acknowledges persistence");
    }

    @Test void stableUuidAloneDoesNotRecoverStaleForeignOrMissingAuthority() {
        var state = FrontierV3OfflineActorRecoveryPlanTest.prepared();
        var body = declaration(state);
        assertTrue(FrontierV3AmbientCarrierRecognition.recognizesOwnership(state, observed(body)));
        for (var invalid : java.util.List.of(
                observed(body, "ACTOR_BODY", -1L, body.epoch(), "LIVE_BODY"),
                observed(body, "ACTOR_BODY", 1L, body.epoch(), "LIVE_BODY"),
                observed(body, "SCENE_LEASE", 0L, body.epoch(), "LIVE_BODY"),
                observed(body, "AMBIENT_LEASE", 0L, body.epoch(), "LIVE_BODY"),
                observed(body, "", 0L, body.epoch(), "LIVE_BODY"),
                observed(body, "ACTOR_BODY", 0L, body.epoch(), "INACTIVE_CARRIER"),
                observed(body, "ACTOR_BODY", 0L, body.epoch(), ""),
                observed(body, "ACTOR_BODY", 0L, 0L, "LIVE_BODY"))) {
            assertFalse(FrontierV3AmbientCarrierRecognition.recognizesOwnership(state, invalid), invalid.toString());
        }
    }
}
