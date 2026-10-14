package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleAdmissionIndexTest {
    @Test void commitChecksReuseTheHeldPrefixOnlyWithinTheExactImmutablePass() {
        var checks = new AtomicInteger();
        var planner = new ScheduledActionPlanner<Object>() {
            public List<ProposedEvent> plan(Object s, ScheduledAction a) { return List.of(); }
            public boolean held(Object s, ScheduledAction a) { checks.incrementAndGet(); return !a.equals(SECOND); }
        };
        var budget = new WorkBudget(1, 1);
        var index = new ScheduleAdmissionIndex<>(planner, 8);
        var queue = new ScheduledActionQueue();
        queue.schedule(action("held-a", 1)); queue.schedule(action("held-b", 1)); queue.schedule(action("held-c", 1)); queue.schedule(SECOND);
        var state = new Object(); index.begin(budget);
        assertEquals(List.of(SECOND), queue.selectAdmittedDue(new SimInstant(2), budget,
                a -> index.assess(state, a), a -> Set.of(), ScheduledAction::weight).admitted());
        // Selection used all four evaluations. A fresh READY commit check must
        // still defer; begin again with its independent evaluation allowance.
        index.begin(new WorkBudget(2, 2));
        queue.selectAdmittedDue(new SimInstant(2), new WorkBudget(2, 2), a -> index.assess(state, a), a -> Set.of(), ScheduledAction::weight);
        int before = checks.get();
        assertTrue(queue.isAdmittedHead(SECOND, new SimInstant(2), a -> index.assess(state, a), a -> Set.of()));
        assertEquals(before + 1, checks.get(), "only READY is reassessed, not the three unchanged refusals");
        var changed = new Object();
        assertEquals(ScheduleAdmissionIndex.Decision.HELD, index.assess(changed, action("held-a", 1)));
        assertEquals(before + 2, checks.get(), "new state cannot reuse an unproved hold");
        index.begin(budget);
        index.assess(changed, action("held-a", 1));
        assertEquals(before + 3, checks.get(), "another host turn requires reevaluation without an owner proof");
    }
    private static final ScheduledAction FIRST = action("first", 1);
    private static final ScheduledAction SECOND = action("second", 2);
    private record State(Object stock, boolean hasStock, Object unrelated) { }
    private static ScheduledAction action(String id, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:" + id), new SimInstant(tick), 1,
                new SubjectId("subject:" + id), "test.review", 1);
    }
    @Test void unchangedProofAvoidsBusinessChecksButMissedWakeCannotChangeCanonicalOrder() {
        var checks = new AtomicInteger();
        var planner = new ScheduledActionPlanner<State>() {
            @Override public List<ProposedEvent> plan(State s, ScheduledAction a) { return List.of(); }
            @Override public boolean held(State s, ScheduledAction a) { checks.incrementAndGet(); return a.equals(FIRST) && !s.hasStock(); }
            @Override public Set<SubjectId> holdWakeKeys(State s, ScheduledAction a) { return Set.of(a.subject()); }
            @Override public Optional<ScheduledHold<State>> holdProof(State s, ScheduledAction a) {
                return Optional.of(new ScheduledHold<>(a, Set.of(a.subject()), next -> next.stock() == s.stock()));
            }
        };
        var queue = new ScheduledActionQueue(); queue.schedule(FIRST); queue.schedule(SECOND);
        var index = new ScheduleAdmissionIndex<>(planner, 8);
        var old = new State(new Object(), false, new Object()); index.begin(new WorkBudget(4, 4));
        assertEquals(List.of(SECOND), queue.selectAdmittedDue(new SimInstant(2), new WorkBudget(4, 4),
                a -> index.assess(old, a), a -> index.wakeKeys(old, a), ScheduledAction::weight).admitted());
        int before = checks.get();
        var unrelated = new State(old.stock(), false, new Object());
        for (int i = 0; i < 100; i++) assertEquals(ScheduleAdmissionIndex.Decision.HELD, index.assess(unrelated, FIRST));
        assertEquals(before, checks.get());
        // No derived wake: validity alone detects the now-ready parked predecessor.
        var changed = new State(new Object(), true, new Object()); index.begin(new WorkBudget(4, 4));
        assertFalse(queue.isAdmittedHead(SECOND, new SimInstant(2), a -> index.assess(changed, a), a -> index.wakeKeys(changed, a)));
        assertTrue(queue.isAdmittedHead(FIRST, new SimInstant(2), a -> index.assess(changed, a), a -> index.wakeKeys(changed, a)));
        assertEquals(List.of(FIRST, SECOND), queue.snapshot());
    }
    @Test void unknownPredecessorDefersInsteadOfLosingOrReorderingWork() {
        var index = new ScheduleAdmissionIndex<Integer>(new ScheduledActionPlanner<>() {
            @Override public List<ProposedEvent> plan(Integer s, ScheduledAction a) { return List.of(); }
        }, 8);
        var budget = new WorkBudget(1, 1); index.begin(budget);
        for (int i = 0; i < budget.maxAdmissionEvaluations(); i++)
            assertEquals(ScheduleAdmissionIndex.Decision.READY, index.assess(0, FIRST));
        var queue = new ScheduledActionQueue(); queue.schedule(FIRST); queue.schedule(SECOND);
        assertFalse(queue.isAdmittedHead(SECOND, new SimInstant(2), a -> index.assess(0, a), a -> Set.of()));
        assertEquals(List.of(FIRST, SECOND), queue.snapshot());
        assertEquals(ScheduleAdmissionIndex.Decision.DEFERRED, index.assess(0, FIRST));
    }
}
