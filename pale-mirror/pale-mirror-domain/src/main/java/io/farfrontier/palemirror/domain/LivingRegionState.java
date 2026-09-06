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
    private final boolean knowledgeGatedIncident;
    private long firstDiscoveredAtStep;
    private long incidentArmedAtStep;
    private String incidentOutcome;
    private long incidentResolvedAtStep;

    public LivingRegionState(String id, WorldObjectId communityId, WorldObjectId placeId,
                             WorldObjectId primaryFacilityId, WorldObjectId alternateFacilityId,
                             WorldObjectId primaryRouteId, WorldObjectId alternateRouteId,
                             long incidentDelaySteps, boolean knowledgeGatedIncident) {
        this(id, communityId, placeId, primaryFacilityId, alternateFacilityId, primaryRouteId,
                alternateRouteId, incidentDelaySteps, knowledgeGatedIncident, -1L, -1L, "", -1L);
    }

    public LivingRegionState(String id, WorldObjectId communityId, WorldObjectId placeId,
                             WorldObjectId primaryFacilityId, WorldObjectId alternateFacilityId,
                             WorldObjectId primaryRouteId, WorldObjectId alternateRouteId,
                             long incidentDelaySteps, boolean knowledgeGatedIncident,
                             long firstDiscoveredAtStep, long incidentArmedAtStep) {
        this(id, communityId, placeId, primaryFacilityId, alternateFacilityId, primaryRouteId,
                alternateRouteId, incidentDelaySteps, knowledgeGatedIncident, firstDiscoveredAtStep,
                incidentArmedAtStep, "", -1L);
    }

    public LivingRegionState(String id, WorldObjectId communityId, WorldObjectId placeId,
                             WorldObjectId primaryFacilityId, WorldObjectId alternateFacilityId,
                             WorldObjectId primaryRouteId, WorldObjectId alternateRouteId,
                             long incidentDelaySteps, boolean knowledgeGatedIncident,
                             long firstDiscoveredAtStep, long incidentArmedAtStep,
                             String incidentOutcome, long incidentResolvedAtStep) {
        this.id = Objects.requireNonNull(id, "id");
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        this.placeId = Objects.requireNonNull(placeId, "placeId");
        this.primaryFacilityId = Objects.requireNonNull(primaryFacilityId, "primaryFacilityId");
        this.alternateFacilityId = Objects.requireNonNull(alternateFacilityId, "alternateFacilityId");
        this.primaryRouteId = Objects.requireNonNull(primaryRouteId, "primaryRouteId");
        this.alternateRouteId = Objects.requireNonNull(alternateRouteId, "alternateRouteId");
        if (incidentDelaySteps < 0 || firstDiscoveredAtStep < -1 || incidentArmedAtStep < -1
                || incidentResolvedAtStep < -1
                || incidentArmedAtStep >= 0 && (firstDiscoveredAtStep < 0
                || incidentArmedAtStep < firstDiscoveredAtStep)
                || incidentResolvedAtStep >= 0 && (incidentArmedAtStep < 0
                || incidentResolvedAtStep < incidentArmedAtStep)
                || incidentResolvedAtStep < 0 && incidentOutcome != null && !incidentOutcome.isBlank()
                || incidentResolvedAtStep >= 0 && (incidentOutcome == null || incidentOutcome.isBlank())) {
            throw new IllegalArgumentException("Invalid region timing");
        }
        this.incidentDelaySteps = incidentDelaySteps;
        this.knowledgeGatedIncident = knowledgeGatedIncident;
        this.firstDiscoveredAtStep = firstDiscoveredAtStep;
        this.incidentArmedAtStep = incidentArmedAtStep;
        this.incidentOutcome = incidentOutcome == null ? "" : incidentOutcome;
        this.incidentResolvedAtStep = incidentResolvedAtStep;
    }

    public String id() { return id; }
    public WorldObjectId communityId() { return communityId; }
    public WorldObjectId placeId() { return placeId; }
    public WorldObjectId primaryFacilityId() { return primaryFacilityId; }
    public WorldObjectId alternateFacilityId() { return alternateFacilityId; }
    public WorldObjectId primaryRouteId() { return primaryRouteId; }
    public WorldObjectId alternateRouteId() { return alternateRouteId; }
    public long incidentDelaySteps() { return incidentDelaySteps; }
    public boolean knowledgeGatedIncident() { return knowledgeGatedIncident; }
    public long firstDiscoveredAtStep() { return firstDiscoveredAtStep; }
    public long incidentArmedAtStep() { return incidentArmedAtStep; }
    public String incidentOutcome() { return incidentOutcome; }
    public long incidentResolvedAtStep() { return incidentResolvedAtStep; }

    public boolean discover(long simulationStep) {
        if (simulationStep < 0) throw new IllegalArgumentException("Negative discovery step");
        if (firstDiscoveredAtStep >= 0) return false;
        firstDiscoveredAtStep = simulationStep;
        if (!knowledgeGatedIncident) incidentArmedAtStep = simulationStep;
        return true;
    }

    public boolean armIncident(long simulationStep) {
        if (simulationStep < 0 || firstDiscoveredAtStep < 0) {
            throw new IllegalArgumentException("Incident cannot precede region discovery");
        }
        if (incidentArmedAtStep >= 0) return false;
        incidentArmedAtStep = simulationStep;
        return true;
    }

    public boolean incidentDue(long simulationStep) {
        return incidentResolvedAtStep < 0 && incidentArmedAtStep >= 0
                && simulationStep >= incidentArmedAtStep + incidentDelaySteps;
    }

    public boolean resolveIncident(String outcome, long simulationStep) {
        Objects.requireNonNull(outcome, "outcome");
        if (outcome.isBlank()) throw new IllegalArgumentException("Blank incident outcome");
        if (incidentArmedAtStep < 0 || simulationStep < incidentArmedAtStep) {
            throw new IllegalArgumentException("Incident resolution precedes arming");
        }
        if (incidentResolvedAtStep >= 0) return false;
        incidentOutcome = outcome;
        incidentResolvedAtStep = simulationStep;
        return true;
    }
}
