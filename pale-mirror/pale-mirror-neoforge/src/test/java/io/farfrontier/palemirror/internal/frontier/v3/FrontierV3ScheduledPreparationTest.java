package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ScheduledPreparationTest {
    @ParameterizedTest @ValueSource(ints = {2, 4}) @Timeout(10)
    void disposableAdviceRunsOffOwnerButOnlyCurrentOwnerMayAuthorizeItsExactContinuation(int workers) throws Exception {
        var owner = Thread.currentThread(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var otherEntered = new CountDownLatch(1); var allEntered = new CountDownLatch(workers);
        var computed = new AtomicReference<Thread>(); var validated = new AtomicReference<Thread>();
        var action = new ScheduledAction(new ScheduleId("schedule:advice"), new SimInstant(1), 0, new SubjectId("subject:advice"), "advice", 1);
        ScheduledActionPlanner<Integer> planner = new ScheduledActionPlanner<>() {
            public List<ProposedEvent> plan(Integer state, ScheduledAction ignored) { throw new AssertionError("advice bypass"); }
            public List<ScheduledAction> reconsideredBy(Integer before, Integer after, SimInstant at) { return List.of(action); }
            public Optional<ScheduledAdvisory<Integer>> advice(Integer captured, ScheduledAction ignored, SimInstant at) {
                return Optional.of(() -> {
                    computed.set(Thread.currentThread()); allEntered.countDown();
                    if (captured == 7) otherEntered.countDown(); else entered.countDown();
                    try { release.await(); } catch (InterruptedException failure) { throw new IllegalStateException(failure); }
                    return (current, now) -> {
                        validated.set(Thread.currentThread());
                        return current.equals(captured) ? Optional.of(() -> () -> List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id()))))
                                : Optional.empty();
                    };
                });
            }
        };
        try (var host = new FrontierV3ScheduledPreparation<>(planner, "frontier:parallel-advice", workers)) {
            var ready = new CountDownLatch(workers + 1); host.onReady(ready::countDown);
            var calculation = host.beginAdvice(host.advice(3, action, new SimInstant(1)).orElseThrow()).orElseThrow();
            entered.await(); assertFalse(calculation.ready()); assertTrue(calculation.poll().isEmpty()); assertNull(validated.get());
            assertNotSame(owner, computed.get());
            var other = host.beginAdvice(host.advice(7, action, new SimInstant(1)).orElseThrow()).orElseThrow();
            otherEntered.await();
            for (int i = 2; i < workers; i++)
                host.beginAdvice(host.advice(100 + i, action, new SimInstant(1)).orElseThrow()).orElseThrow();
            allEntered.await();
            var queued = host.beginAdvice(host.advice(701, action, new SimInstant(1)).orElseThrow()).orElseThrow();
            assertTrue(queued.poll().isEmpty()); assertFalse(host.adviceAvailable());
            assertTrue(host.beginAdvice(host.advice(702, action, new SimInstant(1)).orElseThrow()).isEmpty(),
                    "more compute workers must not expand the waiting queue or consume auxiliary headroom");
            assertTrue(other.poll().isEmpty());
            assertEquals(1, release.getCount(), "one blocked settlement forecast cannot serialize an independent forecast");
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
            assertTrue(calculation.ready());
            var completion = calculation.poll().orElseThrow();
            assertTrue(queued.poll().orElseThrow().revalidate(4, new SimInstant(2)).isEmpty());
            assertTrue(other.poll().orElseThrow().revalidate(4, new SimInstant(2)).isEmpty());
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
        var path = java.nio.file.Files.createTempFile("pm-delay-trace-", ".jfr");
        try (var recording = new jdk.jfr.Recording(); var host = new FrontierV3ScheduledPreparation<>(planner, "frontier:trace-test")) {
            recording.enable("pale_mirror.DelayPoint"); recording.start();
            var ready = new CountDownLatch(1); host.onReady(ready::countDown);
            try (var correlation = FrontierV3DelayTrace.action(new WorldId("frontier:trace-test"), action, new Revision(7), new SimInstant(1))) {
            var calculation = host.begin(3, action, new SimInstant(1));
            entered.await();
            assertTrue(calculation.poll().isEmpty()); assertNull(completedOn.get());
            assertNotSame(owner, preparedOn.get());
            release.countDown();
            ready.await(); assertNull(completedOn.get(), "readiness cannot publish events on the worker");
            Optional<List<ProposedEvent>> result;
            do { result = calculation.poll(); if (result.isEmpty()) Thread.yield(); } while (result.isEmpty());
            assertEquals(1, result.orElseThrow().size()); assertSame(owner, completedOn.get());
            }
            recording.stop(); recording.dump(path);
            var points = jdk.jfr.consumer.RecordingFile.readAllEvents(path).stream()
                    .filter(e -> e.getEventType().getName().equals("pale_mirror.DelayPoint") && "EXACT".equals(e.getString("type"))).toList();
            assertEquals(Set.of("QUEUED", "RUNNING", "COMPUTED", "READY", "OWNER_COMPLETION_BEGIN", "OWNER_COMPLETION_END"),
                    points.stream().map(e -> e.getString("phase")).collect(java.util.stream.Collectors.toSet()));
            assertEquals(1, points.stream().map(e -> e.getLong("id")).distinct().count());
            assertTrue(points.stream().allMatch(e -> "frontier:trace-test".equals(e.getString("world"))
                    && action.id().value().equals(e.getString("action")) && e.getLong("revision") == 7 && e.getLong("parent") > 0));
        } finally { release.countDown(); java.nio.file.Files.deleteIfExists(path); }
    }
}
