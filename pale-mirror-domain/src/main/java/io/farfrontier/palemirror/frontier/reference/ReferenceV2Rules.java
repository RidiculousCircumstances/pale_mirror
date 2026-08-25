package io.farfrontier.palemirror.frontier.reference;

/** Source-pinned V2 constants used by the initial territorial cognition cut. */
final class ReferenceV2Rules {
    static final int SECTOR_SIZE = 4;
    static final double HUMAN_ACCESS_RADIUS = 7.0d;
    static final double HUMAN_OBSERVATION_RADIUS = 8.0d;
    static final double HIVE_OBSERVATION_THRESHOLD = 0.28d;
    static final double ROUTE_VALUE = 0.40d;
    static final double SCAR_ACCESS_PENALTY = 0.35d;
    static final double HUMAN_DECAY_PER_DAY = 0.035d;
    static final double HIVE_DECAY_PER_DAY = 0.055d;
    static final double MINIMUM_ACTION_CONFIDENCE = 0.42d;
    static final double EMERGENCY_RESERVE_DAYS = 10.0d;
    static final double MEDICINE_RESERVE_DAYS = 6.0d;

    private ReferenceV2Rules() { }

    static double siteValue(ReferenceSiteKind kind) {
        return switch (kind) {
            case FARM -> 1.35d;
            case MINE -> 0.95d;
            case FOREST -> 0.75d;
            case POWER -> 1.10d;
        };
    }
}
