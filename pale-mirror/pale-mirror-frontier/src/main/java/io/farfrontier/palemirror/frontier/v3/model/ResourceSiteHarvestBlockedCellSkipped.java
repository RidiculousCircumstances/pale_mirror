package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;
import java.util.List;

/** One selected unavailable CellId is accounted without crop effect or worker displacement. */
public record ResourceSiteHarvestBlockedCellSkipped(SubjectId siteId, SubjectId jobId, SubjectId workerId,
                                                    long layoutRevision, List<ResourceFieldLayout.CellId> cellIds,
                                                    ScheduleId coldScheduleId,
                                                    long coldDueAt, Optional<SceneLeaseId> hotLeaseId)
        implements FrontierPayload {
    public ResourceSiteHarvestBlockedCellSkipped {
        Objects.requireNonNull(siteId, "blocked field site");
        Objects.requireNonNull(jobId, "blocked field job");
        Objects.requireNonNull(workerId, "blocked field worker");
        cellIds = List.copyOf(Objects.requireNonNull(cellIds, "blocked field cells"));
        Objects.requireNonNull(coldScheduleId, "blocked field continuation");
        hotLeaseId = Objects.requireNonNull(hotLeaseId, "blocked field physical owner");
        if (!siteId.value().startsWith("site:") || !jobId.value().startsWith("job:site-harvest-")
                || !workerId.value().startsWith("resident:") || layoutRevision < 1
                || cellIds.size() != 1
                || coldDueAt < 0)
            throw new IllegalArgumentException("blocked field cell has invalid declared identity or due instant");
    }

    @Override public String type() { return "frontier.resource_site_harvest_blocked_cell_skipped"; }
}
