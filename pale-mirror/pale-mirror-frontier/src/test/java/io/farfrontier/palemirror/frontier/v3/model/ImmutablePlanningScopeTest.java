package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ImmutablePlanningScopeTest {
    @Test void branchingReusesOnlyTheExactBasisAndReleasesScopeOnFailure() {
        var view = new ImmutableInputView<Integer>(); var calls = new AtomicInteger();
        Object original = new Object(), draft = new Object();
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
