package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceFormationsTest {
    @Test
    void sourceLargestRemaindersAllocateLiteralRolesWithStableTies() {
        Map<ReferenceHumanUnitKind, Double> weights = roleWeights();
        assertEquals(Map.of("line", 3, "scout", 1, "assault", 2, "engineer", 1),
                ReferenceFormations.integerComposition(7, weights));
        assertEquals(Map.of("line", 1, "assault", 1, "engineer", 1), ReferenceFormations.integerComposition(3, weights));
        assertEquals(Map.of("line", 1, "assault", 1),
                ReferenceFormations.integerComposition(2, Map.of(ReferenceHumanUnitKind.LINE, 1.0d,
                        ReferenceHumanUnitKind.SCOUT, 1.0d, ReferenceHumanUnitKind.ASSAULT, 1.0d)));
    }

    @Test
    void sourceCompositionDisplayAndRatiosIgnoreNegativeAmounts() {
        Map<ReferenceHumanUnitKind, Double> display = new LinkedHashMap<>();
        display.put(ReferenceHumanUnitKind.SCOUT, 1.6d);
        display.put(ReferenceHumanUnitKind.LINE, 0.4d);
        display.put(ReferenceHumanUnitKind.ASSAULT, 0.01d);
        assertEquals("line:0, scout:2", ReferenceFormations.compactComposition(display));
        assertEquals("line:2", ReferenceFormations.compactComposition(Map.of(ReferenceHumanUnitKind.LINE, 2.5d)));
        Map<ReferenceHumanUnitKind, Double> values = Map.of(ReferenceHumanUnitKind.SCOUT, -1.0d, ReferenceHumanUnitKind.LINE, 3.0d);
        assertEquals(3.0d, ReferenceFormations.totalUnits(values));
        assertEquals(1.0d, ReferenceFormations.compositionRatio(values, ReferenceHumanUnitKind.LINE));
        Map<ReferenceBioformKind, Double> bioforms = Map.of(ReferenceBioformKind.RAIDER, 0.25d,
                ReferenceBioformKind.BREAKER, 0.75d);
        assertEquals("breaker:1, raider:0", ReferenceFormations.compactComposition(bioforms));
        assertEquals(0.25d, ReferenceFormations.compositionRatio(bioforms, ReferenceBioformKind.RAIDER));
    }

    @Test
    void sourceRejectsAFormationWithoutPositiveRoles() {
        assertThrows(IllegalArgumentException.class, () -> ReferenceFormations.integerComposition(1, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ReferenceFormations.integerComposition(1, Map.of(ReferenceHumanUnitKind.LINE, 0.0d)));
    }

    @Test
    void sourceFormationPhasesPreserveTheirWireNames() {
        assertEquals("main_action", ReferenceFormationPhase.MAIN_ACTION.id());
        assertEquals("withdraw", ReferenceFormationPhase.WITHDRAW.id());
    }

    private static Map<ReferenceHumanUnitKind, Double> roleWeights() {
        Map<ReferenceHumanUnitKind, Double> result = new LinkedHashMap<>();
        result.put(ReferenceHumanUnitKind.LINE, 0.34d);
        result.put(ReferenceHumanUnitKind.SCOUT, 0.12d);
        result.put(ReferenceHumanUnitKind.ASSAULT, 0.28d);
        result.put(ReferenceHumanUnitKind.ENGINEER, 0.16d);
        result.put(ReferenceHumanUnitKind.MEDIC, 0.05d);
        result.put(ReferenceHumanUnitKind.LOGISTICS, 0.05d);
        return result;
    }
}
