package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidentMealKnownNavigationTest {
    @Test void courtesyDepartureFromPrivateDepotStationHasTheSamePlanningAndReplayPermissions() throws Exception {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:r29-courtesy-meal"), 20260918065L));
        var settlement = initial.bootstrap().settlements().get(6);
        var actor = new SubjectId("resident:7-12");
        var start = SurfaceAnchor.at(136, 64, 14);
        var meal = testMeal(initial, settlement, actor, FrontierWorldState.depotId(settlement.id()), start);
        var state = withMeal(initial.withActorBody(actor, start.standingBody()), meal);
        var step = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess
                .planColdStep(state, actor, 90_596L).orElseThrow();
        assertEquals(start, step.plannedRoute().orElseThrow().route().getFirst());
        var codecs = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs();
        var decoded = (ResidentMealColdStep) codecs.decode(step.type(), codecs.encode(step));
        try (var empty = new io.farfrontier.palemirror.frontier.v3.model.navigation.CooperativePedestrianPlanner();
             var binding = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind(empty)) {
            var retained = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.reduceColdStep(state, actor, decoded);
            assertTrue(retained.humanPopulation().meals().get(actor).coldTravel().isPresent());
            assertEquals(0, empty.pendingCount());
        }
        var damaged = state.recordPhysicalDelta(new PhysicalDelta(start.support(),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:departure-floor-loss"));
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess
                .reduceColdStep(damaged, actor, decoded));
        // An incumbent makes this an exit followed by a waiting approach, not a direct entrance.
        var other = new SubjectId("resident:7-6");
        var port = SettlementServiceAccessPoints.depotPort(state, settlement.id());
        var incumbent = testMeal(state, settlement, other, meal.depotId(), port.exteriorApproach());
        var waiting = withMeal(state.withActorBody(other, port.serviceSurface().standingBody()), incumbent);
        assertTrue(!ResidentMealServiceAccess.available(waiting, meal.depotId(), actor));
        var waitingStep = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess
                .planColdStep(waiting, actor, 90_596L).orElseThrow();
        var accepted = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess
                .reduceColdStep(waiting, actor, waitingStep);
        assertTrue(accepted.humanPopulation().meals().get(actor).coldTravel().isPresent());
        var route = waitingStep.plannedRoute().orElseThrow().route();
        assertTrue(port.accessBoundary().cleared(route.getLast().standingBody()));
        assertTrue(port.accessBoundary().allowsWaitingRoute(PedestrianLocalDeparture.publicContinuation(waiting, route)));
        var reentry = new java.util.ArrayList<>(route);
        reentry.add(port.serviceSurface());
        reentry.add(route.getLast());
        assertThrows(IllegalArgumentException.class,
                () -> ResidentMealKnownNavigation.requireMovementRoute(waiting, meal, reentry));
    }

    @Test void raisedWorkshopMealDepartureCanBeAcceptedAndReplayedWithoutInventingSupport() throws Exception {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:r28-workshop-meal"), 20260918065L));
        var settlement = initial.bootstrap().settlements().get(1);
        var actor = new SubjectId("resident:2-9");
        var workshop = settlement.structures().stream().filter(value -> value.kind() == StructureKind.WORKSHOP)
                .findFirst().orElseThrow();
        var port = SettlementWorkshopServicePort.forWorkshop(workshop);
        assertEquals(SurfaceAnchor.at(-142, 64, -324), port.workStation());
        assertEquals(SurfaceAnchor.at(-142, 64, -325), port.inputStation());
        var meal = testMeal(initial, settlement, actor, FrontierWorldState.depotId(settlement.id()), port.workStation());
        var state = withMeal(initial.withActorBody(actor, port.workStation().standingBody()), meal);
        var step = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess
                .planColdStep(state, actor, 45_136L).orElseThrow();
        assertTrue(step.plannedRoute().orElseThrow().route().contains(port.inputStation()));
        var codecs = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs();
        var decoded = (ResidentMealColdStep) codecs.decode(step.type(), codecs.encode(step));
        try (var empty = new io.farfrontier.palemirror.frontier.v3.model.navigation.CooperativePedestrianPlanner();
             var binding = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind(empty)) {
            var retained = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.reduceColdStep(state, actor, decoded);
            assertTrue(retained.humanPopulation().meals().get(actor).coldTravel().isPresent());
            assertEquals(0, empty.pendingCount(), "replay validates the accepted route without starting a new search");
        }
        var damaged = state.recordPhysicalDelta(new PhysicalDelta(port.inputStation().support(),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:workshop-input-floor-loss"));
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess
                .reduceColdStep(damaged, actor, decoded));
        var moved = state.withActorBody(actor, port.inputStation().standingBody());
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess
                .reduceColdStep(moved, actor, decoded));
    }

    @Test void distantReturnedCourierGetsADeferredRouteAndReplayNeedsNoPlannerCache() throws Exception {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:distant-meal"), 20260918065L));
        var settlement = state.bootstrap().settlements().get(6);
        var actor = new SubjectId("resident:7-12");
        var depot = FrontierWorldState.depotId(settlement.id());
        var meal = testMeal(state, settlement, actor, depot, SurfaceAnchor.at(134, 63, 16));
        state = withMeal(state.withActorBody(actor, SurfaceAnchor.at(-111, 64, 14).standingBody()), meal);
        var directState = state;
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> ResidentMealKnownNavigation.path(directState, meal));
        ResidentMealColdStep step;
        try (var planner = new io.farfrontier.palemirror.frontier.v3.model.navigation.CooperativePedestrianPlanner();
             var binding = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind(planner)) {
            assertTrue(io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.planColdStep(state, actor, 24_001L).isEmpty());
            Optional<ResidentMealColdStep> candidate = Optional.empty();
            for (int i = 0; i < 256 && candidate.isEmpty(); i++) {
                planner.advance(io.farfrontier.palemirror.frontier.v3.model.navigation.HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
                candidate = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.planColdStep(state, actor, 24_001L);
            }
            step = candidate.orElseThrow();
            assertTrue(step.plannedRoute().isPresent());
            assertTrue(step.plannedRoute().orElseThrow().route().size() > 200);
        }
        var codecs = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs();
        var decoded = (ResidentMealColdStep) codecs.decode(step.type(), codecs.encode(step));
        assertEquals(step, decoded);
        // A cold/empty new calculation owner cannot make replay depend on search readiness.
        try (var empty = new io.farfrontier.palemirror.frontier.v3.model.navigation.CooperativePedestrianPlanner();
             var binding = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind(empty)) {
            var retained = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.reduceColdStep(state, actor, decoded);
            assertTrue(retained.humanPopulation().meals().get(actor).coldTravel().isPresent());
            assertEquals(0, empty.pendingCount());
            var travel = retained.humanPopulation().meals().get(actor).coldTravel().orElseThrow();
            var arrival = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.planColdStep(retained, actor, travel.arrivalTick()).orElseThrow();
            var arrived = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.reduceColdStep(retained, actor, arrival);
            assertEquals(travel.route().getLast(), arrived.actorLocations().get(actor).supportingSurface());
            var changed = state.recordPhysicalDelta(new PhysicalDelta(step.plannedRoute().orElseThrow().route().get(3).support(),
                    PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:changed-route"));
            assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.reduceColdStep(changed, actor, decoded));
            var moved = state.withActorBody(actor, step.plannedRoute().orElseThrow().route().get(1).standingBody());
            assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.reduceColdStep(moved, actor, decoded));
        }
    }
    @Test void idleDestinationsCannotBuildADenseRingAroundAnotherResident() {
        var occupied = java.util.Set.of(SurfaceAnchor.at(128, 63, 12));
        assertTrue(!ServiceAreaDestinations.hasStandingClearance(SurfaceAnchor.at(129, 63, 12), occupied));
        assertTrue(!ServiceAreaDestinations.hasStandingClearance(SurfaceAnchor.at(129, 64, 13), occupied));
        assertTrue(ServiceAreaDestinations.hasStandingClearance(SurfaceAnchor.at(130, 63, 12), occupied));
    }
    @Test void concurrentFutureMealReservationsCannotHoldTheServiceExitHostage() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:concurrent-meal-exit"), 20260918065L));
        var settlement = initial.bootstrap().settlements().get(6);
        var depot = FrontierWorldState.depotId(settlement.id());
        var port = SettlementServiceAccessPoints.depotPort(initial, settlement.id());
        var actor = new SubjectId("resident:7-13");
        var state = initial.withActorBody(actor, port.serviceSurface().standingBody());
        var clearing = testMeal(state, settlement, actor, depot, SurfaceAnchor.at(135, 63, 11))
                .advance(ResidentMeal.Phase.TAKE).advance(ResidentMeal.Phase.CLEAR_ACCESS);
        state = withMeal(state, clearing);
        var actualExits = KnownServiceExitNavigation.exitStations(state, settlement.id(), depot, actor, port.serviceSurface());
        assertTrue(!actualExits.isEmpty());
        int index = 0;
        for (var exit : actualExits) {
            SubjectId waiter = settlement.residents().stream().map(Resident::id)
                    .filter(id -> !id.equals(actor)).toList().get(index++);
            var meal = testMeal(state, settlement, waiter, depot, exit);
            state = withMeal(state, meal);
        }
        assertTrue(!KnownServiceExitNavigation.exitStations(state, settlement.id(), depot, actor, port.serviceSurface()).isEmpty(),
                "future eating targets are not committed passage permits");
        var route = ResidentMealKnownNavigation.returnPath(state, clearing);
        assertTrue(port.accessBoundary().cleared(route.getLast().standingBody()));
        var point = SettlementServiceAccessPoints.forSettlement(state, settlement.id()).getFirst();
        assertTrue(point.egressSurfaces().contains(route.getLast()));
        assertTrue(ServiceAreaDestinations.temporary(point, route.getLast()),
                "a resident must clear the escape perimeter after eating");
        assertTrue(point.waitingSurfaces().stream().noneMatch(point.egressSurfaces()::contains));
        assertTrue(!ResidentMealServiceAccess.available(state, depot, settlement.residents().getFirst().id()));
        state = state.withActorBody(actor, route.getLast().standingBody());
        assertTrue(ResidentMealServiceAccess.available(state, depot, settlement.residents().getFirst().id()),
                "a witnessed exit, not home arrival, releases the turn");
        // Actual bodies remain exclusive even though deferred preferred targets do not.
        var blocked = state.withActorBody(actor, port.serviceSurface().standingBody());
        for (int i = 0; i < actualExits.size(); i++) {
            var waiter = settlement.residents().stream().map(Resident::id).filter(id -> !id.equals(actor)).toList().get(i);
            blocked = blocked.withActorBody(waiter, actualExits.get(i).standingBody());
        }
        assertTrue(KnownServiceExitNavigation.exitStations(blocked, settlement.id(), depot, actor, port.serviceSurface()).isEmpty());
        assertEquals(actualExits, KnownServiceExitNavigation.supportedExitStations(blocked,
                settlement.id(), depot, port.serviceSurface()),
                "HOT must expose supported occupied goals to traffic/courtesy; COLD may not enter them");
        var damaged = blocked;
        for (var exit : actualExits) {
            damaged = damaged.recordPhysicalDelta(new PhysicalDelta(exit.support(), PhysicalDeltaKind.UNKNOWN_SCAR,
                    Optional.empty(), Optional.empty(), "test:damaged-service-exit"));
        }
        assertTrue(KnownServiceExitNavigation.supportedExitStations(damaged,
                settlement.id(), depot, port.serviceSurface()).isEmpty(),
                "courtesy must not turn a damaged physical exit into a traversable goal");
    }

    @Test void authoredThresholdAndProtectedExitReplaceStaleWaitingPocketInBothModes() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:meal-egress-geometry"), 20260918065L));
        var settlement = state.bootstrap().settlements().get(6);
        var depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        var port = SettlementDepotServicePort.forDepot(depot);
        var knowledge = KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), List.of(
                new KnownPedestrianRouteKnowledge.Passage(depot, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
        assertEquals(port.thresholdSurface(), knowledge.supportAt(port.thresholdSurface().x(), port.thresholdSurface().z()));
        var exits = ServiceClearanceTargets.egressRegion(port.accessBoundary(), knowledge);
        assertTrue(exits.stream().allMatch(surface -> knowledge.traversable(List.of(surface))));
        assertTrue(exits.stream().noneMatch(surface -> surface.x() == 136 && surface.y() == 63),
                "terrain beneath the floor/wall cannot be an exit station");
        var actor = new SubjectId("resident:7-1");
        var stalePocket = SurfaceAnchor.at(135, 63, 16);
        assertTrue(exits.contains(stalePocket));
        state = state.withActorBody(actor, stalePocket.standingBody());
        var meal = testMeal(state, settlement, actor, FrontierWorldState.depotId(settlement.id()), stalePocket);
        assertTrue(!ResidentMealKnownNavigation.atWaitingPocket(state, meal), "COLD must not hold an obsolete escape-blocking pocket");
        assertTrue(ResidentMealKnownNavigation.waitingStationAvailable(state, meal, port.stations().getFirst()),
                "an authorized entrance may use intermediate boundary stations without parking there");
        var occupant = new SubjectId("resident:7-13");
        var first = testMeal(state, settlement, occupant, meal.depotId(), stalePocket);
        state = withMeal(state.withActorBody(occupant, port.serviceSurface().standingBody()), first);
        assertTrue(!ResidentMealKnownNavigation.waitingStationAvailable(state, meal, stalePocket), "HOT must invalidate its cached pocket without an entrance turn");
        var route = ResidentMealKnownNavigation.path(state, meal);
        assertTrue(!route.getLast().equals(stalePocket));
        assertTrue(!exits.contains(route.getLast()));
        var point = SettlementServiceAccessPoints.forSettlement(state, settlement.id()).getFirst();
        var rest = ServiceAreaDestinations.select(List.of(point), actor, stalePocket, knowledge, java.util.Set.of()).orElseThrow();
        assertTrue(!ServiceAreaDestinations.temporary(point, rest));
    }

    @Test void occupiedExitIsNotTheOnlyGoalAndParkingDoesNotOwnClearance() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:exit-region"), 20260918065L));
        var settlement = initial.bootstrap().settlements().get(6);
        var actor = settlement.residents().getFirst().id();
        var other = settlement.residents().get(1).id();
        var depot = FrontierWorldState.depotId(settlement.id());
        var port = SettlementServiceAccessPoints.depotPort(initial, settlement.id());
        var state = initial.withActorBody(actor, port.serviceSurface().standingBody());
        var exits = KnownServiceExitNavigation.exitStations(state, settlement.id(), depot, actor, port.serviceSurface());
        assertTrue(exits.size() > 1, "the service is an exit region, not one exact parking cell");
        var blocked = state.withActorBody(other, exits.getFirst().standingBody());
        var alternatives = KnownServiceExitNavigation.exitStations(blocked, settlement.id(), depot, actor, port.serviceSurface());
        assertTrue(!alternatives.contains(exits.getFirst()));
        assertTrue(!alternatives.isEmpty());
        assertTrue(alternatives.stream().allMatch(surface -> port.accessBoundary().cleared(surface.standingBody())));
        var arbitraryParking = SurfaceAnchor.at(130, 63, 30);
        var moving = testMeal(blocked, settlement, actor, depot, arbitraryParking);
        var clearing = new ResidentMeal(actor, settlement.id(), depot, arbitraryParking,
                moving.sourceAccountId(), moving.actorAccountId(), moving.portion(), moving.claimId(),
                moving.retainedWorkOwner(), ResidentMeal.Phase.CLEAR_ACCESS, moving.startedAtTick(), Optional.empty(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(actor, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEAL, moving.claimId(), 1L));
        var path = ResidentMealKnownNavigation.clearancePathFrom(blocked, clearing, port.serviceSurface());
        assertTrue(port.accessBoundary().cleared(path.getLast().standingBody()));
        assertTrue(!path.getLast().equals(arbitraryParking));
    }
    @Test void waitingApproachCannotOccupyAnotherMealsReservedClearanceAndHotCanRejectOccupiedPockets() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:meal-waiting-clearance"), 20260918065L));
        Settlement settlement = state.bootstrap().settlements().get(6);
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId other = settlement.residents().get(1).id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        ResidentMeal meal = testMeal(state, settlement, resident, depot,
                state.actorLocations().get(resident).supportingSurface());
        SurfaceAnchor originalPocket = ResidentMealKnownNavigation.path(state, meal).getLast();
        ResidentMeal departing = testMeal(state, settlement, other, depot, originalPocket);
        FrontierWorldState reserved = state.withChanges(FrontierWorldStateUpdate.begin()
                .humanPopulation(state.humanPopulation().withMeal(departing))
                .actorExecutions(state.actorExecutions().begin(departing.executionId(), 0L)));
        assertTrue(!ResidentMealKnownNavigation.waitingStationAvailable(reserved, meal, originalPocket));
        SurfaceAnchor alternative = ResidentMealKnownNavigation.path(reserved, meal).getLast();
        assertTrue(!alternative.equals(originalPocket), "arrival must not steal a reserved departure destination");
        assertTrue(!ResidentMealKnownNavigation.pathFrom(reserved, meal,
                reserved.actorLocations().get(resident).supportingSurface(), station -> !station.equals(alternative))
                .getLast().equals(alternative), "HOT physical availability must choose another waiting point");
        assertThrows(io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable.class,
                () -> ResidentMealKnownNavigation.pathFrom(reserved, meal,
                        reserved.actorLocations().get(resident).supportingSurface(), station -> false));

        List<SurfaceAnchor> route = ResidentMealKnownNavigation.path(state, meal);
        var order = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(resident, resident,
                FrontierWireTags.tag(meal.phase()), 1L, List.of(originalPocket), TraversalCapability.PEDESTRIAN,
                io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
        var travel = new io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute(order, route, 24_001L, 20L, 1L);
        FrontierWorldState inFlight = reserved.withChanges(FrontierWorldStateUpdate.begin()
                .humanPopulation(reserved.humanPopulation().withMeal(meal.withColdTravel(travel)))
                .actorExecutions(reserved.actorExecutions().begin(meal.executionId(), 0L)));
        var interrupted = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess
                .planColdStep(inFlight, resident, travel.departedAtTick() + 1L).orElseThrow();
        assertTrue(interrupted.nextSurface().isEmpty(), "new exit claim invalidates an in-flight waiting destination");
        FrontierWorldState replannable = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess
                .reduceColdStep(inFlight, resident, interrupted);
        assertTrue(replannable.humanPopulation().meals().get(resident).coldTravel().isEmpty());
        assertEquals(meal.claimId(), replannable.humanPopulation().meals().get(resident).claimId(),
                "replanning preserves the portion claim, it does not cancel or duplicate a meal");
    }

    private static ResidentMeal testMeal(FrontierWorldState state, Settlement settlement,
                                         SubjectId resident, SubjectId depot, SurfaceAnchor clearing) {
        return new ResidentMeal(resident, settlement.id(), depot, clearing,
                ReferenceContainerCustody.scopeId(depot), new SubjectId("custody:resident-meal-" + resident.value().replace(':', '-')),
                new FoodPortion(FoodCatalog.BREAD, 1_000, java.util.Map.of(new SubjectId("lot:meal-waiting"), 1)),
                new SubjectId("claim:meal-waiting-" + resident.value().replace(':', '-')), Optional.empty(),
                ResidentMeal.Phase.MOVE, 24_000L, Optional.empty(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(resident, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEAL,
                        new SubjectId("claim:meal-waiting-" + resident.value().replace(':', '-')), 1L));
    }

    private static FrontierWorldState withMeal(FrontierWorldState state, ResidentMeal meal) {
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .humanPopulation(state.humanPopulation().withMeal(meal))
                .actorExecutions(state.actorExecutions().begin(meal.executionId(), meal.executionId().generation() - 1L)));
    }

    @Test void admittedShortEntranceStillRespectsAChangedPhysicalServiceCell() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-changed-service"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow());
        FrontierWorldState atEntrance = initial.withActorBody(resident, port.exteriorApproach().standingBody());
        ResidentMeal meal = new ResidentMeal(resident, settlement.id(), depot, port.exteriorApproach(),
                ReferenceContainerCustody.scopeId(depot), new SubjectId("custody:resident-meal-changed-service"),
                new FoodPortion(FoodCatalog.BREAD, 1_000, java.util.Map.of(new SubjectId("lot:meal-changed-service"), 1)), new SubjectId("claim:meal-changed-service"),
                Optional.empty(), ResidentMeal.Phase.MOVE, 24_000L, Optional.empty(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(resident, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEAL, new SubjectId("claim:meal-changed-service"), 1L));
        assertEquals(port.serviceSurface(), ResidentMealKnownNavigation.path(atEntrance, meal).getLast());
        FrontierWorldState blocked = atEntrance.recordPhysicalDelta(new PhysicalDelta(
                port.serviceSurface().support(), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:changed-depot-service"));
        assertThrows(io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable.class,
                () -> ResidentMealKnownNavigation.path(blocked, meal));
    }

    @Test void residentReleasedAtDepotSideStationCanCompleteColdMealApproach() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-side-station"), 20260918065L));
        Settlement settlement = initial.bootstrap().settlements().get(6);
        SubjectId resident = new SubjectId("resident:7-21");
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow());
        SurfaceAnchor side = port.stations().get(3);
        assertEquals(SurfaceAnchor.at(134, 64, 13), side);
        var actors = new java.util.LinkedHashMap<>(initial.actorLocations());
        actors.put(resident, ActorLocation.standingOn(side, ActorKind.RESIDENT));
        FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        ResidentMeal meal = new ResidentMeal(resident, settlement.id(), depot, side,
                ReferenceContainerCustody.scopeId(depot), new SubjectId("custody:resident-meal-side"),
                new FoodPortion(FoodCatalog.BREAD, 1_000, java.util.Map.of(new SubjectId("lot:resident-meal-side"), 1)), new SubjectId("claim:resident-meal-side"),
                Optional.empty(), ResidentMeal.Phase.MOVE, 22_639L, Optional.empty(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(resident, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEAL, new SubjectId("claim:resident-meal-side"), 1L));

        List<SurfaceAnchor> route = ResidentMealKnownNavigation.path(state, meal);

        assertEquals(side, route.getFirst());
        assertEquals(port.serviceSurface(), route.getLast());
        assertTrue(route.size() > 1);
    }

    @Test void sixthSettlementResidentHasKnownRouteToItsDepot() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-route-six"), 41L));
        Settlement settlement = state.bootstrap().settlements().get(5);
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        ResidentMeal meal = new ResidentMeal(resident, settlement.id(), depot,
                state.actorLocations().get(resident).supportingSurface(),
                ReferenceContainerCustody.scopeId(depot), new SubjectId("custody:resident-meal-six"),
                new FoodPortion(FoodCatalog.BREAD, 1_000, java.util.Map.of(new SubjectId("lot:resident-meal-six"), 1)), new SubjectId("claim:resident-meal-six"),
                Optional.empty(), ResidentMeal.Phase.MOVE, 6_000L, Optional.empty(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(resident, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEAL, new SubjectId("claim:resident-meal-six"), 1L));
        List<SurfaceAnchor> route = ResidentMealKnownNavigation.path(state, meal);
        assertEquals(state.actorLocations().get(resident).supportingSurface(), route.getFirst());
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow());
        assertTrue(port.accessBoundary().cleared(route.getLast().standingBody()));
        var waitingActors = new java.util.LinkedHashMap<>(state.actorLocations());
        waitingActors.put(resident, ActorLocation.standingOn(route.getLast(), ActorKind.RESIDENT));
        List<SurfaceAnchor> shortEntry = ResidentMealKnownNavigation.path(
                state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(waitingActors)), meal);
        assertEquals(port.serviceSurface(), shortEntry.getLast());
        assertTrue(route.size() > 1);
    }

    @Test void idleResidentAndWorkshopWorkerUseSameKnownDepotGoal() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-route"), 422L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        ResidentMeal meal = new ResidentMeal(resident, settlement.id(), depot,
                state.actorLocations().get(resident).supportingSurface(),
                ReferenceContainerCustody.scopeId(depot), new SubjectId("custody:resident-meal-route"),
                new FoodPortion(FoodCatalog.BREAD, 1_000, java.util.Map.of(new SubjectId("lot:resident-meal-route"), 1)), new SubjectId("claim:resident-meal-route"),
                Optional.empty(), ResidentMeal.Phase.MOVE, 24_000L, Optional.empty(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(resident, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEAL, new SubjectId("claim:resident-meal-route"), 1L));
        SettlementStructure depotStructure = settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SurfaceAnchor service = SettlementDepotServicePort.forDepot(depotStructure).serviceSurface();
        List<SurfaceAnchor> fromIdle = ResidentMealKnownNavigation.path(state, meal);
        assertEquals(state.actorLocations().get(resident).supportingSurface(), fromIdle.getFirst());
        assertTrue(SettlementDepotServicePort.forDepot(depotStructure).accessBoundary()
                .cleared(fromIdle.getLast().standingBody()));
        assertTrue(fromIdle.size() > 1);
        ResidentMeal returning = new ResidentMeal(resident, settlement.id(), depot,
                state.actorLocations().get(resident).supportingSurface(),
                meal.sourceAccountId(), meal.actorAccountId(), meal.portion(), meal.claimId(),
                meal.retainedWorkOwner(), ResidentMeal.Phase.CLEAR_ACCESS, meal.startedAtTick(), Optional.empty(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(resident, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEAL, meal.claimId(), 1L));
        var serviceActors = new java.util.LinkedHashMap<>(state.actorLocations());
        serviceActors.put(resident, ActorLocation.standingOn(service, ActorKind.RESIDENT));
        List<SurfaceAnchor> exit = ResidentMealKnownNavigation.returnPath(
                state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(serviceActors)), returning);
        assertEquals(service, exit.getFirst());
        assertTrue(exit.contains(SettlementDepotServicePort.forDepot(depotStructure).exteriorApproach()));
        assertTrue(SettlementDepotServicePort.forDepot(depotStructure).accessBoundary().cleared(exit.getLast().standingBody()),
                "clearance ends at a safe exit, not at the resident's former parking point");
        SettlementStructure workshop = settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow();
        SurfaceAnchor work = SettlementWorkshopServicePort.forWorkshop(workshop).workStation();
        List<SurfaceAnchor> fromWorkshop = ResidentMealKnownNavigation.pathFrom(state, meal, work);
        assertEquals(work, fromWorkshop.getFirst());
        assertTrue(SettlementDepotServicePort.forDepot(depotStructure).accessBoundary()
                .cleared(fromWorkshop.getLast().standingBody()));
        assertTrue(fromWorkshop.size() > 1);
    }
}
