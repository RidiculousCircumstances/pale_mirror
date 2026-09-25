package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Replaces one exhausted work route with the next bounded segment without moving its worker. */
public record ResourceSiteHarvestSegmentRenewed(SubjectId siteId, SubjectId jobId, SubjectId workerId,
                                                int nextCropSlot, ScheduleId coldScheduleId, long coldDueAt,
                                                Optional<SceneLeaseId> hotLeaseId) implements FrontierPayload {
    public ResourceSiteHarvestSegmentRenewed {
        Objects.requireNonNull(siteId, "field segment site"); Objects.requireNonNull(jobId, "field segment job");
        Objects.requireNonNull(workerId, "field segment worker"); Objects.requireNonNull(coldScheduleId, "field segment schedule");
        hotLeaseId = Objects.requireNonNull(hotLeaseId, "field segment physical owner");
        if (!siteId.value().startsWith("site:") || !jobId.value().startsWith("job:site-harvest-")
                || !workerId.value().startsWith("resident:") || nextCropSlot < 1 || coldDueAt < 0)
            throw new IllegalArgumentException("field segment renewal has invalid declared identities");
    }

    @Override public String type() { return "frontier.resource_site_harvest_segment_renewed"; }
}
