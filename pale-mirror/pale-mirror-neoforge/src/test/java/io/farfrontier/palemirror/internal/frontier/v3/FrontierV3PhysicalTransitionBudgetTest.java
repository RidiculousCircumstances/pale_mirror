package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3PhysicalTransitionBudgetTest {
    @Test void exhaustedTurnRetainsProgressButDoesNotStartAnotherTransition() {
        var clock = new AtomicLong(100);
        var budget = new FrontierV3PhysicalTransitionBudget(10, clock::get);
        clock.set(120);
        assertTrue(budget.tryStart(), "a cold departure must not starve behind earlier host work");
        assertFalse(budget.tryStart(), "only the started transition may overrun the soft budget");
        var nextTurn = new FrontierV3PhysicalTransitionBudget(10, clock::get);
        assertTrue(nextTurn.tryStart());
        clock.addAndGet(9);
        assertTrue(nextTurn.tryStart());
        clock.incrementAndGet();
        assertFalse(nextTurn.tryStart());
    }
}
