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
        FrontierWorldState reserved = state.withHumanPopulation(state.humanPopulation().withMeal(departing));
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
        FrontierWorldState inFlight = reserved.withHumanPopulation(reserved.humanPopulation().withMeal(meal.withColdTravel(travel)));
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
                ResidentMeal.Phase.MOVE, 24_000L, Optional.empty());
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
                Optional.empty(), ResidentMeal.Phase.MOVE, 24_000L, Optional.empty());
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
        actors.put(resident, ActorLocation.standingOn(side));
        FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        ResidentMeal meal = new ResidentMeal(resident, settlement.id(), depot, side,
                ReferenceContainerCustody.scopeId(depot), new SubjectId("custody:resident-meal-side"),
                new FoodPortion(FoodCatalog.BREAD, 1_000, java.util.Map.of(new SubjectId("lot:resident-meal-side"), 1)), new SubjectId("claim:resident-meal-side"),
                Optional.empty(), ResidentMeal.Phase.MOVE, 22_639L, Optional.empty());

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
                Optional.empty(), ResidentMeal.Phase.MOVE, 6_000L, Optional.empty());
        List<SurfaceAnchor> route = ResidentMealKnownNavigation.path(state, meal);
        assertEquals(state.actorLocations().get(resident).supportingSurface(), route.getFirst());
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow());
        assertTrue(port.accessBoundary().cleared(route.getLast().standingBody()));
        var waitingActors = new java.util.LinkedHashMap<>(state.actorLocations());
        waitingActors.put(resident, ActorLocation.standingOn(route.getLast()));
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
                Optional.empty(), ResidentMeal.Phase.MOVE, 24_000L, Optional.empty());
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
                meal.retainedWorkOwner(), ResidentMeal.Phase.RETURN, meal.startedAtTick(), Optional.empty());
        var serviceActors = new java.util.LinkedHashMap<>(state.actorLocations());
        serviceActors.put(resident, ActorLocation.standingOn(service));
        List<SurfaceAnchor> exit = ResidentMealKnownNavigation.returnPath(
                state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(serviceActors)), returning);
        assertEquals(service, exit.getFirst());
        assertTrue(exit.contains(SettlementDepotServicePort.forDepot(depotStructure).exteriorApproach()));
        assertEquals(returning.clearingSurface(), exit.getLast());
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
