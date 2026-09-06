package io.farfrontier.palemirror.frontier;

import java.util.Objects;

/** Bounded history entry; a rejected observation is visible but does not mutate the target. */
public record FrontierEvent(long sequence, long day, Type type, String subjectId, String causationId) {
    public enum Type { WORLD_CREATED, DAY_ADVANCED, RESOURCE_PRODUCED, FOOD_SHORTAGE, SETTLEMENT_CIVIC_CHANGED, OPERATION_STATE_CHANGED,
        CARGO_DISPATCHED, CARGO_DELIVERED, CARGO_LOST, HIVE_STATE_CHANGED, HIVE_BIOMASS_GROWN,
        HIVE_ORGAN_DESTROYED, MORPHOGENESIS_STARTED, MORPHOGENESIS_COMPLETED, MORPHOGENESIS_ABORTED,
        MORPHOGENESIS_REJECTED,
        HARVESTER_LAUNCHED, HARVESTER_STATE_CHANGED, HARVESTER_RETURNED, HARVESTER_ABORTED, HARVESTER_REJECTED,
        PROPAGATION_RUN_LAUNCHED, PROPAGATION_RUN_STATE_CHANGED, PROPAGATION_RUN_DEPLOYED, PROPAGATION_RUN_ABORTED, PROPAGATION_RUN_REJECTED,
        LATENT_COLONY_MATURED, LATENT_COLONY_CLEARED, ADAPTATION_ACQUIRED,
        BIOFORM_SPAWNED, BIOFORM_DIED, ASSAULT_LAUNCHED, ASSAULT_STATE_CHANGED,
        ASSAULT_RESOLVED, FIELD_OPERATION_LAUNCHED, FIELD_OPERATION_STATE_CHANGED, FIELD_OPERATION_RESOLVED,
        CAMPAIGN_LAUNCHED, CAMPAIGN_STATE_CHANGED, CAMPAIGN_RESOLVED,
        RESIDENT_DIED, FACILITY_DAMAGED, OBSERVATION_REJECTED }
    public FrontierEvent {
        if (sequence < 1 || day < 0) throw new IllegalArgumentException("invalid event sequence or day");
        Objects.requireNonNull(type, "type");
        if (subjectId == null || subjectId.isBlank() || causationId == null || causationId.isBlank()) {
            throw new IllegalArgumentException("event identity is required");
        }
    }
}
