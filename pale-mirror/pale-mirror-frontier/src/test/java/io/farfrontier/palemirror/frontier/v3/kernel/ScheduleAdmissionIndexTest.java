package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleAdmissionIndexTest {
    private static final ScheduledAction FIRST = action("first", 1);
    private static final ScheduledAction SECOND = action("second", 2);
    private record State(Object stock, boolean hasStock, Object unrelated) { }
    private static ScheduledAction action(String id, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:" + id), new SimInstant(tick), 1,
                new SubjectId("subject:" + id), "test.review", 1);
    }
    @Test void classificationExhaustionCannotConsumeReadyValidationAndBothAreBounded() {
        var index = new ScheduleAdmissionIndex<Integer>((s, a) -> List.of(), 8);
        var budget = new WorkBudget(1, 1); index.begin(budget);
        for (int i = 0; i < budget.maxClassificationEvaluations(); i++)
            assertEquals(ScheduleAdmissionIndex.Decision.READY, index.assess(0, FIRST));
        assertEquals(ScheduleAdmissionIndex.Decision.DEFERRED, index.assess(0, FIRST));
        assertEquals(ScheduleAdmissionIndex.Decision.READY, index.recheckSelected(0, SECOND));
        assertEquals(ScheduleAdmissionIndex.Decision.DEFERRED, index.recheckSelected(0, SECOND));
        assertEquals(budget.maxAdmissionEvaluations(), budget.maxClassificationEvaluations() + budget.maxExecutionEvaluations());
    }
    @Test void samePassRefusalRequiresExactStateAndNewTurnsStillNeedOwnerProof() {
        var checks = new AtomicInteger();
        var index = new ScheduleAdmissionIndex<State>(new ScheduledActionPlanner<>() {
            public List<ProposedEvent> plan(State s, ScheduledAction a) { return List.of(); }
            public boolean held(State s, ScheduledAction a) { checks.incrementAndGet(); return !s.hasStock(); }
        }, 8);
        var old = new State(new Object(), false, new Object()); index.begin(new WorkBudget(4, 4));
        assertEquals(ScheduleAdmissionIndex.Decision.HELD, index.assess(old, FIRST));
        assertEquals(ScheduleAdmissionIndex.Decision.HELD, index.recheckSelected(old, FIRST));
        assertEquals(1, checks.get());
        var changed = new State(new Object(), true, old.unrelated());
        assertEquals(ScheduleAdmissionIndex.Decision.READY, index.recheckSelected(changed, FIRST));
        index.begin(new WorkBudget(4, 4)); index.assess(old, FIRST);
        assertEquals(3, checks.get(), "a new pass cannot reuse an unproved refusal");
    }
    @Test void completeOwnerProofAvoidsBusinessChecksAndChangedDependencyInvalidatesIt() {
        var checks = new AtomicInteger();
        var index = new ScheduleAdmissionIndex<State>(new ScheduledActionPlanner<>() {
            public List<ProposedEvent> plan(State s, ScheduledAction a) { return List.of(); }
            public boolean held(State s, ScheduledAction a) { checks.incrementAndGet(); return !s.hasStock(); }
            public Optional<ScheduledHold<State>> holdProof(State s, ScheduledAction a) {
                return Optional.of(new ScheduledHold<>(a, Set.of(a.subject()), next -> next.stock() == s.stock()));
            }
        }, 8);
        var old = new State(new Object(), false, new Object()); index.begin(new WorkBudget(1, 1));
        index.assess(old, FIRST);
        for (int i = 0; i < 100; i++) {
            index.begin(new WorkBudget(1, 1));
            assertEquals(ScheduleAdmissionIndex.Decision.HELD, index.assess(new State(old.stock(), false, new Object()), FIRST));
        }
        assertEquals(1, checks.get());
        index.begin(new WorkBudget(1, 1));
        assertEquals(ScheduleAdmissionIndex.Decision.READY, index.recheckSelected(new State(new Object(), true, new Object()), FIRST));
        assertEquals(2, checks.get());
    }
}
