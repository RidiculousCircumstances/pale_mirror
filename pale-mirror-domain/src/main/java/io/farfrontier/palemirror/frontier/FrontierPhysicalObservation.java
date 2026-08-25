package io.farfrontier.palemirror.frontier;

import java.util.Objects;

/** Facts observed from a current PM materialization. They grant no direct mutation access. */
public sealed interface FrontierPhysicalObservation permits FrontierPhysicalObservation.ResidentDeath,
        FrontierPhysicalObservation.FacilityDamage, FrontierPhysicalObservation.CargoLost,
        FrontierPhysicalObservation.HiveOrganDestroyed, FrontierPhysicalObservation.BioformDeath,
        FrontierPhysicalObservation.LatentColonyCleared {
    String observationId();
    String materializationId();
    long expectedRevision();
    String causationId();

    record ResidentDeath(String observationId, String residentId, String materializationId,
                         long expectedRevision, String causationId) implements FrontierPhysicalObservation {
        public ResidentDeath { validate(observationId, residentId, materializationId, expectedRevision, causationId); }
    }
    record FacilityDamage(String observationId, String facilityId, String materializationId,
                          long expectedRevision, String causationId) implements FrontierPhysicalObservation {
        public FacilityDamage { validate(observationId, facilityId, materializationId, expectedRevision, causationId); }
    }
    record CargoLost(String observationId, String cargoId, String materializationId,
                     long expectedRevision, String causationId) implements FrontierPhysicalObservation {
        public CargoLost { validate(observationId, cargoId, materializationId, expectedRevision, causationId); }
    }
    record HiveOrganDestroyed(String observationId, String organId, String materializationId,
                              long expectedRevision, String causationId) implements FrontierPhysicalObservation {
        public HiveOrganDestroyed { validate(observationId, organId, materializationId, expectedRevision, causationId); }
    }
    record BioformDeath(String observationId, String bioformId, String materializationId,
                        long expectedRevision, String causationId) implements FrontierPhysicalObservation {
        public BioformDeath { validate(observationId, bioformId, materializationId, expectedRevision, causationId); }
    }
    record LatentColonyCleared(String observationId, String colonyId, String materializationId,
                               long expectedRevision, String causationId) implements FrontierPhysicalObservation {
        public LatentColonyCleared { validate(observationId, colonyId, materializationId, expectedRevision, causationId); }
    }
    private static void validate(String observationId, String subjectId, String materializationId,
                                 long expectedRevision, String causationId) {
        if (observationId == null || observationId.isBlank() || subjectId == null || subjectId.isBlank()
                || materializationId == null || materializationId.isBlank() || causationId == null || causationId.isBlank()
                || expectedRevision < 0) throw new IllegalArgumentException("invalid physical observation");
    }
}
