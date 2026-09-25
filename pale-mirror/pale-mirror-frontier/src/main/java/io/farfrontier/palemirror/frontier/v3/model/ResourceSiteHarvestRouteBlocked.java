package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** HOT observation that the named farmer cannot reach only its current retained movement goal. */
public record ResourceSiteHarvestRouteBlocked(SubjectId siteId, SubjectId jobId, SubjectId workerId,
                                              ResourceSiteHarvestNavigationBlock block, SceneLeaseId leaseId,
                                              ScheduleId coldScheduleId, long coldDueAt) implements FrontierPayload {
    public ResourceSiteHarvestRouteBlocked {
        Objects.requireNonNull(siteId, "blocked route site"); Objects.requireNonNull(jobId, "blocked route job");
        Objects.requireNonNull(workerId, "blocked route worker"); Objects.requireNonNull(block, "blocked route goal");
        Objects.requireNonNull(leaseId, "blocked route HOT owner");
        Objects.requireNonNull(coldScheduleId, "blocked route continuation");
        if (!siteId.value().startsWith("site:") || !jobId.value().startsWith("job:site-harvest-")
                || !workerId.value().startsWith("resident:") || !leaseId.value().startsWith("lease:")
                || coldDueAt < 0) throw new IllegalArgumentException("blocked route has invalid identity or due instant");
    }
    @Override public String type() { return "frontier.resource_site_harvest_route_blocked"; }
}
