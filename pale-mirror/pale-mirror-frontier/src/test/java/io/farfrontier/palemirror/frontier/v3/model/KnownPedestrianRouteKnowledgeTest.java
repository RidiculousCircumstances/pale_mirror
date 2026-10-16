package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementColdAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnownPedestrianRouteKnowledgeTest {
    @Test void replacementStorageAtAFacilityThresholdHasAnExecutablePassageAndBlockedServiceDoesNotInviteFoodPlanning() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:replacement-storage-threshold"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r13")));
        var home = new SubjectId("settlement:6"); var oldDepot = FrontierWorldState.depotId(home);
        var facility = state.bootstrap().settlements().stream().filter(value -> value.id().equals(home)).findFirst().orElseThrow()
                .structures().stream().filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        var port = FrontierTraversalPlan.facilityPort(facility).orElseThrow();
        var station = port.thresholdSurface(); var socket = state.inventory().surfaces().get(oldDepot).position();
        state = state.withInventory(state.inventory().withSurfaceStatus(oldDepot, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(oldDepot, ContainerSurfaceStatus.ACTIVE).withSurfaceStatus(oldDepot, ContainerSurfaceStatus.RETIRED));
        var id = new SubjectId("container:replacement-threshold-storage");
        var endpoint = new ShipmentEndpoint.Storage(home, id, station, Optional.of(facility.id()), station);
        var policy = new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0, ResourceStorageRole.WAREHOUSE);
        state = new ResourceStorageDeclared(new ContainerRecord(id, home, 27, ContainerPurpose.RESOURCE_STORAGE,
                Optional.empty(), Optional.of(policy)), socket).apply(state, home);
        var knowledge = ShipmentEndpointComposition.knowledge(state, List.of(endpoint));
        assertTrue(knowledge.traversable(List.of(station)), "the selected threshold is actual support, not a closed foundation");
        knowledge.requireRoute(knowledge.plannedBetween(port.exteriorApproach().getFirst(), station));
        var pointId = ServiceBoundaryComposition.declaration(state, id).identity();
        var point = ServiceBoundaryComposition.geometry(state, pointId);
        assertTrue(ServiceClearanceTargets.mayPlan(point, knowledge, java.util.Set.of()));
        assertTrue(ServiceClearanceTargets.plannedSelection(point, new SubjectId("resident:6-11"), knowledge, java.util.Set.of()).isPresent());
        var changed = state.recordPhysicalDelta(new PhysicalDelta(station.support().offset(0, 2, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:replacement-threshold-head-blocked"));
        var blockedKnowledge = ShipmentEndpointComposition.knowledge(changed, List.of(endpoint));
        var blockedPoint = ServiceBoundaryComposition.geometry(changed, pointId);
        assertTrue(!blockedKnowledge.traversable(List.of(station)), "a declared port never erases player obstructions");
        assertTrue(!ServiceClearanceTargets.mayPlan(blockedPoint, blockedKnowledge, java.util.Set.of()));
        assertTrue(ServiceClearanceTargets.plannedSelection(blockedPoint, new SubjectId("resident:6-11"),
                blockedKnowledge, java.util.Set.of()).isEmpty());
        var recovered = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state));
        ShipmentEndpointComposition.knowledge(recovered, List.of(endpoint)).requireRoute(
                knowledge.plannedBetween(port.exteriorApproach().getFirst(), station));
    }
    @Test void incompatibleDepartureHeightIsMissingKnowledgeButKnownHardScopeStillDeniesDeparture() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:meal-departure-height"), 20260918065L));
        var home = state.bootstrap().settlements().getFirst();
        var actor = home.residents().getFirst().id();
        var known = KnownPedestrianGround.forFrontier(state).at(home.anchor().x() + 22, home.anchor().z() + 22);
        var start = SurfaceAnchor.at(known.x(), known.y() + 5, known.z());
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state).forActor(state, actor);
        var missing = assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                () -> knowledge.plannedBetween(start, known));
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.UNKNOWN_GEOMETRY,
                missing.status(), "a missing COLD origin cannot deny a path in the real loaded world");
        assertEquals(known, knowledge.supportAt(known.x(), known.z()), "uncertainty cannot rewrite COLD terrain");
        var outside = SurfaceAnchor.at(state.bootstrap().bounds().maxXExclusive(), known.y(), known.z());
        var denied = assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                () -> knowledge.plannedBetween(outside, known));
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH,
                denied.status(), "a known hard boundary must not opt into physical-only navigation");
    }

    @Test void authoredQuarryChestUsesTheSameDepartureGeometryAsDeclaredWarehouses() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:quarry-chest-departure"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r6")));
        var deposit = state.extractionSites().deposits().values().stream()
                .filter(value -> value.site().settlementId().equals(new SubjectId("settlement:6"))).findFirst().orElseThrow();
        var container = state.inventory().containers().get(deposit.site().containerId());
        assertEquals(ContainerPurpose.EXTRACTIVE_STORAGE, container.purpose());
        var endpoint = container.storage().orElseThrow().endpoint();
        var start = new SurfaceAnchor(state.inventory().surfaces().get(container.id()).position());
        var knowledge = ResourceStorageNavigation.forEndpoint(state, endpoint);
        var route = knowledge.plannedBetween(start, endpoint.station());
        assertEquals(start, route.getFirst());
        assertEquals(endpoint.station(), route.getLast());
        knowledge.requireRoute(route);
        assertTrue(knowledge.geometry().blocked(start));
        assertThrows(KnownPedestrianNavigation.RouteUnavailable.class, () -> knowledge.plannedBetween(endpoint.station(), start));
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        ResourceStorageNavigation.forEndpoint(codec.decode(codec.encode(state)), endpoint).requireRoute(route);
    }

    @Test void retainedContactCanLeaveAChestWithoutOpeningItAsATransitDestinationOrIgnoringDamage() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:raised-storage-departure"), 91L));
        var home = state.bootstrap().settlements().getFirst();
        var ground = KnownPedestrianGround.forFrontier(state).at(home.anchor().x() + 22, home.anchor().z() + 22);
        var id = new SubjectId("container:raised-storage-departure");
        var endpoint = new ShipmentEndpoint.Storage(home.id(), id, ground, Optional.empty(), ground);
        var record = new ContainerRecord(id, home.id(), 27, ContainerPurpose.RESOURCE_STORAGE, Optional.empty(),
                Optional.of(new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0, ResourceStorageRole.WAREHOUSE)));
        var block = ground.support().offset(0, 1, 0);
        var declared = new ResourceStorageDeclared(record, block).apply(state, home.id());
        var start = new SurfaceAnchor(block);
        var target = KnownPedestrianGround.forFrontier(declared).at(start.x() + 1, start.z());
        var knowledge = ResourceStorageNavigation.forEndpoint(declared, endpoint);
        var route = knowledge.plannedBetween(start, target);
        assertEquals(List.of(start, target), route);
        knowledge.requireRoute(route);
        assertTrue(knowledge.geometry().blocked(start), "the departure exception cannot open the chest as a remote goal");
        assertThrows(KnownPedestrianNavigation.RouteUnavailable.class, () -> knowledge.plannedBetween(target, start));
        assertThrows(KnownPedestrianNavigation.RouteUnavailable.class, () -> knowledge.withTaskExclusions(java.util.Set.of(start))
                .plannedBetween(start, target));
        var obstruction = declared.recordPhysicalDelta(new PhysicalDelta(block.offset(0, 1, 0), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:blocked-raised-body"));
        assertThrows(KnownPedestrianNavigation.RouteUnavailable.class, () -> ResourceStorageNavigation.forEndpoint(obstruction, endpoint)
                .plannedBetween(start, target));
        var lost = declared.recordPhysicalDelta(new PhysicalDelta(block, PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:changed-raised-support"));
        assertThrows(KnownPedestrianNavigation.RouteUnavailable.class, () -> ResourceStorageNavigation.forEndpoint(lost, endpoint)
                .plannedBetween(start, target));
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        ResourceStorageNavigation.forEndpoint(codec.decode(codec.encode(declared)), endpoint).requireRoute(route);
    }

    @Test void observedChestTopStillGrantsOnlyRetainedDepartureAfterRecovery() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:observed-chest-departure"), 91L));
        var home = state.bootstrap().settlements().getFirst();
        var ground = KnownPedestrianGround.forFrontier(state).at(home.anchor().x() + 22, home.anchor().z() + 22);
        var id = new SubjectId("container:observed-chest-departure");
        var endpoint = new ShipmentEndpoint.Storage(home.id(), id, ground, Optional.empty(), ground);
        var block = ground.support().offset(0, 1, 0);
        state = new ResourceStorageDeclared(new ContainerRecord(id, home.id(), 27, ContainerPurpose.RESOURCE_STORAGE,
                Optional.empty(), Optional.of(new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0,
                        ResourceStorageRole.WAREHOUSE))), block).apply(state, home.id());
        var chest = new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell(
                new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationShape(List.of(
                        new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationShape.Box(
                                .0625, 0, .0625, .9375, .875, .9375))), true);
        state = new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved(0,
                Map.of(block, io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation.known(chest))).apply(state,
                io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved.OWNER);
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        for (var current : List.of(state, codec.decode(codec.encode(state)))) {
            var start = new SurfaceAnchor(block);
            var actor = home.residents().getFirst().id();
            var knowledge = PedestrianLocalDeparture.departure(current, start).forActor(current, actor);
            assertTrue(knowledge.geometry().knowsSupport(start));
            assertTrue(knowledge.geometry().blocked(start), "known contact is still forbidden as a new transit goal");
            var route = PedestrianLocalDeparture.exitToPublic(current, actor, start);
            assertTrue(route.size() > 1);
            knowledge.requireRoute(route);
            assertTrue(PedestrianLocalDeparture.publicPosition(current, route.getLast()));
            assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                    () -> knowledge.plannedBetween(route.getLast(), start));
        }
    }

    @Test void realStorageProviderPreservesUnrelatedPlanningButRecoveryStartsANewKnowledgeEpoch() throws Exception {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:regional-provider"), 91L));
        var home = state.bootstrap().settlements().getFirst();
        var ground = KnownPedestrianGround.forFrontier(state);
        var start = ground.at(home.anchor().x() + 22, home.anchor().z() + 22);
        var end = ground.at(start.x() + 1, start.z());
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        var target = ground.at(home.anchor().x() + 100, home.anchor().z() + 100);
        var id = new SubjectId("container:regional-warehouse");
        var endpoint = new ShipmentEndpoint.Storage(home.id(), id, target, Optional.empty(), target);
        var record = new ContainerRecord(id, home.id(), 27, ContainerPurpose.RESOURCE_STORAGE, Optional.empty(),
                Optional.of(new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0, ResourceStorageRole.WAREHOUSE)));
        var changed = new ResourceStorageDeclared(record, target.support().offset(0, 1, 0)).apply(state, home.id());
        try (var planner = new io.farfrontier.palemirror.frontier.v3.model.navigation.CooperativePedestrianPlanner();
                var binding = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind(planner)) {
            var pending = assertThrows(KnownPedestrianNavigation.RouteUnavailable.class, () -> knowledge.plannedBetween(start, end));
            assertEquals(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING, pending.status());
            for (int turn = 0; turn < 16 && planner.pendingCount() > 0; turn++)
                planner.advance(io.farfrontier.palemirror.frontier.v3.model.navigation.HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            var route = knowledge.plannedBetween(start, end); long work = planner.workUnits();
            var refreshed = KnownPedestrianRouteKnowledge.forFrontier(changed);
            assertEquals(route, refreshed.plannedBetween(start, end));
            assertEquals(work, planner.workUnits(), "declaring a remote chest cannot redo this region search");
            // Hydration starts a new knowledge epoch. Retained route evidence still validates,
            // but an unrelated restored geometry may not borrow a previous live cache.
            var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
            var recovered = KnownPedestrianRouteKnowledge.forFrontier(codec.decode(codec.encode(changed)));
            assertTrue(recovered.planningEvidence(start, end).isEmpty()); recovered.requireRoute(route);
        }
    }
    @Test void fieldOverlayChangesOnlyItsObstructedRegionsWithoutSharingItsPermissionWithAnotherView() throws Exception {
        var state = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial());
        var site = state.resourceSite(new SubjectId("site:1-wheat-field"));
        var access = ResourceStorageAccess.at(state, FrontierWorldState.depotId(site.settlementId()));
        var start = SettlementServiceAccessPoints.depotPort(state, site.settlementId()).exteriorApproach();
        var cycle = state.resourceSites().cycle(site.id()); var cell = cycle.layout().cells().getFirst();
        var changedCycle = cycle.cropObstructed(cell.id());
        var changed = state.withResourceSites(state.resourceSites().replace(state.resourceSites().site(site.id()), changedCycle));
        var geometry = new java.util.concurrent.atomic.AtomicReference<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry>();
        try (var binding = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind((view, from, to) -> {
            geometry.set(view);
            return new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult(
                    io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.FOUND, List.of(from), 0, 1, "geometry-only fixture");
        })) {
            KnownPedestrianRouteKnowledge.forField(state, site, cycle, access, start).plannedBetween(start, start);
            var prior = geometry.get().regionalRevision().orElseThrow();
            KnownPedestrianRouteKnowledge.forField(changed, site, changedCycle, access, start).plannedBetween(start, start);
            var current = geometry.get().regionalRevision().orElseThrow();
            var affected = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region.at(cell.soil().support());
            assertEquals(prior.scope(), current.scope());
            org.junit.jupiter.api.Assertions.assertNotSame(prior.at(affected.x(), affected.z()), current.at(affected.x(), affected.z()));
            org.junit.jupiter.api.Assertions.assertSame(prior.at(affected.x() + 10, affected.z()), current.at(affected.x() + 10, affected.z()));
            KnownPedestrianRouteKnowledge.forFrontier(changed).plannedBetween(start, start);
            org.junit.jupiter.api.Assertions.assertNotEquals(current.scope(), geometry.get().regionalRevision().orElseThrow().scope());
        }
    }
    @Test void storageLifecycleInvalidatesRoutesOnlyWhenItsPhysicalFootprintChanges() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:storage-geometry"), 91L));
        var home = state.bootstrap().settlements().getFirst();
        var station = KnownPedestrianGround.forFrontier(state).at(home.anchor().x() + 22, home.anchor().z() + 22);
        var id = new SubjectId("container:geometry-warehouse");
        var endpoint = new ShipmentEndpoint.Storage(home.id(), id, station, Optional.empty(), station);
        var record = new ContainerRecord(id, home.id(), 27, ContainerPurpose.RESOURCE_STORAGE, Optional.empty(),
                Optional.of(new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0, ResourceStorageRole.WAREHOUSE)));
        var before = KnownPedestrianRouteKnowledge.forFrontier(state);
        assertTrue(before.traversable(List.of(station)));
        var declared = new ResourceStorageDeclared(record, station.support().offset(0, 1, 0)).apply(state, home.id());
        var occupied = KnownPedestrianRouteKnowledge.forFrontier(declared);
        assertTrue(!occupied.traversable(List.of(station)), "a declared physical chest is a real obstacle");
        var lifecycle = declared;
        for (var status : List.of(ContainerSurfaceStatus.PREPARED, ContainerSurfaceStatus.ACTIVE,
                ContainerSurfaceStatus.CONFLICT)) {
            lifecycle = lifecycle.withInventory(lifecycle.inventory().withSurfaceStatus(id, status));
            org.junit.jupiter.api.Assertions.assertSame(occupied, KnownPedestrianRouteKnowledge.forFrontier(lifecycle),
                    "access/custody metadata must not cancel geometry work, or erase an existing physical block");
        }
        var active = declared.withInventory(declared.inventory().withSurfaceStatus(id, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(id, ContainerSurfaceStatus.ACTIVE));
        var retired = active.withInventory(active.inventory().withSurfaceStatus(id, ContainerSurfaceStatus.RETIRED));
        var removed = KnownPedestrianRouteKnowledge.forFrontier(retired);
        org.junit.jupiter.api.Assertions.assertNotSame(occupied, removed);
        assertTrue(removed.traversable(List.of(station)), "a durably retired destroyed chest must not remain a phantom wall");
        assertTrue(!occupied.traversable(List.of(station)), "old route knowledge remains immutable");
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        assertTrue(KnownPedestrianRouteKnowledge.forFrontier(codec.decode(codec.encode(retired))).traversable(List.of(station)));
    }

    @Test void fieldGeometrySurvivesBiologicalChangesButNotObstructionsOrRecovery() {
        var state = ResourceSiteHarvestProcessTest.ready(ResourceSiteHarvestProcessTest.initial());
        var site = state.resourceSite(new SubjectId("site:1-wheat-field"));
        var port = SettlementServiceAccessPoints.depotPort(state, site.settlementId());
        var access = ResourceStorageAccess.at(state, FrontierWorldState.depotId(site.settlementId()));
        var start = port.exteriorApproach();
        var cycle = state.resourceSites().cycle(site.id());
        var base = KnownPedestrianRouteKnowledge.forField(state, site, cycle, access, start);
        org.junit.jupiter.api.Assertions.assertSame(base,
                KnownPedestrianRouteKnowledge.forField(state, site, cycle, access, start));
        var cell = cycle.layout().cells().getFirst();
        var replanted = cycle.cropReplanted(cell.id());
        var changed = state.withResourceSites(state.resourceSites().replace(state.resourceSites().site(site.id()), replanted));
        org.junit.jupiter.api.Assertions.assertSame(base,
                KnownPedestrianRouteKnowledge.forField(changed, site, replanted, access, start),
                "crop age and generation do not change pedestrian geometry");
        var blocked = replanted.cropObstructed(cell.id());
        var damaged = changed.withResourceSites(changed.resourceSites().replace(changed.resourceSites().site(site.id()), blocked));
        var rebuilt = KnownPedestrianRouteKnowledge.forField(damaged, site, blocked, access, start);
        org.junit.jupiter.api.Assertions.assertNotSame(base, rebuilt);
        assertTrue(!rebuilt.traversable(List.of(cell.soil())));
        assertTrue(base.traversable(List.of(cell.soil())), "old immutable knowledge cannot be mutated by an overlay");
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var recovered = codec.decode(codec.encode(damaged));
        assertTrue(!KnownPedestrianRouteKnowledge.forField(recovered, recovered.resourceSite(site.id()),
                recovered.resourceSites().cycle(site.id()), access, start).traversable(List.of(cell.soil())));
        org.junit.jupiter.api.Assertions.assertNotSame(rebuilt,
                KnownPedestrianRouteKnowledge.forField(state, site, cycle, access, start));
    }
    @Test void clearwaterPrivateStationCanBeExitedButCannotBecomeACourtesyRestingPosition() throws Exception {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:courtesy-private-station"), 20260918065L));
        var start = SurfaceAnchor.at(136, 64, 14);
        assertTrue(PedestrianLocalDeparture.departure(state, start).traversable(List.of(start)));
        assertTrue(!PedestrianLocalDeparture.publicPosition(state, start),
                "an escape passage does not grant permission to park on an internal station");
        var exit = PedestrianLocalDeparture.exitToPublic(state, new SubjectId("resident:7-12"), start);
        assertEquals(start, exit.getFirst());
        assertTrue(exit.size() > 1 && PedestrianLocalDeparture.publicPosition(state, exit.getLast()));
        for (var status : List.of(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING,
                io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.UNKNOWN_GEOMETRY,
                io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.SAFETY_LIMIT)) {
            var queries = new java.util.concurrent.atomic.AtomicInteger();
            try (var binding = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind((geometry, from, to) -> {
                queries.incrementAndGet();
                return new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult(status, List.of(), 1, 1, "private exit pending");
            })) {
                var pending = org.junit.jupiter.api.Assertions.assertThrows(
                        io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable.class,
                        () -> PedestrianLocalDeparture.exitToPublic(state, new SubjectId("resident:7-12"), start));
                assertEquals(status, pending.status(), "pending private departure cannot become a route miss");
                assertEquals(1, queries.get());
            }
        }
    }
    @Test void courtesyEscapesTheCurrentFacilityWithoutOpeningUnrelatedWorkStations() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:courtesy-geometry"), 91L));
        var workshops = state.bootstrap().settlements().stream().flatMap(settlement -> settlement.structures().stream())
                .filter(facility -> facility.kind() == StructureKind.WORKSHOP).limit(2).toList();
        var port = FrontierTraversalPlan.facilityPort(workshops.getFirst()).orElseThrow();
        var start = port.stations().getFirst();
        var knowledge = PedestrianLocalDeparture.departure(state, start);
        assertTrue(knowledge.traversable(List.of(start)), "the current station has a declared exit");
        var exit = port.ingressSurfaces().reversed();
        var path = knowledge.path(start, new MovementOrder(new SubjectId("resident:courtesy"), new SubjectId("task:courtesy"),
                0, 1, List.of(exit.getLast()), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION));
        assertEquals(exit.getLast(), path.getLast());
        var unrelated = SettlementWorkshopServicePort.forWorkshop(workshops.getLast()).workStation();
        assertTrue(!knowledge.traversable(List.of(unrelated)), "courtesy is not a permit for a different interior");
    }
    @Test void facilityStationsShareTheirMaterializedSupportWithoutGrantingEntry() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:facility-station-ground"), 20260918065L));
        var cells = FrontierGrayboxPlan.compile(state).cells();
        var ground = KnownPedestrianGround.forFrontier(state);
        for (var settlement : state.bootstrap().settlements()) {
            for (var structure : settlement.structures()) {
                var declared = FrontierTraversalPlan.facilityPort(structure);
                if (declared.isEmpty()) continue;
                var port = declared.orElseThrow();
                var authorized = KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), List.of(
                        new KnownPedestrianRouteKnowledge.Passage(structure,
                                KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS)));
                for (var station : port.stations()) {
                    assertTrue(cells.containsKey(station.support()), "station must have an authored physical floor");
                    assertEquals(station, ground.at(station.x(), station.z()), structure.id().value());
                    assertEquals(station, authorized.supportAt(station.x(), station.z()));
                    authorized.requireRoute(List.of(station));
                }
                if (structure.kind() == StructureKind.WORKSHOP) {
                    var work = SettlementWorkshopServicePort.forWorkshop(structure).workStation();
                    assertEquals(GrayboxSemanticPart.WORKSHOP_PROCESS_STATION, cells.get(work.support()).semanticPart(),
                            "knowledge must not turn a production floor into public circulation");
                    var unauthorized = KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), List.of());
                    assertTrue(!unauthorized.traversable(List.of(work)), "support knowledge is not entry permission");
                    var changed = state.recordPhysicalDelta(new PhysicalDelta(work.support(),
                            PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:station-floor-loss"));
                    var damaged = KnownPedestrianRouteKnowledge.forSettlement(changed, settlement.id(), List.of(
                            new KnownPedestrianRouteKnowledge.Passage(structure,
                                    KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS)));
                    assertThrows(IllegalArgumentException.class, () -> damaged.requireRoute(List.of(work)));
                }
            }
        }
    }

    @Test void composingAnExitWithAnOutdoorRouteCannotRepeatTheEntranceDetour() {
        var apron = SurfaceAnchor.at(-384, 64, -326);
        var exterior = SurfaceAnchor.at(-385, 64, -326);
        var onward = SurfaceAnchor.at(-384, 63, -327);
        var destination = SurfaceAnchor.at(-384, 63, -328);
        var path = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianPathComposition
                .withoutLoops(List.of(apron, exterior, apron, onward, destination));
        assertEquals(List.of(apron, onward, destination), path,
                "the composed next step continues to the destination, not back to the exit anchor");
    }

    @Test void aPublicAccessFloorIsSupportNotAnObstacleForAnyPedestrianCaller() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:public-floor"), 91L));
        var surface = SurfaceAnchor.at(-365, 64, -318);
        var cells = FrontierGrayboxPlan.compile(state).cells();
        assertEquals(GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE, cells.get(surface.support()).semanticPart());
        assertTrue(!cells.containsKey(surface.support().offset(0, 1, 0))
                && !cells.containsKey(surface.support().offset(0, 2, 0)));
        assertEquals(surface, KnownPedestrianGround.forFrontier(state).at(surface.x(), surface.z()));
        assertTrue(KnownPedestrianRouteKnowledge.forFrontier(state).traversable(List.of(surface)));
        assertTrue(KnownPedestrianRouteKnowledge.forSettlement(state, new SubjectId("settlement:1"), List.of())
                .traversable(List.of(surface)));
        for (int height : List.of(0, 1, 2)) {
            var changed = state.recordPhysicalDelta(new PhysicalDelta(surface.support().offset(0, height, 0),
                    PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:public-floor-obstruction"));
            assertTrue(!KnownPedestrianRouteKnowledge.forFrontier(changed).traversable(List.of(surface)),
                    "a witnessed change at support, feet or head remains a hard barrier");
        }
    }

    @Test void anUnsupportedRaisedSillEdgeIsNotItsOwnServiceExit() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:unsupported-service-exit-edge"), 20260918065L));
        var settlementId = new SubjectId("settlement:7");
        var port = SettlementServiceAccessPoints.depotPort(state, settlementId);
        var actorId = new SubjectId("resident:7-9");
        var unsupported = SurfaceAnchor.at(133, 64, 14);
        assertTrue(port.accessBoundary().cleared(unsupported.standingBody()));
        var candidates = KnownServiceExitNavigation.exitStations(state, settlementId,
                FrontierWorldState.depotId(settlementId), actorId, unsupported);
        assertTrue(!candidates.isEmpty(), "a straddling body still needs a supported exit target");
        assertTrue(!candidates.contains(unsupported), "being outside the boundary is not support evidence");
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId);
        var depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow();
        var knowledge = KnownPedestrianRouteKnowledge.forSettlement(state, settlementId,
                List.of(new KnownPedestrianRouteKnowledge.Passage(depot,
                        KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
        assertTrue(candidates.stream().allMatch(surface -> surface.equals(knowledge.supportAt(surface.x(), surface.z()))));
        var supported = candidates.getFirst();
        assertEquals(List.of(supported), KnownServiceExitNavigation.exitStations(state, settlementId,
                FrontierWorldState.depotId(settlementId), actorId, supported));
    }
    @Test void serviceGeometryIsReusedButPhysicalChangesInvalidateIt() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:service-geometry-reuse"), 20260918065L));
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), new SubjectId("settlement:7"));
        var depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow();
        var port = SettlementDepotServicePort.forDepot(depot);
        var point = SettlementServiceAccessPoints.forDepot(state, settlement, depot);
        var resident = settlement.residents().getFirst().id();
        var moved = state.withActorBody(resident, port.serviceSurface().standingBody());
        org.junit.jupiter.api.Assertions.assertSame(point, SettlementServiceAccessPoints.forDepot(moved, settlement, depot));
        var remoteChange = moved.recordPhysicalDelta(new PhysicalDelta(new BlockPosition(490, 64, 490),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:remote-obstruction"));
        org.junit.jupiter.api.Assertions.assertSame(point, SettlementServiceAccessPoints.forDepot(remoteChange, settlement, depot),
                "a distant change must not recompute this service area's targets");
        org.junit.jupiter.api.Assertions.assertSame(SettlementPedestrianGround.localSupports(state.bootstrap(), settlement.id()),
                SettlementPedestrianGround.localSupports(moved.bootstrap(), settlement.id()));
        assertEquals(point.egressSurfaces(), SettlementServiceAccessPoints.egress(moved, port));
        assertTrue(ServiceAccessCoordinator.boundary(moved, FrontierWorldState.depotId(settlement.id()))
                .occupied(moved.actorLocations().get(resident).body()));
        var blocked = point.egressSurfaces().iterator().next();
        var damaged = moved.recordPhysicalDelta(new PhysicalDelta(blocked.support().offset(0, 1, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:service-exit-obstruction"));
        var rebuilt = SettlementServiceAccessPoints.forDepot(damaged, settlement, depot);
        org.junit.jupiter.api.Assertions.assertNotSame(point, rebuilt);
        assertTrue(!rebuilt.egressSurfaces().contains(blocked));
        assertTrue(!SettlementServiceAccessPoints.egress(damaged, port).contains(blocked));
        // Switching to recovered immutable geometry must not retain another world's cache.
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var recovered = codec.decode(codec.encode(damaged));
        assertEquals(rebuilt, SettlementServiceAccessPoints.forDepot(recovered,
                FrontierWorldStateSupport.settlement(recovered.bootstrap(), settlement.id()), depot));
        assertEquals(point, SettlementServiceAccessPoints.forDepot(state, settlement, depot));
    }

    @Test void serviceClearanceFindsAnotherExitWhenThePreferredCellIsBlocked() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:alternate-service-exit"), 20260918065L));
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), new SubjectId("settlement:7"));
        var port = SettlementServiceAccessPoints.depotPort(state, settlement.id());
        var resident = settlement.residents().getFirst().id();
        state = state.withActorBody(resident, port.serviceSurface().standingBody());
        var first = KnownServiceExitNavigation.plannedClearancePathFrom(state, settlement.id(), FrontierWorldState.depotId(settlement.id()),
                resident, resident, 0, 1L, port.serviceSurface());
        var blocked = first.getLast();
        state = state.recordPhysicalDelta(new PhysicalDelta(blocked.support().offset(0, 1, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:preferred-exit-blocked"));
        var alternate = KnownServiceExitNavigation.plannedClearancePathFrom(state, settlement.id(), FrontierWorldState.depotId(settlement.id()),
                resident, resident, 0, 1L, port.serviceSurface());
        assertTrue(!alternate.getLast().equals(blocked));
        assertTrue(port.accessBoundary().cleared(alternate.getLast().standingBody()));
        assertTrue(alternate.subList(0, alternate.size() - 1).stream()
                .allMatch(surface -> port.accessBoundary().occupied(surface.standingBody())));
    }
    @Test void mutableRouteOverlaysCannotPoisonCachedBootstrapOccupancy() {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:occupancy-cache"), 421L);
        var original = KnownPedestrianRouteKnowledge.staticOccupancy(bootstrap);
        assertTrue(!original.isEmpty());
        var changed = KnownPedestrianRouteKnowledge.staticOccupancy(bootstrap);
        changed.clear();
        assertEquals(original, KnownPedestrianRouteKnowledge.staticOccupancy(bootstrap));
        var other = FrontierBootstrapper.create(new WorldId("frontier:other-occupancy-cache"), 422L);
        KnownPedestrianRouteKnowledge.staticOccupancy(other).clear();
        assertEquals(original, KnownPedestrianRouteKnowledge.staticOccupancy(bootstrap));
    }
    @Test void clearwaterRoadHintsUseTheProjectedTopRatherThanTerrainUnderTheRoad() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:clearwater-road-support"), 20260918065L));
        var knowledge = KnownPedestrianRouteKnowledge.forSettlement(state, new SubjectId("settlement:7"), List.of());
        org.junit.jupiter.api.Assertions.assertSame(knowledge,
                KnownPedestrianRouteKnowledge.forSettlement(state.withActorBody(new SubjectId("resident:7-1"),
                        state.actorLocations().get(new SubjectId("resident:7-1")).body()),
                        new SubjectId("settlement:7"), List.of()),
                "actor-only changes must not recompile immutable ground/obstacles");
        BlockPosition road = new BlockPosition(115, 64, 14);
        assertTrue(FrontierRouteNetwork.footprint(state.bootstrap(), state.routeTopology()).surfaceCells().contains(road));
        assertEquals(new SurfaceAnchor(road), knowledge.supportAt(road.x(), road.z()));
        var recovered = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state));
        assertEquals(knowledge.supportAt(115, 14),
                KnownPedestrianRouteKnowledge.forSettlement(recovered, new SubjectId("settlement:7"), List.of())
                        .supportAt(115, 14));
    }
    @Test void crowdCannotTurnAServiceExitIntoPermanentKnownTerrain() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:known-pedestrian-crowd"), 20260918065L));
        Settlement settlement = initial.bootstrap().settlements().get(10);
        SubjectId actorId = settlement.residents().getFirst().id();
        SurfaceAnchor home = initial.actorLocations().get(actorId).supportingSurface();
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        FrontierWorldState atService = initial.withActorBody(actorId, port.serviceSurface().standingBody());
        MovementOrder order = new MovementOrder(actorId, actorId, 0L, 1L, List.of(home),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        SubjectId depotId = FrontierWorldState.depotId(settlement.id());
        List<SurfaceAnchor> baseline = KnownServiceExitNavigation.plannedPathFrom(atService, settlement.id(), depotId, order,
                atService.actorLocations().get(actorId).supportingSurface());
        assertTrue(baseline.size() > 2);

        var actors = new LinkedHashMap<>(atService.actorLocations());
        int index = 1;
        for (Resident resident : settlement.residents()) {
            if (resident.id().equals(actorId)) continue;
            actors.put(resident.id(), ActorLocation.standingOn(baseline.get(index), ActorKind.RESIDENT, UnitNavigationProfile.VILLAGER));
            index = index + 1 < baseline.size() - 1 ? index + 1 : 1;
        }
        FrontierWorldState crowded = atService.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        assertEquals(baseline, KnownServiceExitNavigation.plannedPathFrom(crowded, settlement.id(), depotId, order,
                crowded.actorLocations().get(actorId).supportingSurface()));
        assertEquals(baseline, KnownPedestrianRouteKnowledge.path(crowded, settlement.id(),
                port.serviceSurface(), order, List.of(new KnownPedestrianRouteKnowledge.Passage(depot,
                        KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS))));
        ActorMovement movement = new ActorMovement(order, 27_000L,
                new ActorMovementContext.ServiceExit(settlement.id(), depotId),
                crowded.actorExecutions().next(actorId, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.SERVICE_EXIT, actorId));
        FrontierWorldState moving = crowded.withChanges(FrontierWorldStateUpdate.begin()
                .actorMovements(Map.of(actorId, movement))
                .actorExecutions(crowded.actorExecutions().begin(movement.executionId(), 0L)));
        var action = ActorMovementProcess.progress(movement, 27_001L);
        var started = ActorMovementProcess.plan(moving, action, 27_001L);
        ActorMovementColdAdvanced event = assertInstanceOf(ActorMovementColdAdvanced.class,
                started.getFirst().payload());
        FrontierWorldState travelling = ActorMovementProcess.reduceColdAdvanced(moving, actorId, event);
        List<SurfaceAnchor> firstSegment = travelling.actorMovements().get(actorId)
                .coldTravel().orElseThrow().route();
        assertEquals(baseline.subList(0, firstSegment.size()), firstSegment);
        assertTrue(port.accessBoundary().cleared(firstSegment.getLast().standingBody()));

        FrontierWorldState physicallyBlocked = crowded.recordPhysicalDelta(new PhysicalDelta(
                home.support(), PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(),
                "test:known-pedestrian-hard-block"));
        assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                () -> KnownServiceExitNavigation.plannedPathFrom(physicallyBlocked, settlement.id(), depotId, order,
                    physicallyBlocked.actorLocations().get(actorId).supportingSurface()));
    }

    @Test void facilityPassageMustNameTheExactDeclaredFacility() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:known-pedestrian-passage"), 421L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SubjectId resident = settlement.residents().getFirst().id();
        SurfaceAnchor body = state.actorLocations().get(resident).supportingSurface();
        MovementOrder order = new MovementOrder(resident, resident, 0L, 1L, List.of(body),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        SettlementStructure forged = new SettlementStructure(depot.id(), depot.settlementId(),
                StructureKind.WORKSHOP, depot.anchor(), depot.facing());
        assertThrows(IllegalArgumentException.class, () -> KnownPedestrianRouteKnowledge.path(state,
                settlement.id(), body, order, List.of(new KnownPedestrianRouteKnowledge.Passage(forged,
                        KnownPedestrianRouteKnowledge.Passage.Reach.EXTERIOR))));
    }

    @Test void existingFacilityPortsShareTheSamePassageContract() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:known-pedestrian-port-reuse"), 421L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        for (StructureKind kind : List.of(StructureKind.HALL, StructureKind.DEPOT,
                StructureKind.WORKSHOP, StructureKind.INFIRMARY)) {
            SettlementStructure facility = settlement.structures().stream()
                    .filter(structure -> structure.kind() == kind).findFirst().orElseThrow();
            var route = KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(),
                    List.of(new KnownPedestrianRouteKnowledge.Passage(facility,
                            KnownPedestrianRouteKnowledge.Passage.Reach.EXTERIOR)));
            SurfaceAnchor exterior = FrontierTraversalPlan.facilityPort(facility).orElseThrow()
                    .exteriorApproach().getFirst();
            assertEquals(SettlementPedestrianGround.surveyedSupport(state.bootstrap(),
                    SettlementPedestrianGround.localSupports(state.bootstrap(), settlement.id()),
                    exterior.x(), exterior.z()), route.supportAt(exterior.x(), exterior.z()));
            MovementOrder order = new MovementOrder(resident, resident, 0L, 1L, List.of(exterior),
                    TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
            assertEquals(List.of(exterior), route.path(exterior, order));
        }
    }

    @Test void fieldOverlayUsesTheSameChangedServiceCellBarrier() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:known-pedestrian-field-overlay"), 421L));
        ResourceSite site = state.resourceSite(new SubjectId("site:1-wheat-field"));
        Settlement settlement = state.bootstrap().settlements().stream()
                .filter(candidate -> candidate.id().equals(site.settlementId())).findFirst().orElseThrow();
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        var access = ResourceStorageAccess.at(state, FrontierWorldState.depotId(site.settlementId()));
        SurfaceAnchor start = port.exteriorApproach();
        SubjectId resident = settlement.residents().getFirst().id();
        MovementOrder order = new MovementOrder(resident, resident, 0L, 1L,
                List.of(port.serviceSurface()), TraversalCapability.PEDESTRIAN,
                MovementOrder.ArrivalPolicy.EXACT_STATION);
        ResourceFieldCycle cycle = state.resourceSites().cycle(site.id());
        ResourceFieldCycle foreign = state.resourceSites().cycles().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(site.id())).findFirst().orElseThrow().getValue();
        assertThrows(IllegalArgumentException.class,
                () -> KnownPedestrianRouteKnowledge.forField(state, site, foreign, access, start));
        assertEquals(List.of(start, port.serviceSurface()),
                KnownPedestrianRouteKnowledge.forField(state, site, cycle, access, start).path(start, order));
        FrontierWorldState changed = state.recordPhysicalDelta(new PhysicalDelta(
                port.serviceSurface().support().offset(0, 1, 0), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:changed-field-service"));
        assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                () -> KnownPedestrianRouteKnowledge.forField(changed, site,
                        changed.resourceSites().cycle(site.id()), access, start).path(start, order));
    }
}
