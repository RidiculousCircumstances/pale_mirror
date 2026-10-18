package io.farfrontier.palemirror.frontier.v3.model.agriculture;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CultivationAreaPlannerTest {
    private final CultivationAreaPlanner planner = new CultivationAreaPlanner();
    private static final CultivationRules RULES = CultivationRules.graybox();
    private static CultivationAreaPlanner.Cell cell(int index, CultivationAreaPlanner.Land land, boolean selected, boolean pending) {
        return new CultivationAreaPlanner.Cell(new BlockPosition(index, 64, 0), land, 2, 1000, selected, pending);
    }
    @Test void reserveIsReusedBeforeNewLandAndContractionDoesNotDestroyPlants() {
        var planted = cell(0, CultivationAreaPlanner.Land.PLANTED, true, false);
        var reserve = cell(1, CultivationAreaPlanner.Land.RESERVE, false, false);
        var fresh = cell(2, CultivationAreaPlanner.Land.NEW, false, false);
        var cells = List.of(fresh, reserve, planted);
        var decision = planner.plan(new CultivationAreaPlanner.Request(4, 0, 2000, false, true, RULES, cells));
        assertEquals(Set.of(planted.position(), reserve.position()), decision.sow());
        var stopped = planner.plan(new CultivationAreaPlanner.Request(0, 0, 2000, false, true, RULES, cells));
        assertTrue(stopped.sow().isEmpty());
        assertEquals(CultivationAreaPlanner.Reason.SAFE_FALLOW, stopped.reason());
        assertEquals(CultivationAreaPlanner.Land.PLANTED, planted.land());
    }
    @Test void outstandingPreparationCountsAgainstOneDevelopmentBudget() {
        var cells = new ArrayList<CultivationAreaPlanner.Cell>();
        for (int i = 0; i < 32; i++) cells.add(cell(i, CultivationAreaPlanner.Land.RESERVE, true, true));
        for (int i = 32; i < 132; i++) cells.add(cell(i, CultivationAreaPlanner.Land.NEW, false, false));
        var decision = planner.plan(new CultivationAreaPlanner.Request(264, 0, 2000, false, true, RULES, cells));
        assertEquals(64, decision.sow().size());
        assertEquals(32, decision.outstandingPreparations());
        assertEquals(CultivationAreaPlanner.Reason.DEVELOPMENT_LIMIT, decision.reason());
    }
    @Test void unsafeImportsCannotJustifyZeroSowingOrPromiseLateHarvest() {
        var cell = cell(0, CultivationAreaPlanner.Land.RESERVE, false, false);
        var restart = planner.plan(new CultivationAreaPlanner.Request(0, 2, 2000, true, false, RULES, List.of(cell)));
        assertEquals(Set.of(cell.position()), restart.sow());
        var tooLate = planner.plan(new CultivationAreaPlanner.Request(2, 2, 500, true, false, RULES, List.of(cell)));
        assertEquals(0, tooLate.expectedOutput());
        assertEquals(CultivationAreaPlanner.Reason.SUITABLE_LAND_LIMIT, tooLate.reason());
    }
}
