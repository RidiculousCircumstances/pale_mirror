package io.farfrontier.palemirror.frontier.v3.model;

/** Canonical retention headroom, not a physical mob/scene admission limit. */
public final class SimulationCapacity {
    public static final int MAX_SETTLEMENTS = 100;
    public static final int MAX_RESIDENTS = 8_192;
    public static final int MAX_HOUSEHOLDS = 2_048;
    public static final int MAX_EXTRACTION_SITES = 256;
    public static final int MAX_EXTRACTION_WORK = MAX_SETTLEMENTS * 8;
    public static final int MAX_SCHEDULES = 65_535;
    public static final int MAX_ACTORS = 16_384;
    /** Retained COLD/HOT lifecycle records, not concurrently materialized scenes. */
    public static final int MAX_LIFECYCLE_WORK_PER_OWNER = 512;
    public static final int MAX_PHYSICAL_INTENTS = 8_192;
    private SimulationCapacity() { }
}
