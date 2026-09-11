package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.persistence.StrategicPlanStateCodec;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
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

    @Test void retainsTheReceiptAcrossStrategicSnapshotRecovery() throws Exception {
        OperationFrontEffectKey key = new OperationFrontEffectKey(new SubjectId("cause:pursuit-1"),
                new SubjectId("front:patrol"), new SubjectId("front:expedition"), 4L);
        StrategicPlanState plans = StrategicPlanState.empty().recordFrontEffect(key);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        StrategicPlanStateCodec.write(new DataOutputStream(bytes), plans);

        StrategicPlanState restored = StrategicPlanStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertFalse(restored.frontEffects().accepts(key), "recovery must retain the once-only cross-front receipt");
        assertThrows(IllegalArgumentException.class, () -> restored.recordFrontEffect(key));
    }
}
