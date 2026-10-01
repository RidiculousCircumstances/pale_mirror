package io.farfrontier.palemirror.frontier.v3.time;

import io.farfrontier.palemirror.frontier.v3.model.SettlementDailySchedule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationCalendarTest {
    @Test void scheduleAndPresentationUseTheSameDayBoundary() {
        var schedule = SettlementDailySchedule.initial();
        var calendar = schedule.calendar();
        assertEquals(11_999, calendar.at(11_999).tickOfDay());
        assertEquals(SettlementDailySchedule.Window.WORK, schedule.windowAt(11_999));
        assertEquals(SettlementDailySchedule.Window.FREE, schedule.windowAt(12_000));
        assertEquals(12_000, calendar.at(12_000).cycleTick(24_000, 8));
        assertEquals(1, calendar.at(24_000).dayIndex());
        assertEquals(0, calendar.at(24_000).tickOfDay());
        assertEquals(SettlementDailySchedule.Window.WORK, schedule.windowAt(24_000));
    }

    @Test void projectionScalesAConfiguredDayWithoutChangingCanonicalTime() {
        var reading = new SimulationCalendar(48_000).at(60_000);
        assertEquals(60_000, reading.canonicalTick());
        assertEquals(48_000, reading.dayStart());
        assertEquals(30_000, reading.cycleTick(24_000, 8));
        var extreme = new SimulationCalendar(2).at(Long.MAX_VALUE);
        assertEquals(168_000 + 12_000, extreme.cycleTick(24_000, 8));
        assertEquals(Long.MAX_VALUE - 1, extreme.dayStart());
    }

    @Test void calendarIsStatelessAndRejectsInventedReadings() {
        var calendar = new SimulationCalendar(24_000);
        assertEquals(calendar.at(19_123), new SimulationCalendar(24_000).at(19_123));
        assertThrows(IllegalArgumentException.class, () -> calendar.at(-1));
        assertThrows(IllegalArgumentException.class, () -> new SimulationCalendar(1));
        assertThrows(IllegalArgumentException.class, () -> new SimulationCalendar.Reading(7, 0, 0, 6, 24_000));
        assertThrows(IllegalArgumentException.class, () -> calendar.at(0).cycleTick(0, 8));
    }

    @Test void worldRejectsASettlementScheduleWithAnIndependentDayLength() {
        var state = io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState.initial(
                io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper.create(
                        new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:calendar-test"), 47L));
        var settlement = state.bootstrap().settlements().getFirst().id();
        var alien = new SettlementDailySchedule(48_000, java.util.List.of(
                new SettlementDailySchedule.Segment(0, 24_000, SettlementDailySchedule.Window.WORK),
                new SettlementDailySchedule.Segment(24_000, 48_000, SettlementDailySchedule.Window.FREE)));
        var population = state.humanPopulation().withSchedule(settlement, alien);
        assertThrows(IllegalArgumentException.class, () -> state.withHumanPopulation(population));
    }
}
