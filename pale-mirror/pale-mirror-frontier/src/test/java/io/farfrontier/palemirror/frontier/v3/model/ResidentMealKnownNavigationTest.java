package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidentMealKnownNavigationTest {
    @Test void sixthSettlementResidentHasKnownRouteToItsDepot() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-route-six"), 41L));
        Settlement settlement = state.bootstrap().settlements().get(5);
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        ResidentMeal meal = new ResidentMeal(resident, settlement.id(), depot,
                state.actorLocations().get(resident).supportingSurface(),
                ReferenceContainerCustody.scopeId(depot), new SubjectId("custody:resident-meal-six"),
                new SubjectId("lot:resident-meal-six"), new SubjectId("claim:resident-meal-six"),
                Optional.empty(), ResidentMeal.Phase.MOVE, 6_000L, Optional.empty());
        List<SurfaceAnchor> route = ResidentMealKnownNavigation.path(state, meal);
        assertEquals(state.actorLocations().get(resident).supportingSurface(), route.getFirst());
        assertEquals(SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow()).serviceSurface(), route.getLast());
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
                new SubjectId("lot:resident-meal-route"), new SubjectId("claim:resident-meal-route"),
                Optional.empty(), ResidentMeal.Phase.MOVE, 24_000L, Optional.empty());
        SettlementStructure depotStructure = settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SurfaceAnchor service = SettlementDepotServicePort.forDepot(depotStructure).serviceSurface();
        List<SurfaceAnchor> fromIdle = ResidentMealKnownNavigation.path(state, meal);
        assertEquals(state.actorLocations().get(resident).supportingSurface(), fromIdle.getFirst());
        assertEquals(service, fromIdle.getLast());
        assertTrue(fromIdle.size() > 1);
        ResidentMeal returning = new ResidentMeal(resident, settlement.id(), depot,
                state.actorLocations().get(resident).supportingSurface(),
                meal.sourceAccountId(), meal.actorAccountId(), meal.lotId(), meal.claimId(),
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
        assertEquals(service, fromWorkshop.getLast());
        assertTrue(fromWorkshop.size() > 1);
    }
}
