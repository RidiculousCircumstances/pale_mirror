package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteConstructionTaskProcessTest {
    @Test
    void confirmedPatrolStartsOneConstructionTaskAndUnknownEffectBlocksIt() {
        FrontierWorldState state = stateWithConfirmedPatrol();
        StrategicTask construction = constructionTask(state, StrategicTaskStatus.PENDING);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> started = RouteConstructionProcess.planStart(state, RouteConstructionProcess.start(construction, 100L));
        assertTrue(started.stream().anyMatch(event -> event.payload() instanceof RouteConstructionStarted));
        assertTrue(started.stream().anyMatch(event -> event.payload() instanceof StrategicTaskTransition transition
                && transition.taskId().equals(construction.id()) && transition.status() == StrategicTaskStatus.ACTIVE));

        RouteConstruction project = started.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).filter(RouteConstructionStarted.class::isInstance)
                .map(RouteConstructionStarted.class::cast).map(RouteConstructionStarted::project).findFirst().orElseThrow();
        assertTrue(project.team().isPresent(), "new construction must retain an exact engineering crew instead of an autonomous builder");
        state = state.withStrategicPlans(state.strategicPlans().transitionTask(construction.id(), StrategicTaskStatus.ACTIVE));
        state = RouteConstructionStateSupport.reduceStarted(state, FrontierRouteNetwork.OWNER, new RouteConstructionStarted(project));
        state = state.withInventory(state.inventory().withSurfaceStatus(FrontierRouteNetwork.MAINTENANCE_CONTAINER, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(FrontierRouteNetwork.MAINTENANCE_CONTAINER, ContainerSurfaceStatus.ACTIVE).store(new ExactItemStack(new SubjectId("item:route-construction"), FrontierRouteNetwork.OWNER,
                        "minecraft:gray_concrete", 1, new InventoryCustody.ContainerSlot(FrontierRouteNetwork.MAINTENANCE_CONTAINER, 0))));
        PhysicalIntentPrepared prepared = RouteConstructionProcess.plan(state, RouteConstructionProcess.scan(1, 200L)).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING, prepared.intent().kind());
        state = state.preparePhysicalIntent(prepared.intent());
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> unknown = RouteConstructionProcess.planMaterialLoadingTransition(state, prepared.intent(),
                new PhysicalIntentTransition(prepared.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty()));
        assertTrue(unknown.stream().anyMatch(event -> event.payload() instanceof StrategicTaskTransition transition
                && transition.taskId().equals(construction.id()) && transition.status() == StrategicTaskStatus.BLOCKED));
    }

    private static FrontierWorldState stateWithConfirmedPatrol() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-construction-task"), 91L);
        FrontierWorldState state = FrontierWorldState.initial(bootstrap); Settlement settlement = bootstrap.settlements().getFirst();
        List<BlockPosition> route = state.routeTopology().supplyWaypoints(bootstrap, settlement.id()); BlockPosition obstruction = route.get(1);
        state = state.recordPhysicalDelta(new PhysicalDelta(obstruction, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(FrontierRouteNetwork.OWNER),
                Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));
        Resident guard = settlement.residents().stream().filter(resident -> resident.role() == ResidentRole.GUARD).findFirst().orElseThrow();
        StrategicObjective patrolObjective = new StrategicObjective(new SubjectId("objective:patrol"), settlement.id(), StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE,
                Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask patrolTask = new StrategicTask(new SubjectId("task:patrol"), patrolObjective.id(), settlement.id(), StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE,
                Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_GUARD), List.of(), StrategicTaskStatus.PENDING);
        StrategicPlanState plans = StrategicPlanState.empty().addObjective(patrolObjective).addTask(patrolTask).transitionTask(patrolTask.id(), StrategicTaskStatus.ACTIVE);
        RoutePatrol patrol = new RoutePatrol(patrolTask.id(), settlement.id(), guard.id(), route, 1, RoutePatrolStatus.OBSTRUCTION_CONFIRMED, Optional.of(obstruction));
        plans = plans.startPatrol(patrol).transitionTask(patrolTask.id(), StrategicTaskStatus.COMPLETED);
        StrategicObjective constructionObjective = new StrategicObjective(new SubjectId("objective:construction"), settlement.id(), StrategicObjectiveKind.SETTLEMENT_CONSTRUCT_ROUTE_BYPASS,
                Optional.empty(), 2, StrategicObjectiveStatus.ACTIVE);
        StrategicTask construction = new StrategicTask(new SubjectId("task:construction"), constructionObjective.id(), settlement.id(), StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS,
                Optional.empty(), List.of(StrategicTaskRequirement.CONFIRMED_ROUTE_OBSTRUCTION, StrategicTaskRequirement.EXACT_ROUTE_CONSTRUCTION_MATERIAL),
                List.of(patrolTask.id()), StrategicTaskStatus.PENDING);
        return state.withStrategicPlans(plans.addObjective(constructionObjective).addTask(construction));
    }
    private static StrategicTask constructionTask(FrontierWorldState state, StrategicTaskStatus status) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.kind() == StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS && task.status() == status).findFirst().orElseThrow();
    }
}
