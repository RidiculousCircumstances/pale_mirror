package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementTerrainCompilerTest {
    @Test void managedUnionHasContinuousCoreAndDistanceBasedOuterBlend() {
        var areas = List.of(new VisualBounds(new VisualPoint(0, 64, 0), new VisualPoint(10, 70, 10)),
                new VisualBounds(new VisualPoint(8, 64, 8), new VisualPoint(20, 70, 20)));

        assertEquals(0, SettlementTerrainCompiler.distanceFrom(areas, 5, 5));
        assertEquals(0, SettlementTerrainCompiler.distanceFrom(areas, 15, 15));
        assertEquals(3, SettlementTerrainCompiler.distanceFrom(areas, 23, 15));
        assertEquals(5, SettlementTerrainCompiler.distanceFrom(areas, -5, 5));
    }

    @Test void productionTownshipTerrainEnvelopeStaysWithinColumnBudget() {
        var seed = new FrontierRegionPlanner().plan(7391842605318702447L, 0,
                new VisualPoint(2376, 67, 11400), FrontierClimate.TEMPERATE);
        int terrainColumns = SettlementTerrainCompiler.envelopeColumnCount(
                seed.settlementSite().managedArea().areas());

        assertTrue(terrainColumns <= 50_000,
                () -> "settlement terrain envelope escaped its 50k-column budget: " + terrainColumns);
    }
}
