package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;
import java.util.List;

/** One selected unavailable CellId is accounted without crop effect or worker displacement. */
public record ResourceSiteHarvestImmatureCellSkipped(SubjectId siteId, SubjectId jobId, SubjectId workerId,
                                                    long layoutRevision, List<ResourceFieldLayout.CellId> cellIds,
                                                    ScheduleId coldScheduleId,
                                                    long coldDueAt, Optional<SceneLeaseId> hotLeaseId)
        implements ResourceSiteHarvestCellSkip {
    public ResourceSiteHarvestImmatureCellSkipped {
        Objects.requireNonNull(siteId, "immature field site");
        Objects.requireNonNull(jobId, "immature field job");
        Objects.requireNonNull(workerId, "immature field worker");
        cellIds = List.copyOf(Objects.requireNonNull(cellIds, "immature field cells"));
        Objects.requireNonNull(coldScheduleId, "immature field continuation");
        hotLeaseId = Objects.requireNonNull(hotLeaseId, "immature field physical owner");
        if (!siteId.value().startsWith("site:") || !jobId.value().startsWith("job:site-harvest-")
                || !workerId.value().startsWith("resident:") || layoutRevision < 1
                || cellIds.size() != 1
                || coldDueAt < 0)
            throw new IllegalArgumentException("immature field cell has invalid declared identity or due instant");
    }

    @Override public String type() { return "frontier.resource_site_harvest_immature_cell_skipped"; }
    @Override public ResourceFieldCycle.WorkOutcome outcome() { return ResourceFieldCycle.WorkOutcome.SKIPPED_IMMATURE; }
}
