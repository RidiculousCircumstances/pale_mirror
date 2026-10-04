package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One physically observed semantic arrival; no intermediate path or crop effect is claimed. */
public record ResourceSiteHarvestHotGoalArrived(SubjectId jobId, SceneLeaseId leaseId,
                                                SubjectId workerId, long layoutRevision,
                                                int nextWorkSlot, ResourceSiteHarvestGoal.Kind kind,
                                                BodyPosition observedWorker, long targetGeneration,
                                                io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation observation) implements FrontierPayload {
    public ResourceSiteHarvestHotGoalArrived {
        Objects.requireNonNull(jobId, "field goal arrival job");
        Objects.requireNonNull(leaseId, "field goal arrival lease");
        Objects.requireNonNull(workerId, "field goal arrival worker");
        Objects.requireNonNull(kind, "field goal arrival kind");
        Objects.requireNonNull(observedWorker, "field goal arrival body");
        Objects.requireNonNull(observation, "captured field arrival authority");
        if (!workerId.equals(observation.actuation().body().actorId()))
            throw new IllegalArgumentException("field goal arrival has a foreign body witness");
        if (layoutRevision < 1 || nextWorkSlot < 0 || targetGeneration < 0)
            throw new IllegalArgumentException("field goal arrival has invalid layout or work slot");
    }

    @Override public String type() { return "frontier.resource_site_harvest_hot_goal_arrived"; }
}
