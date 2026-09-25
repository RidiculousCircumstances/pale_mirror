package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;

import java.util.Objects;

/**
 * One COLD semantic edge of a retained harvest topology.
 *
 * <p>This fact deliberately contains no physical lease or body observation.  It is emitted only
 * by the job's stable COLD schedule while no harvest scene holds physical authority; the reducer
 * proves the same retained farmer and cursor from canonical state.</p>
 */
public record ResourceSiteHarvestColdTraversalAdvanced(SubjectId jobId, SubjectId workerId, int nextCursor,
                                                       ScheduleId coldScheduleId, long coldDueAt) implements FrontierPayload {
    public ResourceSiteHarvestColdTraversalAdvanced {
        jobId = Objects.requireNonNull(jobId, "field-work cold traversal job");
        workerId = Objects.requireNonNull(workerId, "field-work cold traversal worker");
        coldScheduleId = Objects.requireNonNull(coldScheduleId, "field-work cold schedule");
        if (!jobId.value().startsWith("job:site-harvest-") || !workerId.value().startsWith("resident:")
                || nextCursor < 1 || nextCursor >= TraversalTopology.MAX_NODES || coldDueAt < -1L) {
            throw new IllegalArgumentException("resource-site COLD traversal identity or cursor is invalid");
        }
    }

    /** Isolated nonterminal fixtures may omit a real engine schedule. */
    public ResourceSiteHarvestColdTraversalAdvanced(SubjectId jobId, SubjectId workerId, int nextCursor) {
        this(jobId, workerId, nextCursor, new ScheduleId("schedule:resource-site-harvest-legacy-unbound"), -1L);
    }

    @Override public String type() { return "frontier.resource_site_harvest_cold_traversal_advanced"; }
}
