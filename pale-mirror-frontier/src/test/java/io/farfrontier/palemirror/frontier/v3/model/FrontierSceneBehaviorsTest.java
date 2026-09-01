package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierSceneBehaviorsTest {
    @Test
    void registryRefusesMissingAndDuplicateSceneFamiliesBeforeEngineStart() {
        assertDoesNotThrow(() -> FrontierSceneBehaviors.requireCompleteKindsForTest(List.of(
                SceneCauseKind.LOGISTICS, SceneCauseKind.SETTLEMENT_ASSAULT, SceneCauseKind.ENGINEERING_WORKSITE)));
        assertThrows(IllegalArgumentException.class, () -> FrontierSceneBehaviors.requireCompleteKindsForTest(List.of(SceneCauseKind.LOGISTICS)));
        assertThrows(IllegalArgumentException.class, () -> FrontierSceneBehaviors.requireCompleteKindsForTest(List.of(
                SceneCauseKind.LOGISTICS, SceneCauseKind.LOGISTICS, SceneCauseKind.SETTLEMENT_ASSAULT, SceneCauseKind.ENGINEERING_WORKSITE)));
    }
}
