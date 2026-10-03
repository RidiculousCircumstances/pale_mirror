package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorBodyAuthorityTest {
    @Test void exactPhysicalReleaseRetiresOnlyTheIncarnationAndPreservesActivity() {
        var state = ResourceSiteHarvestProcessTest.initial();
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution)
                .commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, body);
        assertTrue(ActorBodyAuthority.retainsPhysicalCustody(state, actor));
        var running = state;
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.released(running,
                new ActorBodyId(actor, body.physicalEpoch() + 1L)));
        var released = ActorBodyAuthority.released(state, body);
        assertFalse(ActorBodyAuthority.retainsPhysicalCustody(released, actor));
        assertSame(state.actorExecutions(), released.actorExecutions());
        assertSame(state.actorLocations(), released.actorLocations());
        assertSame(state.inventory(), released.inventory());
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.released(released, body));
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(released));
        var next = ActorBodyAuthority.current(ActorBodyAuthority.demand(recovered, actor), actor);
        assertEquals(body.physicalEpoch() + 1L, next.physicalEpoch());
        assertEquals(execution, recovered.actorExecutions().actors().get(actor).current().orElseThrow());
    }
    @Test void missingActorForeignOwnerAndSceneRevisionCannotBecomeBodyAuthority() {
        var state = ResourceSiteHarvestProcessTest.initial();
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var id = ActorBodyId.recoveryBindingId(actor);
        var foreignOwner = FencedRecoveryBinding.prepared(id, FencedRecoveryAsset.BODY,
                new SubjectId("scene:forged-owner"), 0L, 1L, true);
        var sceneRevision = FencedRecoveryBinding.prepared(id, FencedRecoveryAsset.BODY, actor, 1L, 1L, true);
        var missingActor = new SubjectId("actor:absent-body-owner");
        var absent = FencedRecoveryBinding.prepared(ActorBodyId.recoveryBindingId(missingActor),
                FencedRecoveryAsset.BODY, missingActor, 0L, 1L, true);
        for (var malformed : java.util.List.of(foreignOwner, sceneRevision, absent)) {
            var recovery = state.fencedRecovery().prepare(malformed);
            assertThrows(IllegalArgumentException.class, () -> state.withChanges(
                    FrontierWorldStateUpdate.begin().fencedRecovery(recovery)));
        }
        assertThrows(IllegalArgumentException.class, () -> new FencedRecoveryPayloads.Prepared(
                FencedRecoveryBinding.prepared(id, FencedRecoveryAsset.BODY, actor, 0L, 1L, true)),
                "generic physical recovery cannot introduce a competing body owner");
    }
    @Test void sceneCompletionAndAmbientDemandRetainTheActorOwnedIncarnation() {
        var hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        var state = hot.state();
        var actor = hot.job().workerId();
        var identity = ActorBodyAuthority.current(state, actor);
        var authority = ActorBodyAuthority.require(state, identity);
        assertEquals(actor, authority.ownerId());
        assertEquals(0L, authority.ownerRevision());
        assertEquals(FencedRecoveryPhase.RUNNING, authority.phase());
        var conflicted = state.transitionSceneLease(hot.lease().id(), SceneLeaseStatus.CONFLICT);
        assertSame(authority, ActorBodyAuthority.require(conflicted, identity),
                "a job conflict cannot isolate or replace its actor's body");
        state = state.transitionSceneLease(hot.lease().id(), SceneLeaseStatus.DRAINING);
        state = state.releaseSceneLease(hot.lease().id(), java.util.List.of(new SceneMemberPosition(
                actor, state.actorLocations().get(actor).body(), state.actorLocations().get(actor).condition().health())));
        assertSame(authority, ActorBodyAuthority.require(state, identity),
                "finishing process participation is not physical-body removal");
        assertFalse(ActorExecutionCoordinator.coldAvailable(state, actor),
                "a closed scene with a retained live body still excludes background advancement");
        var ambient = io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess.nextLease(
                state, actor, io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO);
        state = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.prepare(state, ambient);
        state = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.transition(
                state, actor, AmbientLeaseStatus.HOT);
        assertEquals(identity, ActorBodyAuthority.current(state, actor));
        assertSame(authority, ActorBodyAuthority.require(state, identity));
        assertEquals(hot.state().actorExecutions(), state.actorExecutions());
        assertEquals(hot.state().inventory(), state.inventory());
        var codec = new FrontierWorldStateCodec();
        var recovered = codec.decode(codec.encode(state));
        assertEquals(authority, ActorBodyAuthority.require(recovered, identity));
    }

    @Test void bodyEpochSurvivesActivityChangeAndExactRecoveryButOldActuatorCannotRun() {
        var state = ResourceSiteHarvestProcessTest.initial();
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var body = ActorBodyAuthority.next(state, actor);
        var presence = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, presence)
                .commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.prepare(state, body);
        state = ActorBodyAuthority.running(state, body);
        var previous = new ActorActuationId(body, presence);
        ActorBodyAuthority.requireActuation(state, previous);
        var retainedBody = ActorBodyAuthority.require(state, body);
        var next = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, next, 1L)
                .commit(state, FrontierWorldStateUpdate.begin());
        assertEquals(retainedBody, ActorBodyAuthority.require(state, body));
        ActorBodyAuthority.requireActuation(state, new ActorActuationId(body, next));
        var current = state;
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.requireActuation(current, previous));
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.prepare(current, body));
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.require(current,
                new ActorBodyId(actor, body.physicalEpoch() + 1L)));
        assertThrows(IllegalArgumentException.class, () -> new ActorActuationId(
                new ActorBodyId(new SubjectId("actor:another"), body.physicalEpoch()), next));
        var isolated = ActorBodyAuthority.isolate(state, body, "loaded-body-inspection");
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.requireActuation(isolated,
                new ActorActuationId(body, next)));
        var codec = new FrontierWorldStateCodec();
        var hydrated = codec.decode(codec.encode(isolated));
        assertEquals(body.physicalEpoch(), ActorBodyAuthority.require(hydrated, body).authorityEpoch());
        var recovered = ActorBodyAuthority.inspectedRunning(hydrated, body);
        ActorBodyAuthority.requireActuation(recovered, new ActorActuationId(body, next));
        assertEquals(next, recovered.actorExecutions().actors().get(actor).current().orElseThrow());
        assertEquals(state.actorLocations(), recovered.actorLocations());
        assertEquals(state.inventory(), recovered.inventory());
        assertEquals(SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor),
                ActorBodyId.entityId(state.bootstrap().worldId(), actor));
    }
}
