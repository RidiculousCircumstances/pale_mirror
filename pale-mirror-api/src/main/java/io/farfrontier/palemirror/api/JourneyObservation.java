package io.farfrontier.palemirror.api;

public record JourneyObservation(String observationId, String journeyId, Type type,
                                 int checkpointIndex, String residentId, String diagnostic) {
    public enum Type { IDENTITY_RETIRED, CHECKPOINT_REACHED, BLOCKED }
}
