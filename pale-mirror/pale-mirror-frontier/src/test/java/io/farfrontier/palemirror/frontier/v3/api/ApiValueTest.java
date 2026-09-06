package io.farfrontier.palemirror.frontier.v3.api;

import org.junit.jupiter.api.Test;

import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiValueTest {
    @Test
    void identifiersAreTypedAndCanonical() {
        assertEquals("frontier:world-1", new WorldId("frontier:world-1").value());
        assertThrows(IllegalArgumentException.class, () -> new WorldId("World 1"));
        assertThrows(IllegalArgumentException.class, () -> new CommandId("frontier:"));
    }

    @Test
    void simulationTimeAndRevisionFailBeforeWrapping() {
        assertEquals(7L, new SimInstant(4L).plus(3L).ticks());
        assertThrows(IllegalArgumentException.class, () -> new SimInstant(-1L));
        assertThrows(ArithmeticException.class, () -> new SimInstant(Long.MAX_VALUE).plus(1L));
        assertThrows(ArithmeticException.class, () -> new Revision(Long.MAX_VALUE).next());
    }

    @Test
    void fixedPointUsesExplicitRounding() {
        assertEquals(333_333L, FixedScalar.fraction(1L, 3L, RoundingMode.DOWN).raw());
        assertEquals(333_334L, FixedScalar.fraction(1L, 3L, RoundingMode.UP).raw());
        assertEquals(500_000L, FixedScalar.fraction(1L, 2L, RoundingMode.UNNECESSARY).raw());
        assertThrows(ArithmeticException.class, () -> FixedScalar.fraction(1L, 3L, RoundingMode.UNNECESSARY));
        assertThrows(IllegalArgumentException.class, () -> new FixedRatio(FixedScalar.whole(2L)));
    }
}
