package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3SceneBehaviorRegistryTest {
    @Test
    void registryRejectsMissingAndDuplicatePhysicalSceneBehaviorsBeforeWorldMutation() {
        assertDoesNotThrow(() -> FrontierV3SceneBehaviorRegistration.requireCompleteKinds(List.of(
                SceneCauseKind.SETTLEMENT_ASSAULT, SceneCauseKind.LOGISTICS)));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3SceneBehaviorRegistration.requireCompleteKinds(List.of(SceneCauseKind.LOGISTICS)));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3SceneBehaviorRegistration.requireCompleteKinds(List.of(
                SceneCauseKind.LOGISTICS, SceneCauseKind.LOGISTICS, SceneCauseKind.SETTLEMENT_ASSAULT)));
    }
}
