package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableInputViewTest {
    @Test void reuseIsIdentityBoundedAndFailedRecomputationCannotPublishStaleValue() {
        var view = new ImmutableInputView<Integer>(); var calls = new AtomicInteger();
        var first = new String("one"); var second = new String("one");
        assertEquals(1, view.get(List.of(first), calls::incrementAndGet));
        for (int i = 0; i < 64; i++) assertEquals(1, view.get(List.of(first), calls::incrementAndGet));
        assertEquals(2, view.get(List.of(second), calls::incrementAndGet), "equal is not identical immutable input");
        assertThrows(IllegalArgumentException.class, () -> view.get(List.of(first), () -> { throw new IllegalArgumentException("invalid view"); }));
        assertEquals(3, view.get(List.of(first), calls::incrementAndGet));
        assertEquals(4, view.get(List.of(second), calls::incrementAndGet), "only one generation is retained");
    }
}
