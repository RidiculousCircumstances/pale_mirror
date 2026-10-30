package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedValueMemoTest {
    @Test void readMostlyFactsRetainExactKeysThroughPressureAndFailedComputations() {
        var memo = BoundedValueMemo.<String, String>readMostly(2);
        assertEquals("Aa".hashCode(), "BB".hashCode());
        assertEquals("Aa", memo.get("Aa", () -> "Aa"));
        assertEquals("BB", memo.get("BB", () -> "BB"));
        assertEquals("Aa", memo.get("Aa", () -> fail("unchanged fact was not retained")));
        assertEquals("third", memo.get("third", () -> "third"));
        assertEquals("Aa", memo.get("Aa", () -> "Aa"), "eviction recomputes the same complete fact");
        assertThrows(IllegalStateException.class, () -> memo.get("failed", () -> { throw new IllegalStateException(); }));
        assertEquals("recovered", memo.get("failed", () -> "recovered"));
        assertEquals("recovered", memo.get("failed", () -> fail("successful fact was not retained")));
    }
    @Test void equalHashesCannotReuseForeignInputsAndEvictionOnlyRecomputes() {
        var memo = new BoundedValueMemo<String, String>(1);
        assertEquals("Aa".hashCode(), "BB".hashCode());
        assertEquals("first", memo.get("Aa", () -> "first"));
        assertEquals("second", memo.get("BB", () -> "second"));
        assertEquals("first", memo.get("Aa", () -> "first"));
        assertThrows(IllegalStateException.class, () -> memo.get("failed", () -> { throw new IllegalStateException(); }));
        assertEquals("recovered", memo.get("failed", () -> "recovered"));
    }
}
