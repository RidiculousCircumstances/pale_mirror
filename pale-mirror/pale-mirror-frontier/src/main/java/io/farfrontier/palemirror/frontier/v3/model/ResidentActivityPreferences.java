package io.farfrontier.palemirror.frontier.v3.model;

/** Selection preferences only: these ranks grant no execution or physical authority. */
public final class ResidentActivityPreferences {
    private ResidentActivityPreferences() { }

    public enum Priority {
        IDLE(0), OPTIONAL_WORK(1), SCHEDULED_WORK(2), FOOD(3);
        private final int rank;
        Priority(int rank) { this.rank = rank; }
        public boolean outranks(Priority other) { return rank > other.rank; }
    }

    public static Priority work(SettlementDailySchedule schedule, long tick) {
        return switch (schedule.windowAt(tick)) {
            case WORK -> Priority.SCHEDULED_WORK;
            case FREE -> Priority.OPTIONAL_WORK;
        };
    }
}
