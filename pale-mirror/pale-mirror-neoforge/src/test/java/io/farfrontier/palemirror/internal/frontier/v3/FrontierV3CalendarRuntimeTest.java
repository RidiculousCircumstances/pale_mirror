package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.time.SimulationCalendar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3CalendarRuntimeTest {
    @Test void operatorStatusReportsTheReachedPhaseNotTheRequestedTarget() {
        var config = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:calendar-status"), 51L);
        var image = new io.farfrontier.palemirror.frontier.v3.api.CheckpointImage(config.worldId(),
                new io.farfrontier.palemirror.frontier.v3.api.Revision(3L),
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(12_137), new byte[] {1}, java.util.List.of(), java.util.List.of());
        var receipt = new FrontierV3ServerLifecycle.FastForwardRequestOutcome(7L, "RELATIVE", 18_000, 18_000L,
                0L, 12_137L, "REJECTED", "physical work became pending");
        var json = com.google.gson.JsonParser.parseString(FrontierV3DiagnosticJson.operatorStatus(
                image, config.initialState(), java.util.List.of(receipt)).substring(FrontierV3DiagnosticJson.PREFIX.length()))
                .getAsJsonObject().getAsJsonObject("calendar");
        assertEquals(12_137L, json.get("tickOfDay").getAsLong());
        assertEquals(12_137L, json.get("minecraftCycleTime").getAsLong());
        assertEquals(24_000, json.get("ticksPerDay").getAsInt());
    }

    @Test void calendarReadsActualAdvanceAndFreezesAfterQuarantine(@TempDir Path directory) {
        var config = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:calendar-quarantine"), 47L);
        var runtime = FrontierV3ServerRuntime.start(config,
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 200);
        assertEquals(0, runtime.calendarInstant().orElseThrow());
        runtime.advance(3, new WorkBudget(64, 512)).orElseThrow();
        long reached = runtime.calendarInstant().orElseThrow();
        assertEquals(3, reached);
        runtime.quarantine(new IllegalStateException("isolated test stop"));
        assertTrue(runtime.advance(1, new WorkBudget(64, 512)).isEmpty());
        assertEquals(reached, runtime.calendarInstant().orElseThrow());
    }

    @Test void restartDerivesCalendarFromTheRecoveredCanonicalInstant(@TempDir Path directory) {
        var config = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:calendar-restart"), 49L);
        var store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var runtime = FrontierV3ServerRuntime.start(config, store, 200);
        runtime.advance(7, new WorkBudget(64, 512)).orElseThrow();
        var calendar = new SimulationCalendar(config.initialState().bootstrap().ruleset().residentLife().dayTicks());
        var before = calendar.at(runtime.calendarInstant().orElseThrow());
        runtime.shutdown();
        var recovered = FrontierV3ServerRuntime.start(config, store, 200);
        assertEquals(before, calendar.at(recovered.calendarInstant().orElseThrow()));
        recovered.shutdown();
    }
}
