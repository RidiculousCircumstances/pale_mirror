package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
import java.util.Optional;

/** Exact worker/cell-bound labour interval change; never a crop or arrival receipt. */
public record ResourceSiteHarvestWorkChanged(SubjectId siteId, SubjectId jobId, SubjectId workerId,
        long epoch, long layoutRevision, ResourceFieldLayout.CellId cellId, WorkOperation operation,
        long atTick, Optional<WorkProgress> previous, WorkProgress next,
        ScheduleId scheduleId, long dueAt, Optional<SceneLeaseId> hotLeaseId) implements FrontierPayload {
    public ResourceSiteHarvestWorkChanged {
        Objects.requireNonNull(siteId); Objects.requireNonNull(jobId); Objects.requireNonNull(workerId);
        Objects.requireNonNull(cellId); Objects.requireNonNull(operation); Objects.requireNonNull(previous);
        Objects.requireNonNull(next); Objects.requireNonNull(scheduleId); Objects.requireNonNull(hotLeaseId);
        if (epoch < 1 || layoutRevision < 1 || atTick < 0 || dueAt < 0 || next.evaluatedAtTick() != atTick
                || previous.equals(Optional.of(next))) throw new IllegalArgumentException("invalid labour transition");
    }
    @Override public String type() { return "frontier.resource_site_harvest_work_changed"; }
}
