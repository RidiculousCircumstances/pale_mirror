package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.api.VisualPoint;
import org.junit.jupiter.api.Test;

class VisualShowcasePlanTest {
    @Test void goldMasterMatrixCoversEveryModuleClimateRotationAndState() {
        var matrix = VisualShowcasePlan.matrix(new VisualPoint(0, 80, 0));
        assertEquals(FrontierModuleCatalog.definitionsInOrder().size() * 3 * 4 * 3, matrix.size());
        assertEquals(matrix.size(), matrix.stream().map(VisualShowcasePlan.Entry::key).distinct().count());
        matrix.forEach(entry -> {
            assertTrue(java.util.List.of("INTACT", "DAMAGED", "RUINED").contains(entry.state()));
            assertTrue(entry.module().templateId().startsWith("pale_mirror_visuals:"));
            assertEquals(Math.floorMod(entry.rotation(), 4), entry.module().quarterTurns());
        });
    }
}
