package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** An unreachable work target stays pending while the farmer takes another reachable target. */
public record ResourceSiteHarvestTargetRetargeted(SubjectId siteId, SubjectId jobId, SubjectId workerId,
                                                   long layoutRevision, int fromSlot, int toSlot,
                                                   ScheduleId coldScheduleId, long coldDueAt,
                                                   Optional<SceneLeaseId> hotLeaseId) implements FrontierPayload {
    public ResourceSiteHarvestTargetRetargeted {
        Objects.requireNonNull(siteId, "retarget site");
        Objects.requireNonNull(jobId, "retarget job");
        Objects.requireNonNull(workerId, "retarget worker");
        Objects.requireNonNull(coldScheduleId, "retarget continuation");
        hotLeaseId = Objects.requireNonNull(hotLeaseId, "retarget physical owner");
        if (!siteId.value().startsWith("site:") || !jobId.value().startsWith("job:site-harvest-")
                || !workerId.value().startsWith("resident:") || layoutRevision < 0
                || fromSlot < 0 || toSlot < 0 || fromSlot == toSlot || coldDueAt < 0)
            throw new IllegalArgumentException("retarget has invalid identity or target");
    }

    @Override public String type() { return "frontier.resource_site_harvest_target_retargeted"; }
}
