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

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:graybox-plan"), 1234L));
    }
}
