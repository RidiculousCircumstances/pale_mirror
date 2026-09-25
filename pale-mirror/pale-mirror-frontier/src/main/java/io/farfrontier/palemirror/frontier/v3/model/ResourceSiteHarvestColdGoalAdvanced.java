package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One scheduled known-support movement of the same farmer toward a semantic work or depot goal. */
public record ResourceSiteHarvestColdGoalAdvanced(SubjectId jobId, SubjectId workerId, long layoutRevision,
                                                  int nextWorkSlot, ResourceSiteHarvestGoal.Kind kind,
                                                  BodyPosition nextBody, ScheduleId coldScheduleId,
                                                  long coldDueAt) implements FrontierPayload {
    public ResourceSiteHarvestColdGoalAdvanced {
        Objects.requireNonNull(jobId, "cold field goal job");
        Objects.requireNonNull(workerId, "cold field goal worker");
        Objects.requireNonNull(kind, "cold field goal kind");
        Objects.requireNonNull(nextBody, "cold field goal next body");
        Objects.requireNonNull(coldScheduleId, "cold field goal schedule");
        if (layoutRevision < 1 || nextWorkSlot < 0 || coldDueAt < 0)
            throw new IllegalArgumentException("cold field goal needs a current layout, work slot and due action");
    }

    @Override public String type() { return "frontier.resource_site_harvest_cold_goal_advanced"; }
}
