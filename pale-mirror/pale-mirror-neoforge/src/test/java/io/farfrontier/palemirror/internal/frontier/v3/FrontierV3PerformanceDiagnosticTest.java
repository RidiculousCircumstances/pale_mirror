package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierExecutionMetrics;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3PerformanceDiagnosticTest {
    @Test void performanceDistinguishesStaleHistoryFromCurrentReadyPressureAndKeepsTrueOverflowStage() {
        var metrics = new FrontierV3PerformanceMetrics();
        var action = new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:old-pressure"),
                new SimInstant(1), 0, new SubjectId("settlement:1"), "process.old", 1);
        metrics.observeQueue(new SimInstant(100), 20, 0, Optional.of(action));
        metrics.observeQueue(new SimInstant(103), 19, 2, Optional.empty());
        metrics.observePressure(new FrontierExecutionMetrics.QueuePressure(103, 19, 0, 2, 11, 6, 0, 0));
        for (int i = 0; i < 260; i++) metrics.begin(FrontierExecutionMetrics.Stage.VALIDATION, "validate-" + i, "world").close();
        metrics.begin(FrontierExecutionMetrics.Stage.HOST_TURN, "pm-turn-including-durability", "world").close();
        assertTrue(metrics.snapshot().stages().stream().anyMatch(value -> value.stage() == FrontierExecutionMetrics.Stage.VALIDATION && value.kind().equals("other")));
        assertTrue(metrics.snapshot().stages().stream().noneMatch(value -> value.stage() == FrontierExecutionMetrics.Stage.PHYSICAL));
        var checkpoint = new CheckpointImage(new WorldId("frontier:pressure-freshness"),
                new io.farfrontier.palemirror.frontier.v3.api.Revision(1), new SimInstant(103), new byte[]{1}, List.of(), List.of());
        var json = FrontierV3PerformanceDiagnostic.render(checkpoint, metrics.snapshot());
        assertTrue(json.contains("\"currentLagTicks\":null"), "historical owner lag must not masquerade as current pressure");
        assertTrue(json.contains("\"lastObservedLagTicks\":99"));
        assertTrue(json.contains("\"ready\":0,\"held\":2,\"future\":11"), json);
        assertTrue(json.contains("\"checking\":6"));
        assertTrue(json.contains("\"readinessScope\":\"LAST_ASSESSED_RECHECK_BEFORE_EXECUTION\""));
        assertTrue(json.contains("\"hostTurn\":{\"samples\":1") && json.contains("\"recentSamples\":1"));
    }
}
