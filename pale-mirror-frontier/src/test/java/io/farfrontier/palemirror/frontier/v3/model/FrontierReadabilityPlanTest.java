package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierReadabilityPlanTest {
    @Test
    void givesEveryFunctionalBuildingOrganAndResourceSiteOneStablePlayerFacingBoard() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-plan"), 91L));
        FrontierReadabilityPlan plan = FrontierReadabilityPlan.compile(state);
        int expected = state.bootstrap().settlements().stream().mapToInt(value -> value.structures().size()).sum()
                + state.bootstrap().hive().organs().size() + state.resourceSites().sites().size() + 1;
        assertEquals(expected, plan.boards().size());
        FrontierObjectBoard workshop = plan.boards().get(state.bootstrap().settlements().getFirst().structures().stream()
                .filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow().id());
        assertEquals(FrontierObjectBoard.Tone.SETTLEMENT, workshop.tone());
        assertTrue(workshop.text().contains("WORKSHOP"));
        assertTrue(workshop.text().endsWith("OPERATIONAL"));
        ResourceSite field = FrontierResourceSitePlan.compile(state.bootstrap()).values().iterator().next();
        FrontierObjectBoard fieldBoard = plan.boards().get(field.id());
        assertEquals(FrontierObjectBoard.Tone.SETTLEMENT, fieldBoard.tone());
        assertTrue(fieldBoard.text().contains("WHEAT FIELD"));
        assertTrue(fieldBoard.text().contains("PREPARING SOIL"));
        assertTrue(!fieldBoard.text().contains(field.id().value()));
        FrontierObjectBoard routeBoard = plan.boards().get(FrontierRouteNetwork.OWNER);
        assertEquals(FrontierObjectBoard.Tone.SETTLEMENT, routeBoard.tone());
        assertTrue(routeBoard.text().contains("FRONTIER ROUTES"));
        assertTrue(routeBoard.text().endsWith("ACTIVE · 12 SETTLEMENTS"));
        assertEquals(FrontierRouteNetwork.maintenanceContainerPosition(state.bootstrap()).offset(0, 3, -3), routeBoard.position());
        assertEquals(plan.boards(), FrontierReadabilityPlan.compile(state).boards());
    }

    @Test
    void reportsDamagedAndDisabledObjectsWithoutOpaqueIdentifiers() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-condition"), 91L));
        SettlementStructure structure = state.bootstrap().settlements().getFirst().structures().getFirst();
        FrontierWorldState damaged = state.withStructureCondition(structure.id(), StructureCondition.DAMAGED);
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(damaged).boards().get(structure.id());
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("DAMAGED · REPAIR NEEDED"));
        assertTrue(!board.text().contains(structure.id().value()));
    }

    @Test
    void makesCanonicalInfectionContactVisibleOnTheAffectedHiveOrgan() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-infection"), 91L));
        HiveOrgan heart = state.bootstrap().hive().organs().stream().filter(organ -> organ.kind() == HiveOrganKind.HEART).findFirst().orElseThrow();
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(state).boards().get(heart.id());
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("INFECTION · SATURATED"));
        assertTrue(!board.text().contains(heart.id().value()));
    }

    @Test
    void makesAConflictedFieldLocallyVisibleAsARepairableWarning() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:field-board-conflict"), 91L));
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).values().iterator().next();
        FrontierWorldState conflicted = state.withResourceSites(state.resourceSites().replace(state.resourceSites().site(site.id()).conflicted()));
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(conflicted).boards().get(site.id());
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("DAMAGED · REPAIR NEEDED"));
        assertEquals(site.cropSlots().getFirst().offset(4, 3, -2), board.position());
    }

    @Test
    void makesAKnownRouteSurfaceLossLocallyVisibleAsAPatrolWarning() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:route-board-conflict"), 91L));
        BlockPosition lost = FrontierRouteNetwork.surfaceCells(state.bootstrap()).iterator().next();
        java.util.Map<BlockPosition, PhysicalDelta> deltas = new java.util.LinkedHashMap<>(state.physicalDeltas());
        deltas.put(lost, new PhysicalDelta(lost, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(FrontierRouteNetwork.OWNER), java.util.Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "test route loss"));
        FrontierWorldState damaged = state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), deltas, state.ambientLeases());
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(damaged).boards().get(FrontierRouteNetwork.OWNER);
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("ROUTE DAMAGE · PATROL NEEDED"));
    }
}
