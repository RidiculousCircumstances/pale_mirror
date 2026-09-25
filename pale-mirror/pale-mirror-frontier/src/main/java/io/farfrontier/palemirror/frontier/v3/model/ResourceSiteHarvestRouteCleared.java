package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** HOT observation that this exact local block was invalidated before movement resumed. */
public record ResourceSiteHarvestRouteCleared(SubjectId siteId, SubjectId jobId, SubjectId workerId,
                                              ResourceSiteHarvestNavigationBlock expected, SceneLeaseId leaseId,
                                              ScheduleId coldScheduleId, long coldDueAt) implements FrontierPayload {
    public ResourceSiteHarvestRouteCleared {
        Objects.requireNonNull(siteId, "cleared route site"); Objects.requireNonNull(jobId, "cleared route job");
        Objects.requireNonNull(workerId, "cleared route worker"); Objects.requireNonNull(expected, "cleared route block");
        Objects.requireNonNull(leaseId, "cleared route HOT owner");
        Objects.requireNonNull(coldScheduleId, "cleared route continuation");
        if (!siteId.value().startsWith("site:") || !jobId.value().startsWith("job:site-harvest-")
                || !workerId.value().startsWith("resident:") || !leaseId.value().startsWith("lease:")
                || coldDueAt < 0) throw new IllegalArgumentException("cleared route has invalid identity or due instant");
    }
    @Override public String type() { return "frontier.resource_site_harvest_route_cleared"; }
}
