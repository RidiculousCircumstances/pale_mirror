package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ScheduledPreparationTest {
    @Test @Timeout(10) void preparationRunsOffOwnerAndOnlyOwnerCanCompleteItsEvents() throws Exception {
        var owner = Thread.currentThread(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var preparedOn = new AtomicReference<Thread>(); var completedOn = new AtomicReference<Thread>();
        var action = new ScheduledAction(new ScheduleId("schedule:test"), new SimInstant(1), 0, new SubjectId("subject:test"), "test", 1);
        ScheduledActionPlanner<Integer> planner = new ScheduledActionPlanner<>() {
            public List<ProposedEvent> plan(Integer state, ScheduledAction action) { throw new AssertionError("preparation capability bypassed"); }
            public Optional<ScheduledPreparation> preparation(Integer state, ScheduledAction ignored, SimInstant at) {
                return Optional.of(() -> {
                    preparedOn.set(Thread.currentThread()); entered.countDown();
                    try { release.await(); } catch (InterruptedException error) { throw new IllegalStateException(error); }
                    return () -> { completedOn.set(Thread.currentThread()); return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id()))); };
                });
            }
        };
        try (var host = new FrontierV3ScheduledPreparation<>(planner)) {
            var calculation = host.begin(3, action, new SimInstant(1));
            entered.await();
            assertTrue(calculation.poll().isEmpty()); assertNull(completedOn.get());
            assertNotSame(owner, preparedOn.get());
            release.countDown();
            Optional<List<ProposedEvent>> result;
            do { result = calculation.poll(); if (result.isEmpty()) Thread.yield(); } while (result.isEmpty());
            assertEquals(1, result.orElseThrow().size()); assertSame(owner, completedOn.get());
        } finally { release.countDown(); }
    }
}
