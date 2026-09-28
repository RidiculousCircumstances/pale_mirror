package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidentMealKnownNavigationTest {
    @Test void idleResidentAndWorkshopWorkerUseSameKnownDepotGoal() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-meal-route"), 422L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        ResidentMeal meal = new ResidentMeal(resident, settlement.id(), depot,
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
        SettlementStructure workshop = settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow();
        SurfaceAnchor work = SettlementWorkshopServicePort.forWorkshop(workshop).workStation();
        List<SurfaceAnchor> fromWorkshop = ResidentMealKnownNavigation.pathFrom(state, meal, work);
        assertEquals(work, fromWorkshop.getFirst());
        assertEquals(service, fromWorkshop.getLast());
        assertTrue(fromWorkshop.size() > 1);
    }
}
