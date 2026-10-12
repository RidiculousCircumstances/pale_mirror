package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult;

import java.util.Objects;

/** A producer's terminal calculation refusal; it is neither proof of global impossibility nor arrival. */
public record ResourceSiteHarvestColdGoalHeld(SubjectId siteId, SubjectId jobId, SubjectId workerId,
                                              long layoutRevision, int nextWorkSlot,
                                              ResourceSiteHarvestGoal.Kind kind, SurfaceAnchor target,
                                              ResourceSiteHarvestNavigationBlock.Reason reason,
                                              ScheduleId coldScheduleId, long coldDueAt,
                                              long goalRevision, SurfaceAnchor origin, ActorExecutionId executionId,
                                              PedestrianRouteResult.Status routeStatus, String routeDetail) implements FrontierPayload {
    public ResourceSiteHarvestColdGoalHeld {
        Objects.requireNonNull(siteId, "held field site");
        Objects.requireNonNull(jobId, "held field job");
        Objects.requireNonNull(workerId, "held field worker");
        Objects.requireNonNull(kind, "held field goal kind");
        Objects.requireNonNull(target, "held field goal target");
        Objects.requireNonNull(reason, "held field goal reason");
        Objects.requireNonNull(coldScheduleId, "held field continuation");
        Objects.requireNonNull(origin, "held field departure");
        Objects.requireNonNull(executionId, "held field execution");
        Objects.requireNonNull(routeStatus, "held field calculation outcome");
        Objects.requireNonNull(routeDetail, "held field calculation detail");
        if (layoutRevision < 1 || nextWorkSlot < 0 || coldDueAt < 0 || goalRevision < 1
                || reason != ResourceSiteHarvestNavigationBlock.Reason.KNOWN_GEOMETRY_UNAVAILABLE
                || routeStatus == PedestrianRouteResult.Status.PLANNING || routeStatus == PedestrianRouteResult.Status.FOUND
                || routeDetail.isBlank() || routeDetail.length() > 1024
                || !executionId.actorId().equals(workerId) || !executionId.activityOwnerId().equals(jobId)
                || executionId.activityKind() != ActorActivityKind.FIELD_HARVEST)
            throw new IllegalArgumentException("held COLD field goal has invalid revision, slot or due instant");
    }

    @Override public String type() { return "frontier.resource_site_harvest_cold_goal_held"; }
}
