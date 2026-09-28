package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;

/** Immutable settlement policy; it chooses available work time, never the current activity. */
public record SettlementDailySchedule(int dayTicks, List<Segment> segments) {
    public enum Window { WORK, FREE }

    public record Segment(int startInclusive, int endExclusive, Window window) {
        public Segment {
            Objects.requireNonNull(window, "schedule window");
            if (startInclusive < 0 || endExclusive <= startInclusive) {
                throw new IllegalArgumentException("schedule segment must have positive duration");
            }
        }
    }

    public SettlementDailySchedule {
        segments = List.copyOf(Objects.requireNonNull(segments, "schedule segments"));
        if (dayTicks < 2 || segments.size() < 2 || segments.size() > 48
                || segments.getFirst().startInclusive() != 0
                || segments.getLast().endExclusive() != dayTicks
                || segments.stream().map(Segment::window).distinct().count() < 2) {
            throw new IllegalArgumentException("daily schedule needs bounded work and free coverage");
        }
        for (int index = 1; index < segments.size(); index++) {
            Segment before = segments.get(index - 1), after = segments.get(index);
            if (before.endExclusive() != after.startInclusive() || before.window() == after.window()) {
                throw new IllegalArgumentException("daily schedule segments must be contiguous alternating windows");
            }
        }
    }

    public static SettlementDailySchedule initial() {
        return from(FrontierRuleset.ResidentLife.initial());
    }

    public static SettlementDailySchedule from(FrontierRuleset.ResidentLife rules) {
        Objects.requireNonNull(rules, "resident life rules");
        return new SettlementDailySchedule(rules.dayTicks(), List.of(
                new Segment(0, rules.workTicks(), Window.WORK),
                new Segment(rules.workTicks(), rules.dayTicks(), Window.FREE)));
    }

    public Window windowAt(long canonicalTick) {
        if (canonicalTick < 0) throw new IllegalArgumentException("schedule instant must be non-negative");
        int offset = (int) (canonicalTick % dayTicks);
        for (Segment segment : segments) if (offset < segment.endExclusive()) return segment.window();
        throw new IllegalStateException("validated schedule omitted its day tail");
    }

    public long nextWindowBoundaryAfter(long canonicalTick) {
        if (canonicalTick < 0) throw new IllegalArgumentException("schedule instant must be non-negative");
        long dayStart = Math.multiplyExact(canonicalTick / dayTicks, dayTicks);
        int offset = (int) (canonicalTick % dayTicks);
        for (Segment segment : segments) if (offset < segment.endExclusive()) {
            return Math.addExact(dayStart, segment.endExclusive());
        }
        throw new IllegalStateException("validated schedule omitted its day tail");
    }
}
