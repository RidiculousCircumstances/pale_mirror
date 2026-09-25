package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** COLD cannot certify a route from retained knowledge; the exact goal waits for HOT evidence. */
public record ResourceSiteHarvestColdGoalHeld(SubjectId siteId, SubjectId jobId, SubjectId workerId,
                                              long layoutRevision, int nextWorkSlot,
                                              ResourceSiteHarvestGoal.Kind kind, SurfaceAnchor target,
                                              ResourceSiteHarvestNavigationBlock.Reason reason,
                                              ScheduleId coldScheduleId, long coldDueAt) implements FrontierPayload {
    public ResourceSiteHarvestColdGoalHeld {
        Objects.requireNonNull(siteId, "held field site");
        Objects.requireNonNull(jobId, "held field job");
        Objects.requireNonNull(workerId, "held field worker");
        Objects.requireNonNull(kind, "held field goal kind");
        Objects.requireNonNull(target, "held field goal target");
        Objects.requireNonNull(reason, "held field goal reason");
        Objects.requireNonNull(coldScheduleId, "held field continuation");
        if (layoutRevision < 1 || nextWorkSlot < 0 || coldDueAt < 0
                || reason != ResourceSiteHarvestNavigationBlock.Reason.KNOWN_GEOMETRY_UNAVAILABLE
                    && reason != ResourceSiteHarvestNavigationBlock.Reason.CONTINUATION_UNAVAILABLE)
            throw new IllegalArgumentException("held COLD field goal has invalid revision, slot or due instant");
    }

    @Override public String type() { return "frontier.resource_site_harvest_cold_goal_held"; }
}
