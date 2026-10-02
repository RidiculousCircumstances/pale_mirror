package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.List;
import java.util.Optional;

/** No-effect exclusion from one retained area task, not physical work or actor arrival. */
public sealed interface ResourceSiteHarvestCellSkip extends FrontierPayload
        permits ResourceSiteHarvestBlockedCellSkipped, ResourceSiteHarvestImmatureCellSkipped {
    SubjectId siteId();
    SubjectId jobId();
    SubjectId workerId();
    long layoutRevision();
    List<ResourceFieldLayout.CellId> cellIds();
    ScheduleId coldScheduleId();
    long coldDueAt();
    Optional<SceneLeaseId> hotLeaseId();
    ResourceFieldCycle.WorkOutcome outcome();
}
