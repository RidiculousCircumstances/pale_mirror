package io.farfrontier.palemirror.frontier.v3.model;

/** Readable graybox severity for one canonical four-by-four infection surface cell. */
public enum InfectionOverlayStage {
    TRACE,
    INFESTED,
    BLOOM,
    SATURATED;

    public static InfectionOverlayStage fromRaw(long intensity) {
        if (intensity >= 750_000L) return SATURATED;
        if (intensity >= 500_000L) return BLOOM;
        if (intensity >= 250_000L) return INFESTED;
        return TRACE;
    }
}
