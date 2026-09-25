package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** COLD completion of an already returned exact field worker, without a fabricated travel edge. */
public record ResourceSiteHarvestReturned(SubjectId jobId, SubjectId workerId,
                                          ScheduleId coldScheduleId, long coldDueAt) implements FrontierPayload {
    public ResourceSiteHarvestReturned {
        jobId = Objects.requireNonNull(jobId, "returned harvest job");
        workerId = Objects.requireNonNull(workerId, "returned harvest worker");
        coldScheduleId = Objects.requireNonNull(coldScheduleId, "returned harvest schedule");
        if (!jobId.value().startsWith("job:site-harvest-") || !workerId.value().startsWith("resident:") || coldDueAt < 0L)
            throw new IllegalArgumentException("returned harvest identity or due turn is invalid");
    }

    @Override public String type() { return "frontier.resource_site_harvest_returned"; }
}
