package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierSceneBehaviorsTest {
    @Test
    void registryRefusesMissingAndDuplicateSceneFamiliesBeforeEngineStart() {
        assertDoesNotThrow(() -> FrontierSceneBehaviors.requireCompleteKindsForTest(List.of(
                SceneCauseKind.SETTLEMENT_ASSAULT, SceneCauseKind.ENGINEERING_WORKSITE,
                SceneCauseKind.MEDICAL_TREATMENT, SceneCauseKind.RESOURCE_SITE_HARVEST, SceneCauseKind.PRODUCTION_WORK,
                SceneCauseKind.SERVICE_WORK, SceneCauseKind.ROUTE_PATROL)));
        assertThrows(IllegalArgumentException.class, () -> FrontierSceneBehaviors.requireCompleteKindsForTest(List.of(SceneCauseKind.SETTLEMENT_ASSAULT)));
        assertThrows(IllegalArgumentException.class, () -> FrontierSceneBehaviors.requireCompleteKindsForTest(List.of(
                SceneCauseKind.SETTLEMENT_ASSAULT, SceneCauseKind.SETTLEMENT_ASSAULT,
                SceneCauseKind.ENGINEERING_WORKSITE, SceneCauseKind.MEDICAL_TREATMENT,
                SceneCauseKind.RESOURCE_SITE_HARVEST, SceneCauseKind.PRODUCTION_WORK, SceneCauseKind.SERVICE_WORK,
                SceneCauseKind.ROUTE_PATROL)));
    }
}
