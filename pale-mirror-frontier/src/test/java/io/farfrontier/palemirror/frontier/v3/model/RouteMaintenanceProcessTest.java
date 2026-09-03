package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.process.RouteMaintenanceProcess;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.process.EngineeringEquipmentProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure admission regressions for the distinct in-place route-maintenance owner. */
class RouteMaintenanceProcessTest {
    @Test
    void runningMaterialPickupRecoversOnlyFromItsExactObservedSourcePostcondition() {
        FrontierWorldState initial = FrontierV3FixtureCatalog.routeMaintenanceColdSourceFairnessConfiguration(
                new WorldId("frontier:route-maintenance-running-material"), 41L).initialState();
        RouteMaintenance maintenance = initial.routeMaintenances().get(new SubjectId("maintenance:route--380-64--304"));
        PhysicalIntent prepared = initial.physicalIntents().values().stream().filter(intent -> intent.causeSubjectId().equals(maintenance.id()))
                .filter(intent -> intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING)
                .findFirst().orElseThrow();
        FrontierWorldState running = initial.transitionPhysicalIntent(prepared.id(), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING, Optional.empty());
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(running));
        PhysicalIntent recoveredIntent = recovered.physicalIntents().get(prepared.id());
        RouteMaintenanceMaterialLoadObservation observation = new RouteMaintenanceMaterialLoadObservation(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:running-maintenance-material"), recoveredIntent.id(), maintenance.id(),
                maintenance.plannedCargoId(), prepared.subjectIds().get(4), maintenance.plannedCargoItemId(), 0);

        RouteMaintenanceMaterialLoadObservation forgedSource = new RouteMaintenanceMaterialLoadObservation(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:running-maintenance-forged-source"), recoveredIntent.id(), maintenance.id(),
                maintenance.plannedCargoId(), new SubjectId("item:foreign-source"), maintenance.plannedCargoItemId(), 0);
        assertThrows(IllegalArgumentException.class, () -> RouteMaintenanceStateSupport.confirm(recovered, recoveredIntent, forgedSource,
                new java.util.LinkedHashMap<>(recovered.physicalIntents())), "a recovered source receipt cannot adopt another chest stack");

        FrontierWorldState confirmed = RouteMaintenanceStateSupport.confirm(recovered, recoveredIntent, observation,
                new java.util.LinkedHashMap<>(recovered.physicalIntents()));
        FrontierWorldState loaded = RouteMaintenanceStateSupport.reduceMaterialLoaded(confirmed, FrontierRouteNetwork.OWNER,
                new RouteMaintenanceMaterialLoaded(maintenance.id(), new CargoBatch(maintenance.plannedCargoId(), FrontierRouteNetwork.OWNER,
                        List.of(maintenance.plannedCargoItemId()))));
        assertEquals(maintenance.plannedCargoId(), loaded.routeMaintenances().get(maintenance.id()).cargoId().orElseThrow());
        assertFalse(loaded.inventory().items().containsKey(prepared.subjectIds().get(4)),
                "a recovered confirmed pickup consumes only the observed one-unit source");
        assertEquals(new InventoryCustody.Cargo(maintenance.plannedCargoId()), loaded.inventory().items().get(maintenance.plannedCargoItemId()).custody());

        FrontierWorldState conflicted = RouteMaintenanceStateSupport.conflict(recovered, recoveredIntent,
                new java.util.LinkedHashMap<>(recovered.physicalIntents()));
        assertEquals(RouteMaintenanceStatus.CONFLICT, conflicted.routeMaintenances().get(maintenance.id()).status());
        assertEquals(1, conflicted.inventory().items().get(prepared.subjectIds().get(4)).count(),
                "a missing or altered recovered source never fabricates the cargo transfer");
        assertTrue(conflicted.routeMaintenances().get(maintenance.id()).cargoId().isEmpty());
    }

    @Test
    void runningRepairRecoversOnlyFromItsExactObservedTargetPostcondition() {
        FrontierWorldState initial = FrontierV3FixtureCatalog.routeMaintenanceColdSourceFairnessConfiguration(
                new WorldId("frontier:route-maintenance-running-repair"), 41L).initialState();
        RouteMaintenance maintenance = initial.routeMaintenances().get(new SubjectId("maintenance:route--140-64--304"));
        PhysicalIntent prepared = RouteMaintenanceProcess.workIntent(maintenance, maintenance.plannedCargoId(), maintenance.plannedCargoItemId());
        FrontierWorldState running = RouteMaintenanceProcess.reducePrepared(initial, FrontierRouteNetwork.OWNER, prepared)
                .transitionPhysicalIntent(prepared.id(), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING, Optional.empty());
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(running));
        PhysicalIntent recoveredIntent = recovered.physicalIntents().get(prepared.id());
        RouteMaintenanceObservation observation = new RouteMaintenanceObservation(
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId("observation:running-maintenance-repair"), recoveredIntent.id(), maintenance.id(),
                maintenance.plannedCargoItemId(), maintenance.repairCell());

        FrontierWorldState confirmed = RouteMaintenanceStateSupport.complete(recovered, recoveredIntent, observation,
                new java.util.LinkedHashMap<>(recovered.physicalIntents()));
        assertEquals(RouteMaintenanceStatus.READY, confirmed.routeMaintenances().get(maintenance.id()).status());
        assertFalse(confirmed.physicalDeltas().containsKey(maintenance.repairCell()),
                "only the matching recovered receipt may clear its exact route loss");
        assertTrue(confirmed.physicalDeltas().containsKey(new BlockPosition(-380, 64, -304)),
                "one repair receipt never clears a different retained route loss");
        assertFalse(confirmed.inventory().cargo().containsKey(maintenance.plannedCargoId()),
                "the confirmed repair consumes its one exact cargo unit once");

        FrontierWorldState conflicted = RouteMaintenanceStateSupport.conflict(recovered, recoveredIntent,
                new java.util.LinkedHashMap<>(recovered.physicalIntents()));
        assertEquals(RouteMaintenanceStatus.CONFLICT, conflicted.routeMaintenances().get(maintenance.id()).status());
        assertTrue(conflicted.physicalDeltas().containsKey(maintenance.repairCell()),
                "a missing or altered recovered target keeps the original route loss visible");
        assertTrue(conflicted.inventory().cargo().containsKey(maintenance.plannedCargoId()),
                "a missing or altered recovered target does not consume its cargo");
    }

    @Test
    void independentRouteLossesRetainDistinctTeamsAndAdvanceInDeterministicRotation() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-maintenance-parallel"), 41L);
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        SubjectId firstSettlement = bootstrap.settlements().get(0).id();
        SubjectId secondSettlement = bootstrap.settlements().get(1).id();
        List<BlockPosition> firstRoute = initial.routeTopology().supplyWaypoints(bootstrap, firstSettlement);
        List<BlockPosition> secondRoute = initial.routeTopology().supplyWaypoints(bootstrap, secondSettlement);
        BlockPosition firstLoss = firstRoute.stream().filter(position -> !secondRoute.contains(position)).findFirst().orElseThrow();
        BlockPosition secondLoss = secondRoute.stream().filter(position -> !firstRoute.contains(position)).findFirst().orElseThrow();
        FrontierWorldState damaged = initial.recordPhysicalDelta(new PhysicalDelta(firstLoss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:first"))
                .recordPhysicalDelta(new PhysicalDelta(secondLoss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                        Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:second"));

        RouteMaintenanceStarted first = started(RouteMaintenanceProcess.plan(damaged, RouteMaintenanceProcess.scan(1, 100L)));
        FrontierWorldState oneRetained = RouteMaintenanceStateSupport.reduceStarted(damaged, FrontierRouteNetwork.OWNER, first);
        RouteMaintenanceStarted second = started(RouteMaintenanceProcess.plan(oneRetained, RouteMaintenanceProcess.scan(2, 200L)));
        assertFalse(first.maintenance().id().equals(second.maintenance().id()), "one retained COLD repair must not suppress a distinct observed loss");
        assertTrue(Set.copyOf(first.maintenance().team().memberIds()).stream().noneMatch(second.maintenance().team().memberIds()::contains),
                "parallel repairs retain disjoint exact people rather than cloning a crew");

        FrontierWorldState retained = RouteMaintenanceStateSupport.reduceStarted(oneRetained, FrontierRouteNetwork.OWNER, second);
        List<SubjectId> ordered = retained.routeMaintenances().keySet().stream().sorted().toList();
        RouteMaintenanceAssemblyStarted firstTurn = RouteMaintenanceProcess.plan(retained, RouteMaintenanceProcess.scan(3, 300L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceAssemblyStarted.class::isInstance).map(RouteMaintenanceAssemblyStarted.class::cast)
                .findFirst().orElseThrow();
        RouteMaintenanceAssemblyStarted secondTurn = RouteMaintenanceProcess.plan(retained, RouteMaintenanceProcess.scan(4, 400L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceAssemblyStarted.class::isInstance).map(RouteMaintenanceAssemblyStarted.class::cast)
                .findFirst().orElseThrow();
        assertEquals(ordered.get(0), firstTurn.maintenanceId());
        assertEquals(ordered.get(1), secondTurn.maintenanceId(), "a blocked earlier owner cannot monopolize every strategic scan");

        SubjectId sourceId = new SubjectId("item:parallel-maintenance-source");
        SubjectId maintenanceContainer = FrontierRouteNetwork.MAINTENANCE_CONTAINER;
        int sourceSlot = retained.inventory().firstFreeSlot(maintenanceContainer).orElseThrow();
        ExactInventory suppliedInventory = retained.inventory().withSurfaceStatus(maintenanceContainer, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(maintenanceContainer, ContainerSurfaceStatus.ACTIVE).store(new ExactItemStack(sourceId, FrontierRouteNetwork.OWNER,
                        "minecraft:gray_concrete", 2, new InventoryCustody.ContainerSlot(maintenanceContainer, sourceSlot)));
        FrontierWorldState supplied = retained.withInventory(suppliedInventory);
        RouteMaintenance firstMaintenance = supplied.routeMaintenances().get(ordered.get(0));
        RouteMaintenance secondMaintenance = supplied.routeMaintenances().get(ordered.get(1));
        PhysicalIntent firstPickup = RouteMaintenanceProcess.materialLoadingIntent(supplied, firstMaintenance, suppliedInventory.items().get(sourceId));
        FrontierWorldState reserved = RouteMaintenanceProcess.reducePrepared(supplied, FrontierRouteNetwork.OWNER, firstPickup);
        PhysicalIntent duplicateSourcePickup = RouteMaintenanceProcess.materialLoadingIntent(reserved, secondMaintenance, reserved.inventory().items().get(sourceId));
        assertThrows(IllegalArgumentException.class, () -> RouteMaintenanceProcess.reducePrepared(reserved, FrontierRouteNetwork.OWNER, duplicateSourcePickup),
                "a shared physical chest may serialize exact source stacks, but a second repair may not reserve the same stack optimistically");
    }

    @Test
    void knownRouteLossAdmitsOneExactLocalMaintenanceOwnerWithoutCreatingABypass() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-maintenance-admission"), 41L);
        FrontierWorldState state = FrontierWorldState.initial(bootstrap);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        BlockPosition loss = state.routeTopology().supplyWaypoints(bootstrap, settlement).get(2);
        state = state.recordPhysicalDelta(new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));

        RouteMaintenanceStarted started = RouteMaintenanceProcess.plan(state, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .findFirst().orElseThrow();
        assertEquals(loss, started.maintenance().repairCell());
        assertEquals(settlement, started.maintenance().settlementId());
        assertEquals(GrayboxSemanticPart.ROUTE_SURFACE, started.maintenance().semanticPart());
        assertFalse(started.maintenance().team().memberIds().isEmpty());

        FrontierWorldState admitted = RouteMaintenanceStateSupport.reduceStarted(state, FrontierRouteNetwork.OWNER, started);
        assertEquals(1, admitted.routeMaintenances().size());
        assertTrue(admitted.routeConstructions().isEmpty(), "one lost cell is in-place maintenance, never a hidden bypass project");
        assertTrue(admitted.physicalDeltas().containsKey(loss), "admission retains the original loss until an observed repair receipt");
        assertEquals(state.routeTopology().supplyWaypoints(bootstrap, settlement), admitted.routeTopology().supplyWaypoints(bootstrap, settlement));
    }

    @Test
    void nonRouteOrUnknownLossCannotBecomeAControlledRouteRepair() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-maintenance-foreign"), 41L);
        FrontierWorldState state = FrontierWorldState.initial(bootstrap);
        GrayboxCell nonRouteCell = FrontierGrayboxPlan.compile(state).cells().values().stream()
                .filter(cell -> !cell.ownerId().equals(FrontierRouteNetwork.OWNER))
                .filter(cell -> cell.semanticPart() == GrayboxSemanticPart.FOUNDATION).findFirst().orElseThrow();
        BlockPosition ordinaryRouteCell = state.routeTopology().supplyWaypoints(bootstrap, bootstrap.settlements().getFirst().id()).get(2);
        FrontierWorldState foreign = state.recordPhysicalDelta(new PhysicalDelta(nonRouteCell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(nonRouteCell.ownerId()), Optional.of(nonRouteCell.semanticPart()), "player:test"));
        FrontierWorldState unknown = state.recordPhysicalDelta(new PhysicalDelta(ordinaryRouteCell, PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "player:test"));

        assertTrue(RouteMaintenanceProcess.plan(foreign, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .noneMatch(event -> event.payload() instanceof RouteMaintenanceStarted));
        assertTrue(RouteMaintenanceProcess.plan(unknown, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .noneMatch(event -> event.payload() instanceof RouteMaintenanceStarted));
    }

    @Test
    void terminalReadyMaintenanceCompactsItsBoundedOwnerInsteadOfRetainingItForever() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-maintenance-close"), 41L);
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        BlockPosition loss = initial.routeTopology().supplyWaypoints(bootstrap, settlement).get(2);
        FrontierWorldState damaged = initial.recordPhysicalDelta(new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));
        RouteMaintenanceStarted started = RouteMaintenanceProcess.plan(damaged, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .findFirst().orElseThrow();
        FrontierWorldState admitted = RouteMaintenanceStateSupport.reduceStarted(damaged, FrontierRouteNetwork.OWNER, started);
        RouteMaintenance completed = started.maintenance().ready();
        FrontierWorldState ready = admitted.withChanges(FrontierWorldStateUpdate.begin()
                .routeMaintenances(Map.of(completed.id(), completed)).physicalDeltas(Map.of()));

        RouteMaintenanceClosed closed = RouteMaintenanceProcess.plan(ready, RouteMaintenanceProcess.scan(2, 200L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceClosed.class::isInstance).map(RouteMaintenanceClosed.class::cast)
                .findFirst().orElseThrow();
        ProposedEvent closedEvent = new ProposedEvent(FrontierRouteNetwork.OWNER, closed);
        assertEquals(List.of(closedEvent), FrontierWorldRuntimeDefinition.processRegistry().validateEmissions("infrastructure", List.of(closedEvent)),
                "the infrastructure contract must admit the terminal maintenance compaction it plans");
        FrontierWorldState compacted = RouteMaintenanceStateSupport.reduceClosed(ready, FrontierRouteNetwork.OWNER, closed);

        assertTrue(compacted.routeMaintenances().isEmpty());
        assertTrue(compacted.physicalIntents().isEmpty());
        assertTrue(compacted.physicalObservations().isEmpty());
    }

    @Test
    void terminalToolReturnUsesOneSharedExactReadinessBoundary() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-maintenance-return-readiness"), 41L);
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        BlockPosition loss = initial.routeTopology().supplyWaypoints(bootstrap, settlement).get(2);
        FrontierWorldState damaged = initial.recordPhysicalDelta(new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));
        RouteMaintenance maintenance = RouteMaintenanceProcess.plan(damaged, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .findFirst().orElseThrow().maintenance();
        FrontierWorldState admitted = RouteMaintenanceStateSupport.reduceStarted(damaged, FrontierRouteNetwork.OWNER,
                new RouteMaintenanceStarted(maintenance));
        RouteMaintenance ready = maintenance.ready();
        FrontierWorldState terminalWithoutTool = admitted.withChanges(FrontierWorldStateUpdate.begin()
                .routeMaintenances(Map.of(ready.id(), ready)).physicalDeltas(Map.of()));

        assertEquals("DEPOT_SURFACE_INACTIVE", EngineeringEquipmentProcess.returnReadiness(terminalWithoutTool, ready).reason());
        assertTrue(EngineeringEquipmentProcess.returnOne(terminalWithoutTool, ready).isEmpty(),
                "a terminal maintenance must not fabricate a return intent before its exact depot surface is observed");

        SubjectId engineer = ready.team().memberIds().getFirst();
        SubjectId depot = FrontierWorldState.depotId(settlement);
        FrontierWorldState activeDepot = terminalWithoutTool.withInventory(terminalWithoutTool.inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE));
        assertEquals("NO_EXACT_TEAM_TOOL", EngineeringEquipmentProcess.returnReadiness(activeDepot, ready).reason());
        ExactItemStack tool = activeDepot.inventory().items().values().stream()
                .filter(item -> item.economicOwnerId().equals(settlement) && EngineeringToolCustody.isTool(item.itemKind())).findFirst().orElseThrow();
        FrontierWorldState terminalWithTool = activeDepot.withInventory(activeDepot.inventory()
                .moveObservedItem(tool.id(), tool.custody(), new InventoryCustody.Actor(engineer)));
        assertEquals("TOOL_HOLDER_NOT_AT_DEPOT_SERVICE_PORT", EngineeringEquipmentProcess.returnReadiness(terminalWithTool, ready).reason());
        SettlementDepotServicePort port = EngineeringDepotService.port(terminalWithTool, ready);
        java.util.Map<SubjectId, EngineeringWorkAssembly.Member> members = new java.util.LinkedHashMap<>();
        java.util.Map<SubjectId, ActorLocation> positions = new java.util.LinkedHashMap<>(terminalWithTool.actorLocations());
        for (int index = 0; index < ready.team().memberIds().size(); index++) {
            SubjectId member = ready.team().memberIds().get(index); BlockPosition station = port.stations().get(index).support();
            members.put(member, new EngineeringWorkAssembly.Member(List.of(station), 0));
            positions.put(member, new ActorLocation(BodyPosition.above(new SurfaceAnchor(station)), positions.get(member).condition()));
        }
        RouteMaintenance returned = ready.withAssembly(new EngineeringWorkAssembly(EngineeringJourneyPurpose.RETURN_DEPOT, members));
        FrontierWorldState atServicePort = terminalWithTool.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(positions).routeMaintenances(Map.of(returned.id(), returned)));
        var readiness = EngineeringEquipmentProcess.returnReadiness(atServicePort, returned);

        assertEquals("READY", readiness.reason());
        PhysicalIntent intent = EngineeringEquipmentProcess.returnOne(atServicePort, returned).orElseThrow();
        assertEquals(List.of(returned.id(), engineer, tool.id()), intent.subjectIds());
        assertEquals(readiness.targetSlot().orElseThrow(), intent.targetSlot().orElseThrow(),
                "planner and diagnostic readiness must retain the same exact return destination");
    }

    @Test
    void readyToolHolderImmediatelyStartsOneDurableReturnJourneyRatherThanAwaitingTheNextGlobalScan() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-maintenance-reactive-return"), 41L);
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        BlockPosition loss = initial.routeTopology().supplyWaypoints(bootstrap, settlement).get(2);
        FrontierWorldState damaged = initial.recordPhysicalDelta(new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));
        RouteMaintenance maintenance = RouteMaintenanceProcess.plan(damaged, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .findFirst().orElseThrow().maintenance().ready();
        SubjectId engineer = maintenance.team().memberIds().getFirst();
        SubjectId depot = FrontierWorldState.depotId(settlement);
        ExactItemStack tool = damaged.inventory().items().values().stream().filter(item -> item.economicOwnerId().equals(settlement))
                .filter(item -> EngineeringToolCustody.isTool(item.itemKind())).findFirst().orElseThrow();
        FrontierWorldState ready = RouteMaintenanceStateSupport.reduceStarted(damaged, FrontierRouteNetwork.OWNER,
                        new RouteMaintenanceStarted(maintenance))
                .withInventory(damaged.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                        .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE).moveObservedItem(tool.id(), tool.custody(), new InventoryCustody.Actor(engineer)))
                .withChanges(FrontierWorldStateUpdate.begin().routeMaintenances(Map.of(maintenance.id(), maintenance)).physicalDeltas(Map.of()));

        var events = RouteMaintenanceProcess.planReturnProgress(ready, RouteMaintenanceProcess.returnProgress(maintenance, 201L));

        RouteMaintenanceAssemblyStarted started = events.stream().map(ProposedEvent::payload)
                .filter(RouteMaintenanceAssemblyStarted.class::isInstance).map(RouteMaintenanceAssemblyStarted.class::cast).findFirst().orElseThrow();
        assertEquals(EngineeringJourneyPurpose.RETURN_DEPOT, started.assembly().purpose());
        assertTrue(events.stream().map(ProposedEvent::payload).filter(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class::isInstance)
                        .map(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class::cast)
                        .anyMatch(effect -> effect.action().equals(RouteMaintenanceProcess.assemblyProgress(maintenance.id(), 221L))),
                "the retained return journey, not a duplicate owner retry, must own its next step");
        assertTrue(events.stream().map(ProposedEvent::payload).filter(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class::isInstance)
                        .map(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class::cast)
                        .noneMatch(effect -> effect.action().kind().equals("frontier.route_maintenance.return_progress")),
                "a retained assembly must not leave a parallel periodic continuation behind");
    }

    @Test
    void completedDepotMusterSchedulesImmediateToolIssueWithoutAwaitingTheGlobalMaintenanceScan() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-maintenance-reactive-muster"), 41L);
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        BlockPosition loss = initial.routeTopology().supplyWaypoints(bootstrap, settlement).get(2);
        FrontierWorldState damaged = initial.recordPhysicalDelta(new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));
        RouteMaintenanceStarted started = RouteMaintenanceProcess.plan(damaged, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .findFirst().orElseThrow();
        RouteMaintenance maintenance = started.maintenance();
        SubjectId depot = FrontierWorldState.depotId(settlement);
        FrontierWorldState admitted = RouteMaintenanceStateSupport.reduceStarted(damaged, FrontierRouteNetwork.OWNER, started)
                .withInventory(damaged.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                        .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE));
        RouteMaintenanceAssemblyStarted musterStarted = RouteMaintenanceProcess.plan(admitted, RouteMaintenanceProcess.scan(2, 200L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceAssemblyStarted.class::isInstance).map(RouteMaintenanceAssemblyStarted.class::cast)
                .findFirst().orElseThrow();
        assertEquals(EngineeringJourneyPurpose.MUSTER_DEPOT, musterStarted.assembly().purpose());
        FrontierWorldState travelling = RouteMaintenanceStateSupport.reduceAssemblyStarted(admitted, FrontierRouteNetwork.OWNER, musterStarted);
        RouteMaintenance current = travelling.routeMaintenances().get(maintenance.id());
        while (true) {
            EngineeringWorkAssembly assembly = current.assembly().orElseThrow();
            SubjectId advancing = assembly.nextSafeAdvance().orElseThrow();
            EngineeringWorkAssembly advanced = assembly.advance(advancing);
            if (advanced.complete()) break;
            travelling = RouteMaintenanceStateSupport.reduceAssemblyAdvanced(travelling, FrontierRouteNetwork.OWNER,
                    new RouteMaintenanceAssemblyAdvanced(current.id(), advanced));
            current = travelling.routeMaintenances().get(maintenance.id());
        }
        var completion = RouteMaintenanceProcess.planAssemblyProgress(travelling,
                RouteMaintenanceProcess.assemblyProgress(maintenance.id(), 300L));
        RouteMaintenanceAssemblyAdvanced advanced = completion.stream().map(ProposedEvent::payload)
                .filter(RouteMaintenanceAssemblyAdvanced.class::isInstance).map(RouteMaintenanceAssemblyAdvanced.class::cast).findFirst().orElseThrow();
        assertTrue(completion.stream().map(ProposedEvent::payload).filter(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class::cast)
                .anyMatch(effect -> effect.action().equals(RouteMaintenanceProcess.progress(maintenance, 301L))),
                "the final local depot arrival must retain a direct continuation instead of waiting for the strategic scan");
        FrontierWorldState mustered = RouteMaintenanceStateSupport.reduceAssemblyAdvanced(travelling, FrontierRouteNetwork.OWNER, advanced);
        var continuation = RouteMaintenanceProcess.planProgress(mustered, RouteMaintenanceProcess.progress(maintenance, 301L));
        assertTrue(continuation.stream().map(ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).anyMatch(intent -> intent.intent().kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_ISSUE),
                "the exact engineer and exact depot tool must become actionable on the local cadence");
    }

    @Test
    void equippedExactCrewAdmitsOneBoundedAssemblyBeforeAnyRouteMaterialIsTaken() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-maintenance-assembly"), 41L);
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        BlockPosition loss = initial.routeTopology().supplyWaypoints(bootstrap, settlement).get(2);
        FrontierWorldState damaged = initial.recordPhysicalDelta(new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));
        RouteMaintenanceStarted started = RouteMaintenanceProcess.plan(damaged, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .findFirst().orElseThrow();
        SubjectId engineer = started.maintenance().team().memberIds().getFirst();
        ExactItemStack tool = damaged.inventory().items().values().stream()
                .filter(item -> EngineeringToolCustody.isTool(item.itemKind()))
                .filter(item -> item.economicOwnerId().equals(settlement)).findFirst().orElseThrow();
        FrontierWorldState equipped = RouteMaintenanceStateSupport.reduceStarted(damaged, FrontierRouteNetwork.OWNER, started)
                .withInventory(damaged.inventory().moveObservedItem(tool.id(), tool.custody(), new InventoryCustody.Actor(engineer)));

        var readiness = EngineeringWorksite.admission(equipped, started.maintenance());
        assertEquals("READY", readiness.reason(), readiness.detail());
        EngineeringWorkAssembly assembly = EngineeringWorksite.compile(equipped, started.maintenance());
        FrontierWorldState assembled = RouteMaintenanceStateSupport.reduceAssemblyStarted(equipped, FrontierRouteNetwork.OWNER,
                new RouteMaintenanceAssemblyStarted(started.maintenance().id(), assembly));
        assertEquals(AmbientGoalKind.ENGINEERING_ASSEMBLY, AmbientActorProcess.goalFor(assembled, engineer).kind(),
                "a restart/re-entry must recover the retained maintenance assembly rather than inventing ordinary work");
        assertTrue(RouteMaintenanceProcess.plan(equipped, RouteMaintenanceProcess.scan(2, 200L)).stream()
                .map(ProposedEvent::payload).anyMatch(RouteMaintenanceAssemblyStarted.class::isInstance),
                "an equipped local engineer must retain one bounded COLD approach before drawing route material");
    }

    @Test
    void drainedAmbientEngineerRetainsADeclaredApproachToTheSameRouteRepair() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-maintenance-drained-engineer"), 41L);
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        BlockPosition loss = new BlockPosition(-380, 64, -304);
        FrontierWorldState damaged = initial.recordPhysicalDelta(new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));
        RouteMaintenanceStarted started = RouteMaintenanceProcess.plan(damaged, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .findFirst().orElseThrow();
        SubjectId engineer = started.maintenance().team().memberIds().getFirst();
        ExactItemStack tool = damaged.inventory().items().values().stream()
                .filter(item -> EngineeringToolCustody.isTool(item.itemKind()))
                .filter(item -> item.economicOwnerId().equals(settlement)).findFirst().orElseThrow();
        FrontierWorldState equipped = RouteMaintenanceStateSupport.reduceStarted(damaged, FrontierRouteNetwork.OWNER, started)
                .withInventory(damaged.inventory().moveObservedItem(tool.id(), tool.custody(), new InventoryCustody.Actor(engineer)))
                .withActorBody(engineer, new BodyPosition(-365, 64, -347));

        var readiness = EngineeringWorksite.admission(equipped, started.maintenance());
        assertEquals("READY", readiness.reason(), readiness.detail() + "; " +
                "a resident drained at a declared settlement surface must remain able to approach the retained repair");
    }

    @Test
    void engineeringAssemblyAllowsOneSurveyedGradeStepButRejectsACliff() {
        SubjectId engineer = new SubjectId("resident:grade-engineer");
        assertDoesNotThrow(() -> new EngineeringWorkAssembly(EngineeringJourneyPurpose.WORKSITE, Map.of(engineer, new EngineeringWorkAssembly.Member(List.of(
                new BlockPosition(0, 63, 0), new BlockPosition(1, 64, 0)), 0))));
        assertThrows(IllegalArgumentException.class, () -> new EngineeringWorkAssembly(EngineeringJourneyPurpose.WORKSITE, Map.of(engineer, new EngineeringWorkAssembly.Member(List.of(
                new BlockPosition(0, 63, 0), new BlockPosition(1, 65, 0)), 0))));
    }

    @Test
    void physicalReceiptMayEmitTheDeclaredMaintenanceCargoBinding() {
        SubjectId maintenance = new SubjectId("maintenance:route-test");
        RouteMaintenanceMaterialLoaded loaded = new RouteMaintenanceMaterialLoaded(maintenance,
                new CargoBatch(new SubjectId("cargo:route-test"), FrontierRouteNetwork.OWNER, List.of(new SubjectId("item:route-test"))));
        ProposedEvent event = new ProposedEvent(FrontierRouteNetwork.OWNER, loaded);
        assertEquals(List.of(event), FrontierWorldRuntimeDefinition.processRegistry().validateEmissions("physical-observation", List.of(event)),
                "the closed process contract must admit the physical source decrement's infrastructure cargo binding");
    }

    @Test
    void workIntentResolvesItsOperationFromExactSubjectsWhileTheRouteNetworkRemainsItsCause() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-maintenance-intent-owner"), 41L);
        FrontierWorldState state = FrontierWorldState.initial(bootstrap);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        BlockPosition loss = state.routeTopology().supplyWaypoints(bootstrap, settlement).get(2);
        state = state.recordPhysicalDelta(new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));
        RouteMaintenance maintenance = RouteMaintenanceProcess.plan(state, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .findFirst().orElseThrow().maintenance();
        FrontierWorldState admitted = RouteMaintenanceStateSupport.reduceStarted(state, FrontierRouteNetwork.OWNER,
                new RouteMaintenanceStarted(maintenance));
        PhysicalIntent intent = RouteMaintenanceProcess.workIntent(maintenance, maintenance.plannedCargoId(), maintenance.plannedCargoItemId());

        assertEquals(FrontierRouteNetwork.OWNER, intent.causeSubjectId());
        assertEquals(maintenance.id(), RouteMaintenanceStateSupport.workOperation(admitted, intent).id(),
                "the physical executor must resolve an in-place repair from its bound maintenance subject, never the route-network cause ID");
    }

    @Test
    void completedMaintenanceAssemblyMayEnterItsCurrentHotWorksite() {
        WorldId world = new WorldId("frontier:route-maintenance-hot-worksite");
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(world, 41L);
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        BlockPosition loss = initial.routeTopology().supplyWaypoints(bootstrap, settlement).get(2);
        FrontierWorldState damaged = initial.recordPhysicalDelta(new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));
        RouteMaintenance maintenance = RouteMaintenanceProcess.plan(damaged, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .findFirst().orElseThrow().maintenance();
        SubjectId engineer = maintenance.team().memberIds().getFirst();
        ExactItemStack tool = damaged.inventory().items().values().stream().filter(item -> EngineeringToolCustody.isTool(item.itemKind()))
                .filter(item -> item.economicOwnerId().equals(settlement)).findFirst().orElseThrow();
        FrontierWorldState equipped = RouteMaintenanceStateSupport.reduceStarted(damaged, FrontierRouteNetwork.OWNER,
                new RouteMaintenanceStarted(maintenance)).withInventory(damaged.inventory().moveObservedItem(tool.id(), tool.custody(), new InventoryCustody.Actor(engineer)));
        EngineeringWorkAssembly assembly = EngineeringWorksite.compile(equipped, maintenance);
        FrontierWorldState assembled = RouteMaintenanceStateSupport.reduceAssemblyStarted(equipped, FrontierRouteNetwork.OWNER,
                new RouteMaintenanceAssemblyStarted(maintenance.id(), assembly));
        while (!assembled.routeMaintenances().get(maintenance.id()).assembly().orElseThrow().complete()) {
            EngineeringWorkAssembly current = assembled.routeMaintenances().get(maintenance.id()).assembly().orElseThrow();
            SubjectId advancing = current.nextSafeAdvance().orElseThrow();
            assembled = RouteMaintenanceStateSupport.reduceAssemblyAdvanced(assembled, FrontierRouteNetwork.OWNER,
                    new RouteMaintenanceAssemblyAdvanced(maintenance.id(), current.advance(advancing)));
        }
        EngineeringWorkSceneCandidate candidate = FrontierEngineeringWorkSceneSupport.nextCandidate(assembled).orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:maintenance-hot-worksite");
        SceneLease lease = SceneLease.forCause(leaseId, world, new EngineeringWorkSceneCause(candidate.projectId(), candidate.workCellIndex()),
                candidate.workCell(), io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED,
                candidate.memberPositions().keySet().stream().sorted().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(world, leaseId, actor))).toList(),
                SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), java.util.Set.of(), Optional.empty());

        FrontierWorldState prepared = assembled.prepareSceneLease(lease);
        assertEquals(SceneLeaseStatus.HOT, prepared.transitionSceneLease(leaseId, SceneLeaseStatus.HOT).sceneLeases().get(leaseId).status(),
                "a current BUILDING maintenance owner and its exact completed COLD crew must admit the matching HOT lease");
    }

    @Test
    void physicalMaintenanceConflictDrainsItsExactHotWorksiteInsteadOfRetainingStaleBodies() {
        WorldId world = new WorldId("frontier:route-maintenance-conflict-drain");
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(world, 41L);
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        BlockPosition loss = initial.routeTopology().supplyWaypoints(bootstrap, settlement).get(2);
        FrontierWorldState damaged = initial.recordPhysicalDelta(new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));
        RouteMaintenance maintenance = RouteMaintenanceProcess.plan(damaged, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .findFirst().orElseThrow().maintenance();
        SubjectId engineer = maintenance.team().memberIds().getFirst();
        ExactItemStack tool = damaged.inventory().items().values().stream().filter(item -> EngineeringToolCustody.isTool(item.itemKind()))
                .filter(item -> item.economicOwnerId().equals(settlement)).findFirst().orElseThrow();
        FrontierWorldState assembled = RouteMaintenanceStateSupport.reduceStarted(damaged, FrontierRouteNetwork.OWNER,
                new RouteMaintenanceStarted(maintenance)).withInventory(damaged.inventory().moveObservedItem(tool.id(), tool.custody(), new InventoryCustody.Actor(engineer)));
        FrontierWorldState approachState = assembled;
        EngineeringWorkAssembly assembly = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> EngineeringWorksite.compile(approachState, maintenance),
                "one local engineering approach must not compile the complete world route footprint");
        assembled = RouteMaintenanceStateSupport.reduceAssemblyStarted(assembled, FrontierRouteNetwork.OWNER,
                new RouteMaintenanceAssemblyStarted(maintenance.id(), assembly));
        while (!assembled.routeMaintenances().get(maintenance.id()).assembly().orElseThrow().complete()) {
            EngineeringWorkAssembly current = assembled.routeMaintenances().get(maintenance.id()).assembly().orElseThrow();
            assembled = RouteMaintenanceStateSupport.reduceAssemblyAdvanced(assembled, FrontierRouteNetwork.OWNER,
                    new RouteMaintenanceAssemblyAdvanced(maintenance.id(), current.advance(current.nextSafeAdvance().orElseThrow())));
        }
        EngineeringWorkSceneCandidate candidate = FrontierEngineeringWorkSceneSupport.nextCandidate(assembled).orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:maintenance-conflict-drain");
        SceneLease lease = SceneLease.forCause(leaseId, world, new EngineeringWorkSceneCause(candidate.projectId(), candidate.workCellIndex()),
                candidate.workCell(), io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED,
                candidate.memberPositions().keySet().stream().sorted().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(world, leaseId, actor))).toList(),
                SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), java.util.Set.of(), Optional.empty());
        FrontierWorldState hot = assembled.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        PhysicalIntent intent = RouteMaintenanceProcess.workIntent(maintenance, maintenance.plannedCargoId(), maintenance.plannedCargoItemId());
        FrontierWorldState conflicted = RouteMaintenanceStateSupport.conflict(hot, intent, new java.util.LinkedHashMap<>(hot.physicalIntents()));

        assertEquals(RouteMaintenanceStatus.CONFLICT, conflicted.routeMaintenances().get(maintenance.id()).status());
        assertEquals(SceneLeaseStatus.DRAINING, conflicted.sceneLeases().get(leaseId).status(),
                "a terminal physical conflict must atomically revoke HOT authority before the bodies are released");
        assertDoesNotThrow(() -> conflicted.releaseSceneLease(leaseId, lease.members().stream().map(member ->
                new SceneMemberPosition(member.actorId(), conflicted.actorLocations().get(member.actorId()).body(),
                        conflicted.actorLocations().get(member.actorId()).condition().health())).toList()));
    }

    @Test
    void terminalMaintenanceMayRetainItsHistoricalAssemblyWithoutBecomingAnActiveWorksite() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-maintenance-terminal-assembly"), 41L);
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        BlockPosition loss = initial.routeTopology().supplyWaypoints(bootstrap, settlement).get(2);
        FrontierWorldState damaged = initial.recordPhysicalDelta(new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));
        RouteMaintenance maintenance = RouteMaintenanceProcess.plan(damaged, RouteMaintenanceProcess.scan(1, 100L)).stream()
                .map(ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .findFirst().orElseThrow().maintenance();
        SubjectId engineer = maintenance.team().memberIds().getFirst();
        ExactItemStack tool = damaged.inventory().items().values().stream().filter(item -> EngineeringToolCustody.isTool(item.itemKind()))
                .filter(item -> item.economicOwnerId().equals(settlement)).findFirst().orElseThrow();
        FrontierWorldState equipped = RouteMaintenanceStateSupport.reduceStarted(damaged, FrontierRouteNetwork.OWNER,
                new RouteMaintenanceStarted(maintenance)).withInventory(damaged.inventory().moveObservedItem(tool.id(), tool.custody(), new InventoryCustody.Actor(engineer)));
        EngineeringWorkAssembly assembly = EngineeringWorksite.compile(equipped, maintenance);
        FrontierWorldState assembled = RouteMaintenanceStateSupport.reduceAssemblyStarted(equipped, FrontierRouteNetwork.OWNER,
                new RouteMaintenanceAssemblyStarted(maintenance.id(), assembly));
        RouteMaintenance conflicted = assembled.routeMaintenances().get(maintenance.id()).conflict();

        assertDoesNotThrow(() -> assembled.withChanges(FrontierWorldStateUpdate.begin().routeMaintenances(Map.of(conflicted.id(), conflicted))),
                "a retained terminal assembly is history, not permission to validate or materialize a new active worksite");
    }

    private static RouteMaintenanceStarted started(List<ProposedEvent> events) {
        return events.stream().map(ProposedEvent::payload).filter(RouteMaintenanceStarted.class::isInstance)
                .map(RouteMaintenanceStarted.class::cast).findFirst().orElseThrow();
    }
}
