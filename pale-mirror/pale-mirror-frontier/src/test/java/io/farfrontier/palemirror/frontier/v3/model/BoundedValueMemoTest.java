package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedValueMemoTest {
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
