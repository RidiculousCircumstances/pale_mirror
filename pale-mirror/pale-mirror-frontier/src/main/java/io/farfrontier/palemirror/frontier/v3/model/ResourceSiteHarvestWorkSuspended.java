package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
import java.util.Optional;

/** Settles only the retained interval; cancellation of sowing or a player edit cannot forbid stopping it. */
public record ResourceSiteHarvestWorkSuspended(SubjectId siteId, SubjectId jobId, SubjectId workerId,
        long epoch, long layoutRevision, ResourceFieldLayout.CellId cellId, long atTick,
        Optional<WorkProgress> previous, WorkProgress next, ScheduleId scheduleId, long dueAt,
        Optional<SceneLeaseId> hotLeaseId) implements ResourceSiteHarvestLabourChange {
    public ResourceSiteHarvestWorkSuspended {
        Objects.requireNonNull(siteId); Objects.requireNonNull(jobId); Objects.requireNonNull(workerId);
        Objects.requireNonNull(cellId); Objects.requireNonNull(previous); Objects.requireNonNull(next);
        Objects.requireNonNull(scheduleId); Objects.requireNonNull(hotLeaseId);
        if (epoch < 1 || layoutRevision < 1 || atTick < 0 || dueAt < 0
                || previous.filter(WorkProgress::running).isEmpty() || next.running()
                || !next.equals(previous.orElseThrow().pause(atTick)))
            throw new IllegalArgumentException("invalid retained labour suspension");
    }
    @Override public String type() { return "frontier.resource_site_harvest_work_suspended"; }
}
