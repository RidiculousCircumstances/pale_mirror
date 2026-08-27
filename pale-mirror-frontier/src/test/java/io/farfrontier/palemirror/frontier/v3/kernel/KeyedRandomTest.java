package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeyedRandomTest {
    private static final DecisionKey BASE = new DecisionKey(
            77L, "hive.planning", new SubjectId("hive:root"), "select-target", 3L);

    @Test
    void decisionHasStableGoldenVector() {
        assertEquals(7233605245037717612L, KeyedRandom.nextLong(BASE));
        assertEquals(59, KeyedRandom.nextInt(BASE, 97));
        assertEquals(706_173L, KeyedRandom.nextRatio(BASE).value().raw());
    }

    @Test
    void unrelatedKeyCannotPerturbExistingDecision() {
        long before = KeyedRandom.nextLong(BASE);
        KeyedRandom.nextLong(new DecisionKey(77L, "human.market", new SubjectId("settlement:4"), "price", 0L));
        assertEquals(before, KeyedRandom.nextLong(BASE));
        assertNotEquals(before, KeyedRandom.nextLong(new DecisionKey(
                77L, "hive.planning", new SubjectId("hive:root"), "select-target", 4L)));
    }

    @Test
    void invalidBoundsAndKeysFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> KeyedRandom.nextInt(BASE, 0));
        assertThrows(IllegalArgumentException.class, () -> new DecisionKey(
                1L, "bad token", new SubjectId("hive:root"), "select", 0L));
    }
}
