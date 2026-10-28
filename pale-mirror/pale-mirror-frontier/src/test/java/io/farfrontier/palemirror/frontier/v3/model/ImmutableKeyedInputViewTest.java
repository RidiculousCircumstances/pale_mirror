package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableKeyedInputViewTest {
    @Test void activeRegistryRowsSurviveACompleteScanAndWithdrawWithoutMutatingPublishedImages() {
        var view = new ImmutableRegistryView<Integer, Object, Object>(ResourceSiteState.MAX_SITES);
        var world = new Object(); var inputs = new java.util.LinkedHashMap<Integer, Object>();
        for (int i = 0; i < 300; i++) inputs.put(i, new Object());
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.BiFunction<Integer, Object, Object> compute = (key, value) -> { calls.incrementAndGet(); return new Object(); };
        var before = view.project(world, inputs, compute);
        assertSame(before, view.project(world, inputs, compute)); assertEquals(300, calls.get());
        inputs.put(2, new Object()); inputs.remove(4);
        var after = view.project(world, inputs, compute);
        assertEquals(301, calls.get()); assertEquals(299, after.size());
        assertSame(before.get(0), after.get(0)); assertNotSame(before.get(2), after.get(2)); assertTrue(before.containsKey(4));
        var failed = new java.util.LinkedHashMap<>(inputs); failed.put(2, new Object()); failed.put(500, new Object());
        assertThrows(IllegalStateException.class, () -> view.project(world, failed, (key, value) -> {
            if (key == 500) throw new IllegalStateException("rejected projection"); return new Object();
        }));
        assertSame(after, view.project(world, inputs, compute), "failed derivation cannot install partial trusted inputs");
        assertThrows(UnsupportedOperationException.class, () -> after.put(900, new Object()));
        assertTrue(view.project(world, Map.of(), compute).isEmpty());
        assertNotSame(after.get(0), view.project(new Object(), Map.of(0, inputs.get(0)), compute).get(0));
    }
    @Test void independentSubjectsRetainExactInputsButChangesAndEvictionRecompute() {
        var view = new ImmutableKeyedInputView<String, Object>(2);
        var world = new Object(); var input = new Object(); var changed = new Object();
        var a = view.get(world, "a", List.of(input), Object::new);
        var b = view.get(world, "b", List.of(input), Object::new);
        assertSame(a, view.get(world, "a", List.of(input), Object::new));
        assertNotSame(a, view.get(world, "a", List.of(changed), Object::new));
        assertNotSame(a, view.get(world, "a", List.of(input), Object::new), "rollback rechecks the exact basis");
        view.get(world, "c", List.of(input), Object::new);
        assertNotSame(b, view.get(world, "b", List.of(input), Object::new), "bounded cache may evict, never fabricate");
        var fresh = view.get(new Object(), "b", List.of(input), Object::new);
        assertNotSame(b, fresh, "another generation cannot inherit evidence");
    }
}
