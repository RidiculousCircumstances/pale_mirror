package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.farfrontier.palemirror.api.SettlementDevelopmentStage;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementArchetypeCatalogTest {
    @Test void everyApprovedPopulationSnapshotIsCompleteButOnlyTownshipIsActive() {
        var definition = SettlementArchetypeCatalog.ironFrontier();
        assertEquals(SettlementDevelopmentStage.TOWNSHIP, definition.activeStage());
        assertEquals(Map.of("CIVILIANS", 2, "WORKERS", 7, "SPECIALISTS", 1, "GUARDS", 2, "CHILDREN", 0),
                definition.stages().get(SettlementDevelopmentStage.PROSPECTING_POST).cohorts());
        assertEquals(Map.of("CIVILIANS", 6, "WORKERS", 10, "SPECIALISTS", 2, "GUARDS", 4, "CHILDREN", 2),
                definition.stages().get(SettlementDevelopmentStage.MINING_CAMP).cohorts());
        assertEquals(Map.of("CIVILIANS", 20, "WORKERS", 14, "SPECIALISTS", 4, "GUARDS", 6, "CHILDREN", 4),
                definition.stages().get(SettlementDevelopmentStage.TOWNSHIP).cohorts());
        assertEquals(Map.of("CIVILIANS", 28, "WORKERS", 22, "SPECIALISTS", 7, "GUARDS", 9, "CHILDREN", 6),
                definition.stages().get(SettlementDevelopmentStage.MINING_TOWN).cohorts());
        assertEquals(20, definition.active().buildings().size());
        assertEquals(27, definition.stages().get(SettlementDevelopmentStage.MINING_TOWN).buildings().size());
    }
}
