package io.farfrontier.palemirror.api;

public record ResidentDeathObservation(String observationId, String dimensionId, String regionPlanId, String residentId,
                                       String cohort, String causeKind, String actorId) {
    public ResidentDeathObservation {
        if (observationId == null || observationId.isBlank() || dimensionId == null || dimensionId.isBlank()
                || regionPlanId == null || regionPlanId.isBlank()
                || residentId == null || residentId.isBlank() || cohort == null || cohort.isBlank()
                || causeKind == null || causeKind.isBlank()) {
            throw new IllegalArgumentException("Resident death observation identity is incomplete");
        }
        actorId = actorId == null ? "" : actorId;
    }
}
