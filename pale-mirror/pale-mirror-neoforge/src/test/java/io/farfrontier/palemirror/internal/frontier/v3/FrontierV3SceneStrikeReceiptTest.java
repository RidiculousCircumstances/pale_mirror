package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3SceneStrikeReceiptTest {
    private static FrontierV3SceneStrikeReceipt receipt() {
        var world = new WorldId("frontier:stored-strike");
        var target = new SubjectId("actor:target");
        return new FrontierV3SceneStrikeReceipt(world, new PhysicalSceneBinding(new SceneLeaseId("lease:stored-strike"), 7),
                PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT, 3, SceneLease.deterministicEntityId(world, target),
                new SceneStrikeObservation(new PhysicalObservationId("observation:stored-strike"), new PhysicalIntentId("intent:stored-strike"),
                        new SubjectId("actor:attacker"), target, FixedScalar.whole(20), FixedScalar.whole(18)));
    }

    @Test void targetLocalReceiptRoundTripsEveryExactField() {
        var expected = receipt();
        assertEquals(expected, FrontierV3SceneStrikeReceipt.load(expected.save()));
    }

    @Test void missingFieldsUnknownOwnerAndForeignEntityCannotBecomeEvidence() {
        var expected = receipt();
        for (String key : expected.save().getAllKeys()) {
            var tag = expected.save(); tag.remove(key);
            assertThrows(IllegalArgumentException.class, () -> FrontierV3SceneStrikeReceipt.load(tag), key);
        }
        var owner = expected.save(); owner.putString("owner", "foreign:owner");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3SceneStrikeReceipt.load(owner));
        var entity = expected.save(); entity.putUUID("entity", new java.util.UUID(0, 1));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3SceneStrikeReceipt.load(entity));
        var health = expected.save(); health.putLong("after", FixedScalar.whole(21).raw());
        assertThrows(IllegalArgumentException.class, () -> FrontierV3SceneStrikeReceipt.load(health));
    }
}
