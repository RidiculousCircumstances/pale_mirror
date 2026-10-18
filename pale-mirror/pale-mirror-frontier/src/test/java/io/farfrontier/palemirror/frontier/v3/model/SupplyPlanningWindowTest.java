package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupplyPlanningWindowTest {
    @Test void fourDayForecastUsesFourDaysOfLabourAndOnlyActualPredecessorWork() {
        var schedule = new SettlementDailySchedule(24_000, List.of(
                new SettlementDailySchedule.Segment(0, 12_000, SettlementDailySchedule.Window.WORK),
                new SettlementDailySchedule.Segment(12_000, 24_000, SettlementDailySchedule.Window.FREE)));
        assertEquals(48_000, new SupplyPlanningWindow(96_000).workTicks(schedule));
        assertEquals(0, SupplyPlanningWindow.completionDelay(schedule, 0));
        assertEquals(12_100, SupplyPlanningWindow.completionDelay(schedule, 100));
        assertEquals(60_100, SupplyPlanningWindow.completionDelay(schedule, 24_100));
    }
    @Test void repeatedCropsAreDatedWavesNotContinuousInventedOutput() {
        var process = new SupplyPlanner.Process("field", "wheat", 1, Map.of(), 0, Map.of(), 0,
                100, 1, 128, 1, Map.of("worker", 1000L), 1,
                List.of(new SupplyPlanner.OutputRelease(100, 64), new SupplyPlanner.OutputRelease(1000, 128)));
        assertEquals(0, process.recipesAvailableBy(99));
        assertEquals(64, process.recipesAvailableBy(999));
        assertEquals(128, process.recipesAvailableBy(1000));
        assertEquals(1000, process.completionTicks(65));
    }
}
