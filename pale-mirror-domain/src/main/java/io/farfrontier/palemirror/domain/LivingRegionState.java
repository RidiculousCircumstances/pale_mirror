package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** One authored causal region; physical coordinates belong to the NeoForge presentation record. */
public final class LivingRegionState {
    private final String id;
    private final WorldObjectId settlementId;
    private final WorldObjectId primaryFacilityId;
    private final WorldObjectId alternateFacilityId;
    private final WorldObjectId primaryRouteId;
    private final WorldObjectId alternateRouteId;
    private final long crisisDelaySteps;
    private StoryAudienceId primaryAudience;
    private LivingRegionStatus status;
    private long discoveredAtStep;

    public LivingRegionState(String id, WorldObjectId settlementId, WorldObjectId primaryFacilityId,
                             WorldObjectId alternateFacilityId, WorldObjectId primaryRouteId,
                             WorldObjectId alternateRouteId) {
        this(id, settlementId, primaryFacilityId, alternateFacilityId, primaryRouteId, alternateRouteId,
                2, null, LivingRegionStatus.PLANNED, -1);
    }

    public LivingRegionState(String id, WorldObjectId settlementId, WorldObjectId primaryFacilityId,
                             WorldObjectId alternateFacilityId, WorldObjectId primaryRouteId,
                             WorldObjectId alternateRouteId, long crisisDelaySteps, StoryAudienceId primaryAudience,
                             LivingRegionStatus status, long discoveredAtStep) {
        this.id = Objects.requireNonNull(id, "id");
        this.settlementId = Objects.requireNonNull(settlementId, "settlementId");
        this.primaryFacilityId = Objects.requireNonNull(primaryFacilityId, "primaryFacilityId");
        this.alternateFacilityId = Objects.requireNonNull(alternateFacilityId, "alternateFacilityId");
        this.primaryRouteId = Objects.requireNonNull(primaryRouteId, "primaryRouteId");
        this.alternateRouteId = Objects.requireNonNull(alternateRouteId, "alternateRouteId");
        if (crisisDelaySteps < 0) throw new IllegalArgumentException("Crisis delay must not be negative");
        this.crisisDelaySteps = crisisDelaySteps;
        this.primaryAudience = primaryAudience;
        this.status = Objects.requireNonNull(status, "status");
        this.discoveredAtStep = discoveredAtStep;
    }

    public String id() { return id; }
    public WorldObjectId settlementId() { return settlementId; }
    public WorldObjectId primaryFacilityId() { return primaryFacilityId; }
    public WorldObjectId alternateFacilityId() { return alternateFacilityId; }
    public WorldObjectId primaryRouteId() { return primaryRouteId; }
    public WorldObjectId alternateRouteId() { return alternateRouteId; }
    public long crisisDelaySteps() { return crisisDelaySteps; }
    public StoryAudienceId primaryAudience() { return primaryAudience; }
    public LivingRegionStatus status() { return status; }
    public long discoveredAtStep() { return discoveredAtStep; }
    public boolean discover(StoryAudienceId audience, long simulationStep) {
        if (status != LivingRegionStatus.PLANNED) return false;
        primaryAudience = Objects.requireNonNull(audience, "audience");
        status = LivingRegionStatus.DISCOVERED;
        discoveredAtStep = simulationStep;
        return true;
    }
    public boolean crisisDue(long simulationStep) {
        return status == LivingRegionStatus.DISCOVERED && discoveredAtStep >= 0 && simulationStep >= discoveredAtStep + crisisDelaySteps;
    }
    public boolean activateCrisis() {
        if (status != LivingRegionStatus.DISCOVERED) return false;
        status = LivingRegionStatus.CRISIS_ACTIVE;
        return true;
    }
    public boolean resolve() {
        if (status != LivingRegionStatus.CRISIS_ACTIVE) return false;
        status = LivingRegionStatus.RESOLVED;
        return true;
    }
}
