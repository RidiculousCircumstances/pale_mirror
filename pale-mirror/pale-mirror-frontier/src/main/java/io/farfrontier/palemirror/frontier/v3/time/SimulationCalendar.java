package io.farfrontier.palemirror.frontier.v3.time;

/** Pure calendar policy over canonical elapsed time; it owns no ticking or persistence. */
public record SimulationCalendar(int ticksPerDay) {
    public SimulationCalendar {
        if (ticksPerDay < 2) throw new IllegalArgumentException("calendar day must contain at least two ticks");
    }

    public Reading at(long canonicalTick) {
        if (canonicalTick < 0) throw new IllegalArgumentException("calendar instant must be non-negative");
        return new Reading(canonicalTick, canonicalTick / ticksPerDay,
                canonicalTick - canonicalTick % ticksPerDay, (int) (canonicalTick % ticksPerDay), ticksPerDay);
    }

    public record Reading(long canonicalTick, long dayIndex, long dayStart, int tickOfDay, int ticksPerDay) {
        public Reading {
            if (canonicalTick < 0 || ticksPerDay < 2 || dayIndex != canonicalTick / ticksPerDay
                    || tickOfDay != canonicalTick % ticksPerDay || dayStart != canonicalTick - tickOfDay) {
                throw new IllegalArgumentException("calendar reading must describe exactly one canonical instant");
            }
        }

        /** Bounded cyclic projection (e.g. Minecraft's eight-day lunar cycle), not another clock. */
        public long cycleTick(int presentationTicksPerDay, int daysPerCycle) {
            if (presentationTicksPerDay < 1 || daysPerCycle < 1) {
                throw new IllegalArgumentException("presentation cycle must be positive");
            }
            return (dayIndex % daysPerCycle) * presentationTicksPerDay
                    + (long) tickOfDay * presentationTicksPerDay / ticksPerDay;
        }
    }
}
