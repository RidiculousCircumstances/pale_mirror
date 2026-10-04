package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorBodyAuthorityTest {
    @Test void savedAssemblyDepartureRejoinsWithoutCreditingArrivalOrMovingCargo() {
        var state = FrontierV3FixtureCatalog.operationAssemblyConfiguration(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:assembly-rejoin"), 91L).initialState();
        var operation = FrontierDevelopmentScenarios.initialNorthwatchAssembly(state).orElseThrow();
        var assembly = operation.activeAssembly().orElseThrow();
        var actor = assembly.safeAdvances().stream().filter(id -> {
            var approach = assembly.members().get(id);
            return approach.cursor() + 2 < approach.corridor().size()
                    && assembly.members().entrySet().stream().noneMatch(other -> !other.getKey().equals(id)
                    && other.getValue().currentSurface().equals(approach.corridor().get(approach.cursor() + 2)));
        }).findFirst().orElseThrow();
        var member = assembly.members().get(actor);
        state = ModeledActorBodyFacts.present(state, actor);
        var location = state.actorLocations().get(actor);
        var body = ActorBodyAuthority.current(state, actor);
        var execution = state.actorExecutions().actors().get(actor).current().orElseThrow();
        var observed = member.corridor().get(member.cursor() + 2).standingBody();
        var unloaded = ActorBodyAuthority.unloaded(state, new ActorBodyUnloaded(body, location.body(),
                location.condition().health(), observed, location.condition().health(), java.util.Optional.of(execution)));
        var checkpoint = unloaded.operations().get(operation.id()).activeAssembly().orElseThrow();
        assertEquals(member.cursor(), checkpoint.members().get(actor).cursor());
        assertEquals(member.topology(), checkpoint.members().get(actor).topology());
        assertEquals(member.routeRevision() + 1, checkpoint.members().get(actor).routeRevision());
        assertEquals(observed, checkpoint.members().get(actor).currentSurface().standingBody());
        assertTrue(checkpoint.members().get(actor).rejoin().isPresent());
        assertTrue(checkpoint.members().get(actor).rejoin().orElseThrow().path().size() > 1,
                "the saved actual position requires real known travel, not merely arrival credit");
        assertEquals(state.inventory(), unloaded.inventory());
        assertEquals(state.actorExecutions(), unloaded.actorExecutions());
        assertTrue(ActorExecutionCoordinator.coldAvailable(unloaded, actor));
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(unloaded));
        assertEquals(checkpoint, recovered.operations().get(operation.id()).activeAssembly().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> checkpoint.advance(assembly.advance(actor).members()),
                "an obsolete pre-departure route revision cannot certify arrival");
        var advanced = checkpoint.advance(actor);
        var blocked = recovered.recordPhysicalDelta(new PhysicalDelta(
                advanced.members().get(actor).currentSurface().support().offset(0, 1, 0), PhysicalDeltaKind.UNKNOWN_SCAR,
                java.util.Optional.empty(), java.util.Optional.empty(), "test:blocked-assembly-rejoin"));
        assertThrows(IllegalArgumentException.class, () -> blocked.advanceOperationAssembly(operation.id(), advanced,
                OperationExecutionAuthority.assemblyCurrent(blocked, operation)));
        var planned = io.farfrontier.palemirror.frontier.v3.process.SupplyOperationProcess.planAssembly(blocked,
                io.farfrontier.palemirror.frontier.v3.process.SupplyOperationProcess.operationAssembly(operation, 20L));
        assertTrue(planned.stream().map(event -> event.payload()).filter(OperationAssemblyAdvanced.class::isInstance)
                .map(OperationAssemblyAdvanced.class::cast).allMatch(event ->
                        event.assembly().members().get(actor).equals(checkpoint.members().get(actor))),
                "ordinary planning must hold the blocked member instead of emitting a rejected advance");
        var receipt = new OperationAssemblyAdvanced(operation.id(), advanced, OperationExecutionAuthority.assemblyCurrent(recovered, operation));
        var codecs = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(receipt, codecs.decode(receipt.type(), codecs.encode(receipt)));
        var continued = recovered.advanceOperationAssembly(operation.id(), advanced, receipt.executions());
        assertEquals(member.cursor() + 1, continued.operations().get(operation.id()).activeAssembly().orElseThrow().members().get(actor).cursor());
        assertEquals(member.nextSurface().standingBody(), continued.actorLocations().get(actor).body());
        assertEquals(state.inventory(), continued.inventory());
    }
    @Test void physicalDeathWithoutAnyPresentationScopeRetiresExactBodyAndPassiveExecution() {
        var state = ResourceSiteHarvestProcessTest.initial();
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution).commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        var location = state.actorLocations().get(actor);
        var death = new ActorBodyDied(body, location.body(), location.condition().health(), java.util.Optional.empty(),
                java.util.Optional.of(execution), "environment");
        var notInserted = state;
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.died(notInserted, death, FrontierActorDeathConsequences.INSTANCE, 10L));
        state = ActorBodyAuthority.running(state, body);
        var running = state;
        var codecs = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(death, codecs.decode(death.type(), codecs.encode(death)));
        byte[] encoded = codecs.encode(death);
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(death.type(), java.util.Arrays.copyOf(encoded, encoded.length - 1)));
        for (var invalid : java.util.List.of(
                new ActorBodyDied(new ActorBodyId(actor, body.physicalEpoch() + 1), death.expectedBody(), death.expectedHealth(), death.observedBody(), death.expectedExecution(), death.cause()),
                new ActorBodyDied(body, death.expectedBody(), death.expectedHealth(), death.observedBody(), java.util.Optional.empty(), death.cause()),
                new ActorBodyDied(body, death.expectedBody().offset(1, 0, 0), death.expectedHealth(), death.observedBody(), death.expectedExecution(), death.cause()),
                new ActorBodyDied(body, death.expectedBody(), death.expectedHealth(), death.observedBody(), java.util.Optional.of(
                        new ActorExecutionId(actor, ActorActivityKind.PRESENCE, actor, execution.generation() + 1)), death.cause())))
            assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.died(running, invalid, FrontierActorDeathConsequences.INSTANCE, 10L));
        var died = ActorBodyAuthority.died(state, death, FrontierActorDeathConsequences.INSTANCE, 10L);
        assertEquals(ActorLifeStatus.DEAD, died.actorLocations().get(actor).condition().status());
        assertEquals(location.body(), died.actorLocations().get(actor).body(), "an airborne death cannot fabricate a supporting surface");
        assertTrue(died.actorExecutions().actors().get(actor).current().isEmpty());
        assertFalse(ActorBodyAuthority.retainsPhysicalCustody(died, actor));
        assertSame(state.inventory(), died.inventory());
        assertEquals(body.physicalEpoch(), died.fencedRecovery().tombstones().get(ActorBodyId.recoveryBindingId(actor)).retiredEpoch());
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.died(died, death, FrontierActorDeathConsequences.INSTANCE, 10L));
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.demand(died, actor));
        assertEquals(died, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(died)));
    }

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
