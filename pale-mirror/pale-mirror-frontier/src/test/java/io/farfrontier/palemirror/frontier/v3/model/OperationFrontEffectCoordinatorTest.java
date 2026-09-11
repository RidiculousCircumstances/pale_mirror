package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OperationFrontEffectCoordinatorTest {
    @Test void acceptsOneExactCrossFrontEffectOnlyOnce() {
        OperationFrontEffectKey key = new OperationFrontEffectKey(new SubjectId("cause:blast-1"), new SubjectId("front:a"), new SubjectId("front:b"), 2L);
        OperationFrontEffectCoordinator coordinator = OperationFrontEffectCoordinator.empty();
        assertTrue(coordinator.accepts(key));
        coordinator = coordinator.record(key);
        assertFalse(coordinator.accepts(key));
        OperationFrontEffectCoordinator retained = coordinator;
        assertThrows(IllegalArgumentException.class, () -> retained.record(key));
        assertThrows(IllegalArgumentException.class, () -> new OperationFrontEffectKey(key.causeId(), key.sourceFrontId(), key.sourceFrontId(), 2L));
    }
}
