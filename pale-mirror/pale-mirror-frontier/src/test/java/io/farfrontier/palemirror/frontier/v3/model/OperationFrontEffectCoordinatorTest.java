package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.persistence.StrategicPlanStateCodec;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
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

    @Test void coordinatesProjectileBlastPursuitAndCargoAcrossEveryHotColdDirectionAndRecovery() throws Exception {
        List<OperationFrontEffectKey> effects = List.of(
                key("projectile", "attack-hot", "defence-hot", 3L),
                key("blast", "attack-hot", "defence-cold", 3L),
                key("pursuit", "defence-cold", "attack-hot", 3L),
                key("cargo", "supply-cold", "escort-hot", 3L));
        StrategicPlanState state = StrategicPlanState.empty();
        for (OperationFrontEffectKey effect : effects) {
            state = state.recordFrontEffect(effect);
            StrategicPlanState retained = state;
            assertThrows(IllegalArgumentException.class, () -> retained.recordFrontEffect(effect),
                    "retry may not duplicate " + effect.causeId());
        }
        // Direction is part of a causal identity.  A reverse arrival with its own cause remains
        // distinct; replaying the original reverse receipt does not.
        OperationFrontEffectKey reverseArrival = key("pursuit-return", "escort-hot", "supply-cold", 3L);
        state = state.recordFrontEffect(reverseArrival);
        StrategicPlanState directed = state;
        assertThrows(IllegalArgumentException.class, () -> directed.recordFrontEffect(reverseArrival));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        StrategicPlanStateCodec.write(new DataOutputStream(bytes), state);
        StrategicPlanState restored = StrategicPlanStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        for (OperationFrontEffectKey effect : effects) assertFalse(restored.frontEffects().accepts(effect),
                "restart must retain the exact " + effect.causeId() + " receipt");
        assertFalse(restored.frontEffects().accepts(reverseArrival));
    }

    private static OperationFrontEffectKey key(String family, String source, String target, long epoch) {
        return new OperationFrontEffectKey(new SubjectId("cause:" + family + "-r" + epoch), new SubjectId("front:" + source),
                new SubjectId("front:" + target), epoch);
    }
}
