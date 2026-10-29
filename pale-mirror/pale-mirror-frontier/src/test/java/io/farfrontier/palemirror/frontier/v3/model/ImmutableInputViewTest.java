package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableInputViewTest {
    @Test void sharedSnapshotResultCannotCrossChangedSnapshotQueryFailureOrEviction() throws Exception {
        var view = new ImmutableSnapshotView<String, Integer>(2, 2);
        var snapshot = new String("world"); var calls = new AtomicInteger();
        var prepared = new java.util.concurrent.FutureTask<Integer>(() -> view.get(snapshot, "home-at-tick", calls::incrementAndGet));
        new Thread(prepared).start();
        assertEquals(1, prepared.get());
        assertEquals(1, view.get(snapshot, "home-at-tick", calls::incrementAndGet));
        assertEquals(2, view.get(snapshot, "other-home-or-tick", calls::incrementAndGet));
        var changed = new String("world");
        assertThrows(IllegalArgumentException.class, () -> view.get(changed, "home-at-tick",
                () -> { throw new IllegalArgumentException("invalid changed facts"); }));
        assertEquals(3, view.get(changed, "home-at-tick", calls::incrementAndGet));
        assertEquals(4, view.get(new Object(), "home-at-tick", calls::incrementAndGet));
        assertEquals(5, view.get(snapshot, "home-at-tick", calls::incrementAndGet), "old snapshot eviction must recompute");
        // Exercise capacity eviction with two live inputs; GC retirement is a different cause.
        java.lang.ref.Reference.reachabilityFence(changed);
    }

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
    @Test void completeSnapshotVisitCannotReplaceChangedInputRecoveryOrWithdrawnOwnerValidation() {
        var proof = new ImmutableInvariantRegistry<String>(2);
        var epoch = new Object(); var row = new Object(); var input = new Object(); var snapshot = new Object();
        var registry = java.util.Map.of("one", row); var checks = new AtomicInteger(); var reads = new AtomicInteger();
        java.util.function.Supplier<Object[]> dependencies = () -> { reads.incrementAndGet(); return new Object[]{input}; };
        proof.validateSnapshot(epoch, registry, "one", row, snapshot, checks::incrementAndGet, dependencies);
        proof.validateSnapshot(epoch, registry, "one", row, snapshot, checks::incrementAndGet, dependencies);
        assertEquals(1, reads.get()); assertEquals(1, checks.get());
        // Another immutable root must inspect the entire SAME owner read set, even if it matches.
        proof.validateSnapshot(epoch, registry, "one", row, new Object(), checks::incrementAndGet, dependencies);
        assertEquals(2, reads.get()); assertEquals(1, checks.get());
        var changedSnapshot = new Object(); var changedInput = new Object();
        assertThrows(IllegalArgumentException.class, () -> proof.validateSnapshot(epoch, registry, "one", row,
                changedSnapshot, () -> { throw new IllegalArgumentException("changed invalid relationship"); }, () -> new Object[]{changedInput}));
        proof.validateSnapshot(epoch, registry, "one", row, changedSnapshot, checks::incrementAndGet, () -> new Object[]{changedInput});
        assertEquals(2, checks.get(), "failed checks never mark a snapshot visited");
        ImmutableInvariantCheck.full(() -> {
            proof.validateSnapshot(epoch, registry, "one", row, changedSnapshot, checks::incrementAndGet, () -> new Object[]{changedInput}); return null;
        });
        assertEquals(3, checks.get(), "full ingress bypasses both successful dependency and snapshot evidence");
        proof.validateSnapshot(epoch, java.util.Map.of(), "one", row, changedSnapshot, checks::incrementAndGet, dependencies);
        proof.validateSnapshot(epoch, registry, "one", row, changedSnapshot, checks::incrementAndGet, () -> new Object[]{changedInput});
        assertEquals(5, checks.get(), "withdrawal clears same-snapshot evidence as well as input proofs");
        proof.validateSnapshot(new Object(), registry, "one", row, changedSnapshot, checks::incrementAndGet, () -> new Object[]{changedInput});
        assertEquals(6, checks.get(), "same row/snapshot cannot cross an epoch");
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
