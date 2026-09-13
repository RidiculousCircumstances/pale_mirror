package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3InfectionOverlayStructuralCeilingTest {
    @Test
    void retainedStructuralColumnCeilingKeepsDynamicSurfaceAboveAnUnmaterializedHiveCell() {
        var state = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:overlay-structural-ceiling"), 41L).initialState();
        FrontierGrayboxPlan plan = FrontierGrayboxPlan.compileStructuralBaseline(state);
        BlockPosition ganglionTop = state.bootstrap().hive().organs().stream()
                .filter(organ -> organ.id().value().equals("organ:west-ganglion"))
                .findFirst().orElseThrow().anchor().offset(0, 3, 0);
        assertTrue(plan.cells().containsKey(ganglionTop), "the immutable first-visible organ plan owns its top tissue cell");

        Map<Long, Integer> ceilings = FrontierV3GrayboxExecutor.structuralCeilings(plan);
        Integer ceiling = ceilings.get(FrontierV3GrayboxExecutor.columnKey(ganglionTop.x(), ganglionTop.z()));
        assertEquals(ganglionTop.y(), ceiling, "the published index retains the full declared organ column before loaded projection catches up");
        assertEquals(ganglionTop.y() + 1, FrontierV3InfectionOverlayExecutor.surfaceY(ganglionTop.y(), ceiling),
                "an infection surface never chooses an immutable organ cell merely because the heightmap has not caught up");
    }
}
