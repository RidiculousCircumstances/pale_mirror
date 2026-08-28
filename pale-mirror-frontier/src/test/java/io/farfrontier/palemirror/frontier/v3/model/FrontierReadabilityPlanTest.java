package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierReadabilityPlanTest {
    @Test
    void givesEveryFunctionalBuildingAndOrganOneStablePlayerFacingBoard() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:board-plan"), 91L));
        FrontierReadabilityPlan plan = FrontierReadabilityPlan.compile(state);
        int expected = state.bootstrap().settlements().stream().mapToInt(value -> value.structures().size()).sum() + state.bootstrap().hive().organs().size();
        assertEquals(expected, plan.boards().size());
        FrontierObjectBoard workshop = plan.boards().get(state.bootstrap().settlements().getFirst().structures().stream()
                .filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow().id());
        assertEquals(FrontierObjectBoard.Tone.SETTLEMENT, workshop.tone());
        assertTrue(workshop.text().contains("WORKSHOP"));
        assertTrue(workshop.text().endsWith("OPERATIONAL"));
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
}
