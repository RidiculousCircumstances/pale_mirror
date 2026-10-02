package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

/** Field-owner identity and wake policy; neither the modifier producer nor shared coordinator chooses a family. */
public final class ResourceSiteHarvestContinuation {
    public static final String KIND = "frontier.resource_site.harvest.cold_progress";
    private ResourceSiteHarvestContinuation() { }
    public static ScheduledAction at(ResourceSiteHarvestJob job, long tick) {
        return new ScheduledAction(new ScheduleId("schedule:resource-site-harvest-cold-progress-"
                + job.id().value().substring("job:".length())), new SimInstant(tick), 0, job.siteId(), KIND, 1);
    }
}
