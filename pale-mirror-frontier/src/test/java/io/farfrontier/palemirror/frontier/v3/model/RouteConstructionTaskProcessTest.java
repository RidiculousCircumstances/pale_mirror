package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteConstructionTaskProcessTest {
    @Test
    void unreachableExactCrewBlocksConstructionAdmissionWithoutCreatingProject() {
        FrontierWorldState state = stateWithConfirmedPatrol();
        StrategicTask construction = constructionTask(state, StrategicTaskStatus.PENDING);
        RouteConstruction viable = RouteConstructionProcess.planStart(state, RouteConstructionProcess.start(construction, 100L)).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).filter(RouteConstructionStarted.class::isInstance)
                .map(RouteConstructionStarted.class::cast).map(RouteConstructionStarted::project).findFirst().orElseThrow();
        SubjectId stranded = viable.team().orElseThrow().memberIds().getFirst();
        BlockPosition blockedFloor = FrontierGrayboxPlan.currentBodyGeometry(state).stream().findFirst().orElseThrow().offset(0, -1, 0);
        state = state.withActorLocation(stranded, blockedFloor);

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> result = RouteConstructionProcess.planStart(state, RouteConstructionProcess.start(construction, 100L));

        assertTrue(result.stream().anyMatch(event -> event.payload() instanceof StrategicTaskTransition transition
                && transition.taskId().equals(construction.id()) && transition.status() == StrategicTaskStatus.BLOCKED));
        assertFalse(result.stream().anyMatch(event -> event.payload() instanceof RouteConstructionStarted),
                "a topology with no real COLD crew approach must not create an unreachable construction project");
    }

    @Test
    void confirmedPatrolStartsOneExactCrewProjectAndGatesLegacyPlacer() {
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
        SubjectId depot = FrontierWorldState.depotId(project.settlementId());
        state = state.withInventory(state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .withSurfaceStatus(FrontierRouteNetwork.MAINTENANCE_CONTAINER, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(FrontierRouteNetwork.MAINTENANCE_CONTAINER, ContainerSurfaceStatus.ACTIVE).store(new ExactItemStack(new SubjectId("item:route-construction"), FrontierRouteNetwork.OWNER,
                        "minecraft:gray_concrete", 1, new InventoryCustody.ContainerSlot(FrontierRouteNetwork.MAINTENANCE_CONTAINER, 0))));
        PhysicalIntentPrepared toolIssue = RouteConstructionProcess.plan(state, RouteConstructionProcess.scan(1, 200L)).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_ISSUE, toolIssue.intent().kind());
        assertEquals(project.id(), toolIssue.intent().subjectIds().getFirst());
        state = state.preparePhysicalIntent(toolIssue.intent());
        SubjectId issuedItem = toolIssue.intent().subjectIds().get(2), issuedResident = toolIssue.intent().subjectIds().get(1);
        ExactItemStack exactTool = state.inventory().items().get(issuedItem);
        state = EquipmentIssueStateSupport.complete(state, toolIssue.intent(), new EquipmentIssueObservation(
                new PhysicalObservationId("observation:engineering-tool-issue"), toolIssue.intent().id(), project.id(), issuedResident, issuedItem,
                (InventoryCustody.ContainerSlot) exactTool.custody()), new LinkedHashMap<>(state.physicalIntents()));
        assertEquals(new InventoryCustody.Actor(issuedResident), state.inventory().items().get(issuedItem).custody());
        assertEquals(state, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state)));
        for (SubjectId member : project.team().orElseThrow().memberIds()) {
            if (member.equals(issuedResident)) continue;
            ExactItemStack tool = state.inventory().items().values().stream().filter(item -> item.economicOwnerId().equals(project.settlementId())
                    && EngineeringToolCustody.isTool(item.itemKind()) && item.custody() instanceof InventoryCustody.ContainerSlot).findFirst().orElseThrow();
            state = state.withInventory(state.inventory().moveObservedItem(tool.id(), tool.custody(), new InventoryCustody.Actor(member)));
        }
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> assemblyPlan = RouteConstructionProcess.plan(state, RouteConstructionProcess.scan(1, 200L));
        assertTrue(assemblyPlan.stream()
                .noneMatch(event -> event.payload() instanceof PhysicalIntentPrepared),
                "a tool-ready exact crew cannot borrow the legacy autonomous block placer before HOT assembly exists");
        RouteConstructionAssemblyStarted assemblyStarted = assemblyPlan.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(RouteConstructionAssemblyStarted.class::isInstance).map(RouteConstructionAssemblyStarted.class::cast).findFirst().orElseThrow();
        assertEquals(assemblyStarted, FrontierWorldRuntimeDefinition.payloadCodecs().decode(assemblyStarted.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(assemblyStarted)));
        state = RouteConstructionStateSupport.reduceAssemblyStarted(state, FrontierRouteNetwork.OWNER, assemblyStarted);
        assertFalse(state.routeConstructions().get(project.id()).assembly().orElseThrow().complete());
        RouteConstructionAssemblyAdvanced assemblyAdvanced = RouteConstructionProcess.plan(state, RouteConstructionProcess.scan(2, 300L)).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).filter(RouteConstructionAssemblyAdvanced.class::isInstance)
                .map(RouteConstructionAssemblyAdvanced.class::cast).findFirst().orElseThrow();
        assertEquals(assemblyAdvanced, FrontierWorldRuntimeDefinition.payloadCodecs().decode(assemblyAdvanced.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(assemblyAdvanced)));
        state = RouteConstructionStateSupport.reduceAssemblyAdvanced(state, FrontierRouteNetwork.OWNER, assemblyAdvanced);
        assertEquals(state, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state)));

        RouteConstruction activeProject = state.routeConstructions().get(project.id());
        int requiredCells = FrontierRouteNetwork.constructionCells(state.bootstrap(), state.routeTopology(), activeProject.settlementId(), activeProject.waypoints()).size();
        RouteConstruction ready = activeProject.withConfirmedCells(requiredCells, RouteConstructionStatus.READY);
        LinkedHashMap<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions());
        projects.put(project.id(), ready);
        state = state.withChanges(FrontierWorldStateUpdate.begin().routeConstructions(projects));
        PhysicalIntentPrepared toolReturn = RouteConstructionProcess.plan(state, RouteConstructionProcess.scan(2, 300L)).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_RETURN, toolReturn.intent().kind());
        state = state.preparePhysicalIntent(toolReturn.intent());
        SubjectId returnedItem = toolReturn.intent().subjectIds().get(2), returnedResident = toolReturn.intent().subjectIds().get(1);
        InventoryCustody.ContainerSlot returnSlot = new InventoryCustody.ContainerSlot(toolReturn.intent().targetSlot().orElseThrow().containerId(),
                toolReturn.intent().targetSlot().orElseThrow().slot());
        state = EquipmentReturnStateSupport.complete(state, toolReturn.intent(), new EquipmentReturnObservation(
                new PhysicalObservationId("observation:engineering-tool-return"), toolReturn.intent().id(), project.id(), returnedResident, returnedItem, returnSlot),
                new LinkedHashMap<>(state.physicalIntents()));
        assertEquals(returnSlot, state.inventory().items().get(returnedItem).custody());
        assertEquals(state, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state)));
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
