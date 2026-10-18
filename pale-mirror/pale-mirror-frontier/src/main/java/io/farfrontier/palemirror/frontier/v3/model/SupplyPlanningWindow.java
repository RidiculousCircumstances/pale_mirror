package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner;
import java.util.Collection;

/** Relative forecast interval; no inferred world clock or mutation authority. */
public record SupplyPlanningWindow(long ticks) {
    public SupplyPlanningWindow {
        if (ticks < 1) throw new IllegalArgumentException("empty supply planning window");
    }
    static SupplyPlanningWindow forSettlement(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId home,
            Collection<SupplyReservePlanner.Target> reserves) {
        long day = state.humanPopulation().schedule(home).dayTicks();
        long horizon = state.bootstrap().ruleset().goodsTrade().supplyPlanning().economics()
                .map(rules -> Math.multiplyExact(day, rules.horizonDays())).orElse(day);
        return new SupplyPlanningWindow(Math.max(horizon,
                reserves.stream().mapToLong(SupplyReservePlanner.Target::horizonTicks).max().orElse(day)));
    }
    /** Conservative available work when the caller has no exact calendar phase. */
    long workTicks(SettlementDailySchedule schedule) {
        long work = dailyWork(schedule);
        long completeDays = ticks / schedule.dayTicks();
        long remainder = ticks % schedule.dayTicks();
        return Math.addExact(Math.multiplyExact(completeDays, work),
                Math.max(0, remainder - (schedule.dayTicks() - work)));
    }
    static long dailyWork(SettlementDailySchedule schedule) {
        return schedule.segments().stream().filter(s -> s.window() == SettlementDailySchedule.Window.WORK)
                .mapToLong(s -> s.endExclusive() - s.startInclusive()).sum();
    }
    /** Upper bound includes the unknown starting phase, not two fabricated full days. */
    static long completionDelay(SettlementDailySchedule schedule, long workTicks) {
        if (workTicks < 0) throw new IllegalArgumentException("negative forecast work");
        if (workTicks == 0) return 0;
        long daily = dailyWork(schedule);
        if (daily == 0) throw new IllegalArgumentException("work forecast needs a work window");
        return Math.addExact(workTicks, Math.multiplyExact(Math.ceilDiv(workTicks, daily), schedule.dayTicks() - daily));
    }
}
