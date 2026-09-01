package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        state = state.withActorBody(stranded, FrontierTestPositions.bodyAboveSupport(blockedFloor));

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
        assertEquals(EngineeringRecoveryTeam.MIN_MEMBERS, project.team().orElseThrow().memberIds().size(),
                "one narrow replacement cell admits its reachable two-person work front, not a fictitious four-body crowd");
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
        assertTrue(assemblyPlan.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(ScheduleEffect.Created.class::isInstance).map(ScheduleEffect.Created.class::cast)
                .anyMatch(created -> created.action().equals(RouteConstructionProcess.assemblyProgress(project.id(), 220L))),
                "assembly admission must schedule the crew's own human COLD cadence, not wait for the next construction scan");
        assertEquals(assemblyStarted, FrontierWorldRuntimeDefinition.payloadCodecs().decode(assemblyStarted.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(assemblyStarted)));
        state = RouteConstructionStateSupport.reduceAssemblyStarted(state, FrontierRouteNetwork.OWNER, assemblyStarted);
        assertFalse(state.routeConstructions().get(project.id()).assembly().orElseThrow().complete());
        RouteConstructionAssemblyAdvanced assemblyAdvanced = RouteConstructionProcess.planAssemblyProgress(state,
                        RouteConstructionProcess.assemblyProgress(project.id(), 320L)).stream()
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

    @Test
    void crewAssemblyProgressIsDurableAcrossRestartAndDoesNotWaitForTheNextStrategicScan() {
        WorldId world = new WorldId("frontier:route-construction-assembly-restart");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base =
                FrontierV3FixtureCatalog.engineeringEquipmentConfiguration(world, 41L);
        FrontierWorldState toolReady = issueAllFixtureTools(base.initialState());
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(
                base.worldId(), toolReady, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(), base.transactionCommitter(),
                base.stateValidator(), base.executionMetrics());
        var original = FrontierEngines.create(configuration);

        original.advanceTo(new SimInstant(200L), new WorkBudget(64, 512));
        FrontierWorldState admitted = state(original);
        RouteConstruction project = admitted.routeConstructions().values().stream().findFirst().orElseThrow();
        EngineeringWorkAssembly beforeRestart = project.assembly().orElseThrow();
        assertFalse(beforeRestart.complete());
        assertTrue(original.checkpoint().schedules().contains(RouteConstructionProcess.assemblyProgress(project.id(), 220L)),
                "assembly must retain its own due action before restart instead of relying on the 200-tick construction scan");

        var checkpoint = original.checkpoint();
        var recovered = FrontierEngines.recover(configuration, new RecoveryImage(world,
                Optional.of(new SnapshotRecord(checkpoint, checkpoint.revision().value())), List.of()));
        assertEquals(checkpoint.schedules(), recovered.checkpoint().schedules(),
                "recovery must retain the exact outstanding COLD crew step");

        recovered.advanceTo(new SimInstant(220L), new WorkBudget(64, 512));
        EngineeringWorkAssembly afterRecoveryStep = state(recovered).routeConstructions().get(project.id()).assembly().orElseThrow();
        assertTrue(afterRecoveryStep.members().values().stream().mapToInt(EngineeringWorkAssembly.Member::cursor).sum()
                        > beforeRestart.members().values().stream().mapToInt(EngineeringWorkAssembly.Member::cursor).sum(),
                "the recovered human cadence must advance a retained crew member before the next strategic scan at tick 400");
        assertTrue(recovered.checkpoint().schedules().contains(RouteConstructionProcess.assemblyProgress(project.id(), 240L)));

        for (long tick = 221L; tick <= 2_000L; tick++) {
            recovered.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
            if (state(recovered).routeConstructions().get(project.id()).assembly().orElseThrow().complete()) break;
        }
        assertTrue(state(recovered).routeConstructions().get(project.id()).assembly().orElseThrow().complete(),
                "the recurring human cadence must complete the bounded two-person approach rather than merely prove its first step");
    }

    @Test
    void engineeringAssemblyWaitsForPriorAmbientLeaseThenRetainsOneHotCursorThroughRelease() {
        FrontierWorldState state = issueAllFixtureTools(FrontierV3FixtureCatalog.engineeringEquipmentConfiguration(
                new WorldId("frontier:route-construction-hot-cursor"), 41L).initialState());
        RouteConstruction project = state.routeConstructions().values().stream().findFirst().orElseThrow();
        SubjectId member = project.team().orElseThrow().memberIds().getFirst();

        AmbientActorLease ordinary = AmbientActorProcess.nextLease(state, member, new SimInstant(0L));
        assertEquals(AmbientGoalKind.WORK, ordinary.goal(), "the predecessor is an ordinary ambient lease before an engineering assembly exists");
        state = AmbientLeaseStateProcess.transition(AmbientLeaseStateProcess.prepare(state, ordinary), member, AmbientLeaseStatus.HOT);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> blockedAdmission = RouteConstructionProcess.plan(state,
                RouteConstructionProcess.scan(1, 200L));
        assertFalse(blockedAdmission.stream().anyMatch(event -> event.payload() instanceof RouteConstructionAssemblyStarted),
                "COLD must not compile from an actor whose physical predecessor lease still owns its final floor");

        state = AmbientLeaseStateProcess.transition(state, member, AmbientLeaseStatus.DRAINING);
        state = AmbientLeaseStateProcess.release(state, new AmbientLeaseReleased(member, ordinary.handoffBody(), FixedScalar.whole(8)));
        RouteConstructionAssemblyStarted started = RouteConstructionProcess.plan(state, RouteConstructionProcess.scan(2, 400L)).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).filter(RouteConstructionAssemblyStarted.class::isInstance)
                .map(RouteConstructionAssemblyStarted.class::cast).findFirst().orElseThrow();
        state = RouteConstructionStateSupport.reduceAssemblyStarted(state, FrontierRouteNetwork.OWNER, started);

        AmbientActorLease engineering = AmbientActorProcess.nextLease(state, member, new SimInstant(400L));
        assertEquals(AmbientGoalKind.ENGINEERING_ASSEMBLY, engineering.goal());
        EngineeringWorkAssembly assembly = state.routeConstructions().get(project.id()).assembly().orElseThrow();
        EngineeringWorkAssembly.Member before = assembly.members().get(member);
        assertEquals(before.corridor().get(before.cursor() + 1), engineering.goalBody().supportingSurface().support());
        state = AmbientLeaseStateProcess.transition(AmbientLeaseStateProcess.prepare(state, engineering), member, AmbientLeaseStatus.HOT);

        EngineeringWorkAssembly advanced = assembly.advance(member);
        state = RouteConstructionStateSupport.reduceAssemblyAdvanced(state, FrontierRouteNetwork.OWNER,
                new RouteConstructionAssemblyAdvanced(project.id(), advanced));
        EngineeringWorkAssembly.Member after = state.routeConstructions().get(project.id()).assembly().orElseThrow().members().get(member);
        AmbientActorLease retargeted = state.ambientLeases().get(member);
        BlockPosition expectedGoal = after.arrived() ? after.currentPosition() : after.corridor().get(after.cursor() + 1);
        assertEquals(expectedGoal, retargeted.goalBody().supportingSurface().support(), "HOT arrival must advance and retarget the same retained COLD cursor");

        FrontierWorldState releaseState = AmbientLeaseStateProcess.transition(state, member, AmbientLeaseStatus.DRAINING);
        assertThrows(IllegalArgumentException.class, () -> AmbientLeaseStateProcess.release(releaseState,
                new AmbientLeaseReleased(member, FrontierTestPositions.bodyAboveSupport(before.currentPosition()), FixedScalar.whole(8))),
                "a HOT body may not silently return the assembly to a stale predecessor position");
        FrontierWorldState released = AmbientLeaseStateProcess.release(releaseState,
                new AmbientLeaseReleased(member, FrontierTestPositions.bodyAboveSupport(after.currentPosition()), FixedScalar.whole(8)));
        assertEquals(AmbientLeaseStatus.CLOSED, released.ambientLeases().get(member).status());
        assertEquals(after.currentPosition(), FrontierTestPositions.supportOf(released.actorLocations().get(member)));
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

    private static FrontierWorldState issueAllFixtureTools(FrontierWorldState state) {
        RouteConstruction project = state.routeConstructions().values().stream().findFirst().orElseThrow();
        ExactInventory inventory = state.inventory();
        for (SubjectId member : project.team().orElseThrow().memberIds()) {
            ExactItemStack tool = inventory.items().values().stream()
                    .filter(item -> item.economicOwnerId().equals(project.settlementId()))
                    .filter(item -> EngineeringToolCustody.isTool(item.itemKind()))
                    .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot)
                    .findFirst().orElseThrow();
            inventory = inventory.moveObservedItem(tool.id(), tool.custody(), new InventoryCustody.Actor(member));
        }
        return state.withInventory(inventory);
    }

    private static FrontierWorldState state(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine) {
        return new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
    }
}
