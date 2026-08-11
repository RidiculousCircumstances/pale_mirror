package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Authored relationships for one causal region; crisis is owned by its community, not this record. */
public final class LivingRegionState {
    private final String id;
    private final WorldObjectId communityId;
    private final WorldObjectId placeId;
    private final WorldObjectId primaryFacilityId;
    private final WorldObjectId alternateFacilityId;
    private final WorldObjectId primaryRouteId;
    private final WorldObjectId alternateRouteId;
    private final long incidentDelaySteps;
    private StoryAudienceId primaryAudience;
    private RecognitionState recognition;
    private long discoveredAtStep;
    private final boolean knowledgeGatedIncident;

    public LivingRegionState(String id, WorldObjectId communityId, WorldObjectId placeId,
                             WorldObjectId primaryFacilityId, WorldObjectId alternateFacilityId,
                             WorldObjectId primaryRouteId, WorldObjectId alternateRouteId,
                             long incidentDelaySteps, StoryAudienceId primaryAudience,
                             RecognitionState recognition, long discoveredAtStep) {
        this(id, communityId, placeId, primaryFacilityId, alternateFacilityId, primaryRouteId,
                alternateRouteId, incidentDelaySteps, primaryAudience, recognition, discoveredAtStep, false);
    }

    public LivingRegionState(String id, WorldObjectId communityId, WorldObjectId placeId,
                             WorldObjectId primaryFacilityId, WorldObjectId alternateFacilityId,
                             WorldObjectId primaryRouteId, WorldObjectId alternateRouteId,
                             long incidentDelaySteps, StoryAudienceId primaryAudience,
                             RecognitionState recognition, long discoveredAtStep,
                             boolean knowledgeGatedIncident) {
        this.id = Objects.requireNonNull(id, "id");
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        this.placeId = Objects.requireNonNull(placeId, "placeId");
        this.primaryFacilityId = Objects.requireNonNull(primaryFacilityId, "primaryFacilityId");
        this.alternateFacilityId = Objects.requireNonNull(alternateFacilityId, "alternateFacilityId");
        this.primaryRouteId = Objects.requireNonNull(primaryRouteId, "primaryRouteId");
        this.alternateRouteId = Objects.requireNonNull(alternateRouteId, "alternateRouteId");
        if (incidentDelaySteps < 0 || discoveredAtStep < -1) throw new IllegalArgumentException("Invalid region timing");
        this.incidentDelaySteps = incidentDelaySteps;
        this.primaryAudience = primaryAudience;
        this.recognition = Objects.requireNonNull(recognition, "recognition");
        this.discoveredAtStep = discoveredAtStep;
        this.knowledgeGatedIncident = knowledgeGatedIncident;
    }

    public String id() { return id; }
    public WorldObjectId communityId() { return communityId; }
    public WorldObjectId placeId() { return placeId; }
    public WorldObjectId primaryFacilityId() { return primaryFacilityId; }
    public WorldObjectId alternateFacilityId() { return alternateFacilityId; }
    public WorldObjectId primaryRouteId() { return primaryRouteId; }
    public WorldObjectId alternateRouteId() { return alternateRouteId; }
    public long incidentDelaySteps() { return incidentDelaySteps; }
    public StoryAudienceId primaryAudience() { return primaryAudience; }
    public RecognitionState recognition() { return recognition; }
    public long discoveredAtStep() { return discoveredAtStep; }
    public boolean knowledgeGatedIncident() { return knowledgeGatedIncident; }

    public boolean recognize(StoryAudienceId audience, long simulationStep) {
        if (recognition != RecognitionState.DISCOVERED) return false;
        primaryAudience = Objects.requireNonNull(audience, "audience");
        recognition = RecognitionState.RECOGNIZED;
        discoveredAtStep = simulationStep;
        return true;
    }

    public boolean incidentDue(long simulationStep) {
        return incidentDue(simulationStep, null);
    }

    public boolean incidentDue(long simulationStep, AudienceRegionKnowledge knowledge) {
        if (recognition != RecognitionState.RECOGNIZED || discoveredAtStep < 0) return false;
        if (!knowledgeGatedIncident) return simulationStep >= discoveredAtStep + incidentDelaySteps;
        return knowledge != null && primaryAudience != null && knowledge.audience().equals(primaryAudience)
                && knowledge.regionId().equals(id) && knowledge.supplyChainDiscoveredAtStep() >= 0
                && simulationStep >= knowledge.supplyChainDiscoveredAtStep() + incidentDelaySteps;
    }
}
