package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierGrayboxPlanTest {
    @Test
    void fullBootstrapCompilesAStableAttributablePlan() {
        FrontierWorldState state = initial();
        FrontierGrayboxPlan first = FrontierGrayboxPlan.compile(state);
        FrontierGrayboxPlan second = FrontierGrayboxPlan.compile(state);

        assertEquals(first.cells(), second.cells());
        assertTrue(first.cells().size() > 10_000);
        assertTrue(first.cells().values().stream().anyMatch(cell -> cell.ownerId().value().equals("structure:1-depot")
                && cell.material() == GrayboxMaterial.DEPOT));
        assertTrue(first.cells().values().stream().anyMatch(cell -> cell.ownerId().value().equals("organ:west-heart")
                && cell.semanticPart() == GrayboxSemanticPart.HIVE_TISSUE));
        assertTrue(first.cells().values().stream().anyMatch(cell -> cell.material() == GrayboxMaterial.ROUTE));
        assertEquals(state.infection(), first.infection());
    }

    @Test
    void destroyedStructureHasNoDesiredCellsButOtherOwnersRemain() {
        FrontierWorldState state = initial().withStructureCondition(new io.farfrontier.palemirror.frontier.v3.api.SubjectId("structure:1-workshop"), StructureCondition.DESTROYED);
        FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);

        assertFalse(plan.cells().values().stream().anyMatch(cell -> cell.ownerId().value().equals("structure:1-workshop")));
        assertTrue(plan.cells().values().stream().anyMatch(cell -> cell.ownerId().value().equals("structure:1-hall")));
    }

    @Test
    void cargoOperationUsesTheSameRouteNetworkAsTheVisibleCorridor() {
        FrontierWorldState state = initial();
        var waypoints = FrontierRouteNetwork.supplyWaypoints(state.bootstrap(), state.bootstrap().settlements().getFirst().id());
        var plan = FrontierGrayboxPlan.compile(state);

        assertTrue(waypoints.stream().skip(1).allMatch(position -> plan.cells().containsKey(position)));
        assertTrue(waypoints.stream().skip(1).allMatch(position -> plan.cells().get(position).ownerId().equals(FrontierRouteNetwork.OWNER)));
    }

    @Test
    void everyBootstrapContainerHasOnePlannedProvenanceSupportSocket() {
        FrontierWorldState state = initial();
        FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);

        state.inventory().surfaces().values().forEach(surface -> {
            GrayboxCell support = FrontierContainerSocketPlan.support(state, surface).orElseThrow();
            assertEquals(support, plan.cells().get(support.position()), "socket must be part of the immutable graybox plan: " + surface.containerId());
        });
    }

    @Test
    void everyBootstrapResidentUsesOneClearDeterministicStreetOrPerimeterSlot() {
        FrontierWorldState state = initial();
        FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);

        state.bootstrap().settlements().forEach(settlement -> {
            java.util.Set<BlockPosition> positions = new java.util.HashSet<>();
            settlement.residents().forEach(resident -> {
                BlockPosition position = state.actorLocations().get(resident.id()).position();
                assertTrue(positions.add(position), "resident slots must not overlap: " + resident.id());
                GrayboxCell foot = plan.cells().get(position);
                assertTrue(foot == null || foot.semanticPart() == GrayboxSemanticPart.ROUTE_SURFACE,
                        "resident foot cell may use a route surface but must stay outside structure geometry: " + resident.id());
                assertEquals(null, plan.cells().get(position.offset(0, 1, 0)), "resident body clearance must stay outside graybox geometry: " + resident.id());
                assertEquals(null, plan.cells().get(position.offset(0, 2, 0)), "resident head clearance must stay outside graybox geometry: " + resident.id());
            });
        });
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:graybox-plan"), 1234L));
    }
}
