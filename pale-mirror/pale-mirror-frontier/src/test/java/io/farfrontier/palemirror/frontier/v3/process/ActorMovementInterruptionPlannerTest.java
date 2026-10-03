package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ActorMovementInterruptionPlannerTest {
    @Test void interruptionCancelsItsOwnContinuationAndStartsFoodInOneEngineTransaction() {
        FrontierWorldState state = fixture(false);
        SubjectId actor = new SubjectId("resident:1-1");
        ActorMovement movement = state.actorMovements().get(actor);
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 421L);
        var config = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                state.bootstrap().worldId(), state, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(23_999L),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                base.projectionMapper(), base.limits(), List.of(ResidentActivityProcess.review(actor, 24_000L),
                    ActorMovementProcess.progress(movement, 24_001L)), base.transactionCommitter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(config);
        var result = engine.advanceTo(new io.farfrontier.palemirror.frontier.v3.api.SimInstant(24_000L),
                new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(1, 512));
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE,
                result.status().kind(), () -> result.status().failureDetail().orElse("quarantined"));
        var after = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec()
                .decode(engine.checkpoint().canonicalState());
        assertFalse(after.actorMovements().containsKey(actor));
        assertTrue(after.humanPopulation().meals().containsKey(actor));
        assertEquals(state.actorLocations().get(actor).body(), after.actorLocations().get(actor).body());
    }

    @Test void hotMovementKeepsTheSameAuthorityAndChangesPurposeWithoutRelocation() {
        FrontierWorldState state = fixture(false);
        SubjectId actor = new SubjectId("resident:1-1");
        BodyPosition body = state.actorLocations().get(actor).body();
        var lease = new AmbientActorLease(actor, body,
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(23_900L), 1L,
                AmbientLeaseStatus.HOT, AmbientGoalKind.ACTOR_MOVEMENT,
                state.actorMovements().get(actor).order().legalStations().getFirst().standingBody());
        state = state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(Map.of(actor, lease)));
        var ready = assertInstanceOf(ActivityInterruptionPlanner.Ready.class,
                new ActorMovementInterruptionPlanner().assess(state, actor, 24_000L));
        assertEquals(body, ready.following().actorLocations().get(actor).body());
        assertEquals(lease, ready.following().ambientLeases().get(actor));
        var planned = ResidentActivityProcess.plan(state, ResidentActivityProcess.review(actor, 24_000L));
        assertInstanceOf(ActorMovementInterrupted.class, planned.getFirst().payload());
        assertTrue(planned.stream().anyMatch(event -> event.payload() instanceof ResidentMealStarted));
    }
    @Test void aHungryResidentCanReplaceAnUnfinishedHomeJourneyWithoutReachingHome() {
        FrontierWorldState state = fixture(false);
        SubjectId actor = new SubjectId("resident:1-1");
        BodyPosition before = state.actorLocations().get(actor).body();
        ActorMovement movement = state.actorMovements().get(actor);
        assertFalse(movement.order().arrivedAt(before.supportingSurface()));
        var action = ResidentActivityProcess.review(actor, 24_000L);
        assertFalse(ResidentActivityProcess.held(state, action));
        var planned = FrontierWorldRuntimeDefinition.planScheduled(state, action);
        var interrupted = assertInstanceOf(ActorMovementInterrupted.class, planned.getFirst().payload());
        assertEquals(before, interrupted.retainedBody());
        assertTrue(planned.stream().anyMatch(event -> event.payload() instanceof ResidentMealStarted));
        assertEquals(interrupted, FrontierWorldRuntimeDefinition.payloadCodecs().decode(interrupted.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(interrupted)));
        FrontierWorldState next = ActorMovementInterruptionPlanner.reduce(state, actor, interrupted);
        assertFalse(next.actorMovements().containsKey(actor));
        assertEquals(before, next.actorLocations().get(actor).body());
        assertThrows(IllegalArgumentException.class, () -> ActorMovementInterruptionPlanner.reduce(next, actor, interrupted));
        assertThrows(IllegalArgumentException.class, () -> ActorMovementInterruptionPlanner.reduce(state, actor,
                new ActorMovementInterrupted(actor, movement.order().goalRevision() + 1L, 24_000L, before, movement.executionId())));
    }

    @Test void serviceClearanceCannotBeSkippedToStartAnotherActivity() {
        FrontierWorldState state = fixture(true);
        SubjectId actor = new SubjectId("resident:1-1");
        var waiting = assertInstanceOf(ActivityInterruptionPlanner.Waiting.class,
                new ActorMovementInterruptionPlanner().assess(state, actor, 24_000L));
        assertEquals(ActivityInterruptionPlanner.Reason.SERVICE_CLEARANCE, waiting.reason());
        assertTrue(ResidentActivityProcess.held(state, ResidentActivityProcess.review(actor, 24_000L)));
    }

    private static FrontierWorldState fixture(boolean atService) {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:activity-interruption"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId actor = settlement.residents().getFirst().id();
        var depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow();
        var port = SettlementDepotServicePort.forDepot(depot);
        var home = initial.actorLocations().get(actor).supportingSurface();
        var body = atService ? port.serviceSurface() : SurfaceAnchor.at(home.x(), home.y(), home.z() + 1);
        var order = new MovementOrder(actor, actor, 0L, 10L, List.of(home), TraversalCapability.PEDESTRIAN,
                MovementOrder.ArrivalPolicy.EXACT_STATION);
        var movement = new ActorMovement(order, 23_900L,
                new ActorMovementContext.ServiceExit(settlement.id(), FrontierWorldState.depotId(settlement.id())),
                initial.actorExecutions().next(actor, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.SERVICE_EXIT, actor));
        SubjectId account = ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(settlement.id()));
        var resources = initial.inventory().fungibleResources().transformCold(account,
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(new SubjectId("lot:interruption-bread"), settlement.id(), ResidentMeal.BREAD_KIND,
                        64, "test", List.of()));
        return initial.withInventory(initial.inventory().withFungibleResources(resources)).withActorBody(actor, body.standingBody())
                .withChanges(FrontierWorldStateUpdate.begin().actorMovements(Map.of(actor, movement))
                        .actorExecutions(initial.actorExecutions().begin(movement.executionId(), 0L)));
    }
}
