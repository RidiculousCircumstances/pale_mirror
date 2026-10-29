package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3DelayTraceTest {
    @Test void notificationCoalescingAndNestedAdvanceRetainExplicitParentsAndRestoreOnFailure() throws Exception {
        var path = Files.createTempFile("pm-causal-trace-", ".jfr");
        var queue = new ArrayDeque<Runnable>(); var resumed = new java.util.concurrent.atomic.AtomicInteger();
        var world = new WorldId("frontier:causal-test");
        var budget = new WorkBudget(8, 8, 8, 8, 8);
        var action = new ScheduledAction(new ScheduleId("schedule:causal"), new SimInstant(1), 0, new SubjectId("subject:test"), "test", 1);
        try (var recording = new Recording()) {
            recording.enable("pale_mirror.DelayPoint"); recording.enable("pale_mirror.ClockTurn");
            recording.enable("pale_mirror.ClockAttempt"); recording.start();
            var ready = new FrontierV3ReadyTurn(world.value(), queue::add, () -> {
                var turn = FrontierV3DelayTrace.turn(world.value(), 1);
                var attempt = FrontierV3DelayTrace.attempt(world.value(), 1, 2, budget);
                try (var pass = FrontierV3DelayTrace.action(world, action, new Revision(1), new SimInstant(1))) { resumed.incrementAndGet(); }
                finally {
                    FrontierV3DelayTrace.close(attempt, 2, 0, null);
                    FrontierV3DelayTrace.close(turn, 2, 0, 1, "NO_DEBT", 0, null, budget, "");
                }
            });
            var origin = FrontierV3DelayTrace.task("EXACT", world.value());
            origin.mark("QUEUED"); origin.mark("RUNNING"); origin.mark("COMPUTED"); origin.mark("READY");
            origin.notifyReady(ready::signal); origin.notifyReady(ready::signal);
            assertEquals(1, queue.size()); assertEquals(0, resumed.get());
            queue.remove().run(); assertEquals(1, resumed.get());
            assertThrows(IllegalArgumentException.class, () -> origin.run(() -> { throw new IllegalArgumentException("nested failure"); }));
            FrontierV3DelayTrace.task("AFTER_FAILURE", world.value()).mark("QUEUED");
            recording.stop(); recording.dump(path);
            var events = RecordingFile.readAllEvents(path);
            var points = events.stream().filter(e -> e.getEventType().getName().equals("pale_mirror.DelayPoint")).toList();
            var notification = points.stream().filter(e -> "READY_TURN".equals(e.getString("type")) && "QUEUED".equals(e.getString("phase"))).findFirst().orElseThrow();
            assertEquals(origin.id, notification.getLong("parent"));
            assertTrue(points.stream().anyMatch(e -> "COALESCED".equals(e.getString("phase"))
                    && e.getLong("id") == notification.getLong("id") && e.getLong("causedBy") == origin.id));
            var turn = events.stream().filter(e -> e.getEventType().getName().equals("pale_mirror.ClockTurn")).findFirst().orElseThrow();
            assertEquals(notification.getLong("id"), turn.getLong("parent"));
            var attempt = events.stream().filter(e -> e.getEventType().getName().equals("pale_mirror.ClockAttempt")).findFirst().orElseThrow();
            assertEquals(turn.getLong("id"), attempt.getLong("parent"));
            var pass = points.stream().filter(e -> "ACTION".equals(e.getString("type")) && "BEGIN".equals(e.getString("phase"))).findFirst().orElseThrow();
            assertEquals(attempt.getLong("id"), pass.getLong("parent"));
            var after = points.stream().filter(e -> "AFTER_FAILURE".equals(e.getString("type"))).findFirst().orElseThrow();
            assertEquals(0, after.getLong("parent")); assertEquals(0, after.getLong("turn")); assertEquals(0, after.getLong("advance"));
        } finally { Files.deleteIfExists(path); }
    }
}
