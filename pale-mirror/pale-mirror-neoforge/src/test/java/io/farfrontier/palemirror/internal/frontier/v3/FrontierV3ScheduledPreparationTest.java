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
    @Test @Timeout(10) void disposableAdviceRunsOffOwnerButOnlyCurrentOwnerMayAuthorizeItsExactContinuation() throws Exception {
        var owner = Thread.currentThread(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var computed = new AtomicReference<Thread>(); var validated = new AtomicReference<Thread>();
        var action = new ScheduledAction(new ScheduleId("schedule:advice"), new SimInstant(1), 0, new SubjectId("subject:advice"), "advice", 1);
        ScheduledActionPlanner<Integer> planner = new ScheduledActionPlanner<>() {
            public List<ProposedEvent> plan(Integer state, ScheduledAction ignored) { throw new AssertionError("advice bypass"); }
            public List<ScheduledAction> reconsideredBy(Integer before, Integer after, SimInstant at) { return List.of(action); }
            public Optional<ScheduledAdvisory<Integer>> advice(Integer captured, ScheduledAction ignored, SimInstant at) {
                return Optional.of(() -> {
                    computed.set(Thread.currentThread()); entered.countDown();
                    try { release.await(); } catch (InterruptedException failure) { throw new IllegalStateException(failure); }
                    return (current, now) -> {
                        validated.set(Thread.currentThread());
                        return current.equals(captured) ? Optional.of(() -> () -> List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id()))))
                                : Optional.empty();
                    };
                });
            }
        };
        try (var host = new FrontierV3ScheduledPreparation<>(planner)) {
            var ready = new CountDownLatch(1); host.onReady(ready::countDown);
            var calculation = host.beginAdvice(host.advice(3, action, new SimInstant(1)).orElseThrow()).orElseThrow();
            entered.await(); assertTrue(calculation.poll().isEmpty()); assertNull(validated.get());
            assertNotSame(owner, computed.get());
            // The live kernel holds its exact predecessor during this stage. An
            // unrelated forecast must not keep it queued behind this blocked work.
            var independentReady = new CountDownLatch(1); host.onReady(independentReady::countDown);
            var independent = host.beginPreparation(() -> () -> List.of(
                    new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id()))));
            independentReady.await();
            assertEquals(1, independent.poll().orElseThrow().size());
            assertEquals(1, release.getCount(), "exact preparation completes while the independent forecast is still blocked");
            host.onReady(ready::countDown);
            release.countDown(); ready.await();
            var completion = calculation.poll().orElseThrow();
            assertTrue(completion.revalidate(4, new SimInstant(2)).isEmpty());
            assertSame(owner, validated.get());
            var exactReady = new CountDownLatch(1); host.onReady(exactReady::countDown);
            var exact = host.beginPreparation(completion.revalidate(3, new SimInstant(2)).orElseThrow());
            exactReady.await(); assertEquals(1, exact.poll().orElseThrow().size());
            assertEquals(List.of(action), host.reconsideredBy(3, 4, new SimInstant(2)), "host wrapping must preserve addressed economic review requests");
        } finally { release.countDown(); }
    }

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
            var ready = new CountDownLatch(1); host.onReady(ready::countDown);
            var calculation = host.begin(3, action, new SimInstant(1));
            entered.await();
            assertTrue(calculation.poll().isEmpty()); assertNull(completedOn.get());
            assertNotSame(owner, preparedOn.get());
            release.countDown();
            ready.await(); assertNull(completedOn.get(), "readiness cannot publish events on the worker");
            Optional<List<ProposedEvent>> result;
            do { result = calculation.poll(); if (result.isEmpty()) Thread.yield(); } while (result.isEmpty());
            assertEquals(1, result.orElseThrow().size()); assertSame(owner, completedOn.get());
        } finally { release.countDown(); }
    }
}
