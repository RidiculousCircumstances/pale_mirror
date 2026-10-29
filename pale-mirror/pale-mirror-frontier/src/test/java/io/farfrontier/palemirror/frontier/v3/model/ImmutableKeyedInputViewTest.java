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
    @Test void registryReadSetsCompareTheirExactInputsRatherThanWrapperIdentity() {
        var view = new ImmutableRegistryView<Integer, ImmutableDependencies, Object>(1);
        var epoch = new Object(); var input = new Object();
        var before = view.project(epoch, Map.of(1, ImmutableDependencies.of(input)), ImmutableDependencies::sameAs, (id, ignored) -> new Object());
        assertSame(before, view.project(epoch, Map.of(1, ImmutableDependencies.of(input)), ImmutableDependencies::sameAs,
                (id, ignored) -> { throw new AssertionError("unchanged row was recomputed"); }));
        assertNotSame(before, view.project(epoch, Map.of(1, ImmutableDependencies.of(new Object())), ImmutableDependencies::sameAs,
                (id, ignored) -> new Object()));
    }
    @Test void changedInputWithTheSameProjectionRetainsItsImageButExplicitRevalidationStillRuns() {
        var view = new ImmutableRegistryView<Integer, Object, Boolean>(1); var epoch = new Object();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.BiFunction<Integer, Object, Boolean> compute = (id, input) -> { calls.incrementAndGet(); return Boolean.TRUE; };
        var inputs = ImmutableLookupMap.copyOf(Map.of(1, new Object())); var before = view.project(epoch, inputs, compute);
        var changed = ImmutableLookupMap.withEntry(inputs, 1, new Object());
        assertSame(before, view.project(epoch, changed, compute)); assertEquals(2, calls.get());
        assertSame(before, view.project(epoch, changed, (old, next) -> false, compute));
        assertEquals(3, calls.get(), "forced full audit must not take the same-source shortcut");
        assertTrue(view.project(epoch, ImmutableLookupMap.withoutEntry(changed, 1), compute).isEmpty());
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
    @Test void keyedRowsKeepBranchIdentityFailureIsolationAndThreadIsolation() throws Exception {
        var view = new ImmutableKeyedInputView<String, Object>(2);
        var epoch = new Object(); var a = new Object(); var b = new Object();
        var original = view.get(epoch, "one", List.of(a), Object::new);
        ImmutablePlanningScope.within(() -> {
            assertSame(original, view.get(epoch, "one", List.of(a), Object::new));
            var other = view.get(epoch, "one", List.of(b), Object::new);
            assertNotSame(original, other);
            assertSame(original, view.get(epoch, "one", List.of(a), Object::new));
            assertThrows(IllegalArgumentException.class, () -> view.get(epoch, "one", List.of(new Object()),
                    () -> { throw new IllegalArgumentException("invalid facts"); }));
            assertSame(other, view.get(epoch, "one", List.of(b), Object::new));
            return null;
        });
        var worker = new java.util.concurrent.FutureTask<>(() -> view.get(epoch, "one", List.of(b), Object::new));
        var thread = new Thread(worker); thread.start();
        assertNotSame(worker.get(), view.get(epoch, "one", List.of(b), Object::new), "mutable rows never cross threads");
        thread.join();
    }
    @Test void publishedKeyedRowsRetainEqualValuesOnlyAfterSuccessfulNewInputEvaluation() {
        var view = new ImmutableKeyedInputView<String, String>(2);
        var epoch = new Object(); var calls = new java.util.concurrent.atomic.AtomicInteger();
        var first = view.getPublished(epoch, "one", List.of(new Object()), () -> { calls.incrementAndGet(); return new String("same"); });
        var changed = new Object();
        assertSame(first, view.getPublished(epoch, "one", List.of(changed), () -> { calls.incrementAndGet(); return new String("same"); }));
        assertSame(first, view.getPublished(epoch, "one", List.of(changed), () -> { throw new AssertionError("unchanged input recomputed"); }));
        assertEquals(2, calls.get());
    }

}
