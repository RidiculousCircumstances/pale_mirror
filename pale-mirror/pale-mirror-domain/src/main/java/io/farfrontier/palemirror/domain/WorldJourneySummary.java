package io.farfrontier.palemirror.domain;

/** Aggregate receipt for terminal journeys removed from the detailed window. */
public record WorldJourneySummary(JourneyState outcome, long count, long confirmedLosses,
                                  long lastCompletedStep) {
    public WorldJourneySummary {
        if (outcome != JourneyState.ARRIVED && outcome != JourneyState.LOST && outcome != JourneyState.CANCELLED) {
            throw new IllegalArgumentException("Journey summary requires a terminal outcome");
        }
        if (count < 1 || confirmedLosses < 0 || lastCompletedStep < 0) {
            throw new IllegalArgumentException("Invalid journey summary");
        }
    }
}
