package io.farfrontier.palemirror.internal.world;

/**
 * Durable pacing for the indivisible source-world day.  This is presentation
 * scheduling only: it never changes the deterministic source transaction.
 */
enum SourceGrayboxClockProfile {
    GAMEPLAY("gameplay", 24_000L),
    FAST_GRAYBOX("fast_graybox", 1_200L);

    private final String id;
    private final long dayIntervalTicks;

    SourceGrayboxClockProfile(String id, long dayIntervalTicks) {
        this.id = id;
        this.dayIntervalTicks = dayIntervalTicks;
    }

    String id() { return id; }
    long dayIntervalTicks() { return dayIntervalTicks; }

    static SourceGrayboxClockProfile fromId(String id) {
        for (SourceGrayboxClockProfile profile : values()) {
            if (profile.id.equals(id)) return profile;
        }
        throw new IllegalArgumentException("unknown source graybox clock profile: " + id);
    }
}
