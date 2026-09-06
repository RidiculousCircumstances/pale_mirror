package io.farfrontier.palemirror.api;

/** A bounded fact from a current Frontier physical carrier; Core validates it before mutation. */
public record FrontierPhysicalObservation(Type type, String observationId, String subjectId,
                                          String materializationId, long expectedRevision, String causationId) {
    public enum Type { RESIDENT_DIED, FACILITY_DAMAGED, CARGO_LOST, HIVE_ORGAN_DESTROYED, BIOFORM_DIED, LATENT_COLONY_CLEARED }
    public FrontierPhysicalObservation {
        if (type == null || blank(observationId) || blank(subjectId) || blank(materializationId)
                || blank(causationId) || expectedRevision < 0) {
            throw new IllegalArgumentException("invalid Frontier physical observation");
        }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
