package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Optional;

/** Exact clock transition. Stopping existing labour does not authorize a new cell operation. */
public sealed interface ResourceSiteHarvestLabourChange extends FrontierPayload
        permits ResourceSiteHarvestWorkChanged, ResourceSiteHarvestWorkSuspended {
    SubjectId siteId();
    SubjectId jobId();
    SubjectId workerId();
    long epoch();
    long layoutRevision();
    ResourceFieldLayout.CellId cellId();
    long atTick();
    Optional<WorkProgress> previous();
    WorkProgress next();
    ScheduleId scheduleId();
    long dueAt();
    Optional<SceneLeaseId> hotLeaseId();
}
