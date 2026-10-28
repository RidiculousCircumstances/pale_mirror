package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableInputViewTest {
    @Test void completedImmutableResultCanCrossThreadsButCannotCrossAnInputRevision() throws Exception {
        var view = new ImmutableResultView<String, Integer>(2); var calls = new AtomicInteger();
        var input = new String("basis");
        var result = new java.util.concurrent.FutureTask<Integer>(() -> view.get(List.of(input), "plan", calls::incrementAndGet));
        new Thread(result).start();
        assertEquals(1, result.get());
        assertEquals(1, view.get(List.of(input), "plan", calls::incrementAndGet));
        assertEquals(2, view.get(List.of(new String("basis")), "plan", calls::incrementAndGet));
        assertEquals(3, view.get(List.of(input), "other-plan", calls::incrementAndGet));
        assertEquals(4, view.get(List.of(input), "plan", calls::incrementAndGet), "bounded eviction recomputes, never supplies another result");
    }
    @Test void registeredInvariantEvidenceCannotCrossWithdrawalFailureOrFullIngress() {
        var proof = new ImmutableInvariantRegistry<String>(2);
        var epoch = new Object(); var declaration = new Object(); var basis = new Object();
        var registry = java.util.Map.of("one", declaration); var calls = new AtomicInteger();
        proof.validate(epoch, registry, "one", declaration, calls::incrementAndGet, basis);
        proof.validate(epoch, registry, "one", declaration, calls::incrementAndGet, basis);
        assertEquals(1, calls.get());
        ImmutableInvariantCheck.full(() -> {
            proof.validate(epoch, registry, "one", declaration, calls::incrementAndGet, basis); return null;
        });
        assertEquals(2, calls.get(), "recovery cannot reuse a warmed proof");
        var changed = new Object();
        assertThrows(IllegalArgumentException.class, () -> proof.validate(epoch, registry, "one", declaration,
                () -> { throw new IllegalArgumentException("changed invalid owner fact"); }, changed));
        proof.validate(epoch, registry, "one", declaration, calls::incrementAndGet, changed);
        assertEquals(3, calls.get(), "a failed check cannot publish its dependency basis");
        proof.validate(epoch, java.util.Map.of(), "one", declaration, calls::incrementAndGet, changed);
        assertEquals(4, calls.get(), "an unregistered draft is always checked");
        proof.validate(epoch, registry, "one", declaration, calls::incrementAndGet, changed);
        assertEquals(5, calls.get(), "a withdrawn row no longer retains trusted inputs");
        var replacement = new Object();
        proof.validate(epoch, java.util.Map.of("one", replacement), "one", replacement, calls::incrementAndGet, changed);
        assertEquals(6, calls.get(), "nominal id reuse cannot reuse the previous declaration proof");
    }
    @Test void mutableDerivedMemoIsConfinedToItsCalculationThread() throws Exception {
        var view = new ImmutableInputView<java.util.Map<String, Integer>>(); var input = new Object();
        var main = view.get(List.of(input), java.util.HashMap::new); main.put("value", 1);
        var workerValue = new java.util.concurrent.atomic.AtomicReference<java.util.Map<String, Integer>>();
        var worker = new Thread(() -> {
            var memo = view.get(List.of(input), java.util.HashMap::new); memo.put("value", 2); workerValue.set(memo);
        });
        worker.start(); worker.join();
        assertEquals(1, main.get("value"));
        assertEquals(2, workerValue.get().get("value"));
        assertSame(main, view.get(List.of(input), java.util.HashMap::new));
        assertNotSame(main, workerValue.get());
    }
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
