package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ImmutablePlanningScopeTest {
    @Test void completedProjectionBranchesKeepExactEvidenceWithoutRestoringMutableWorkingIndexes() {
        var calls = new AtomicInteger(); var reads = new AtomicInteger(); var epoch = new Object();
        var source = new ImmutableReferenceIndex.Source<Map<Integer, String>, Integer, String>(facts -> { reads.incrementAndGet(); return facts; });
        var view = new ImmutableReadProjection<Map<Integer, String>, String, Integer, String>(2, 1, List.of(source),
                (facts, address) -> List.of(source.at(address)), (facts, address) -> {
                    calls.incrementAndGet(); var value = facts.getOrDefault(address, "absent");
                    if (value.equals("broken")) throw new IllegalArgumentException("invalid fact");
                    return value;
                });
        var declarations = ImmutableLookupMap.copyOf(Map.of("one", 1, "two", 2));
        Map<Integer, String> a = ImmutableLookupMap.copyOf(Map.of(1, "a"));
        var b = ImmutableLookupMap.withEntry(a, 1, "b"); var c = ImmutableLookupMap.withEntry(a, 2, "c");
        ImmutablePlanningScope.within(() -> {
            var first = view.project(epoch, declarations, a); assertEquals(2, calls.get());
            assertEquals("b", view.project(epoch, declarations, b).get("one")); assertEquals(3, calls.get());
            assertSame(first, view.project(epoch, declarations, a)); assertEquals(3, calls.get());
            assertEquals(Map.of("one", "a", "two", "c"), view.project(epoch, declarations, c));
            assertEquals(5, calls.get(), "a cache hit does not pretend the working index advanced to A");
            assertSame(first, view.project(epoch, declarations, a));
            var missing = ImmutableLookupMap.withoutEntry(a, 1);
            assertEquals(Map.of("one", "absent", "two", "absent"), view.project(epoch, declarations, missing));
            var broken = ImmutableLookupMap.withEntry(a, 1, "broken");
            assertThrows(IllegalArgumentException.class, () -> view.project(epoch, declarations, broken));
            assertSame(first, view.project(epoch, declarations, a));
            int before = calls.get();
            ImmutableInvariantCheck.full(() -> view.project(epoch, declarations, a));
            assertEquals(before + 2, calls.get(), "full ingress rechecks despite an exact scoped result");
            before = calls.get(); view.project(new Object(), declarations, a); assertEquals(before + 2, calls.get());
            for (int i = 0; i < 100; i++) assertEquals("value:" + i,
                    view.project(epoch, declarations, ImmutableLookupMap.withEntry(a, 1, "value:" + i)).get("one"));
            return null;
        });
        int before = calls.get(); view.project(epoch, declarations, a); assertTrue(calls.get() > before, "decision exit releases branch history");
        assertEquals(111, reads.get(), "each projection captures its source once, including failures and cached branches");
    }
    @Test void branchingReusesOnlyTheExactBasisAndReleasesScopeOnFailure() {
        var view = new ImmutableInputView<Integer>(); var calls = new AtomicInteger();
        Object original = new Object(), draft = new Object();
        assertEquals(1, view.get(List.of(original), calls::incrementAndGet), "an existing last image must also enter the new scope's branch history");
        assertThrows(IllegalArgumentException.class, () -> ImmutablePlanningScope.within(() -> {
            int first = view.get(List.of(original), calls::incrementAndGet);
            assertNotEquals(first, view.get(List.of(draft), calls::incrementAndGet));
            assertEquals(first, ImmutablePlanningScope.within(() -> view.get(List.of(original), calls::incrementAndGet)));
            throw new IllegalArgumentException("abandoned candidate");
        }));
        assertEquals(2, calls.get());
        assertEquals(3, view.get(List.of(original), calls::incrementAndGet));
    }
    @Test void equalButDistinctInputsNeverReuseAStaleView() {
        var view = new ImmutableInputView<Integer>(); var calls = new AtomicInteger();
        ImmutablePlanningScope.within(() -> {
            assertEquals(1, view.get(List.of(new String("same")), calls::incrementAndGet));
            assertEquals(2, view.get(List.of(new String("same")), calls::incrementAndGet));
            return true;
        });
    }
}
