package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact owned-body checkpoint at a HOT interruption or shared-service boundary exit. */
public record ResourceSiteHarvestHotTransitObserved(SubjectId jobId, SceneLeaseId leaseId,
                                                    SubjectId workerId, long layoutRevision,
                                                    int nextWorkSlot, ResourceSiteHarvestGoal.Kind kind,
                                                    BodyPosition observedWorker) implements FrontierPayload {
    public ResourceSiteHarvestHotTransitObserved {
        Objects.requireNonNull(jobId, "interrupted field goal job");
        Objects.requireNonNull(leaseId, "interrupted field goal lease");
        Objects.requireNonNull(workerId, "interrupted field goal worker");
        Objects.requireNonNull(kind, "interrupted field goal kind");
        Objects.requireNonNull(observedWorker, "interrupted field goal body");
        if (layoutRevision < 1 || nextWorkSlot < 0)
            throw new IllegalArgumentException("interrupted field goal has invalid layout or work slot");
    }

    @Override public String type() { return "frontier.resource_site_harvest_hot_transit_observed"; }
}
