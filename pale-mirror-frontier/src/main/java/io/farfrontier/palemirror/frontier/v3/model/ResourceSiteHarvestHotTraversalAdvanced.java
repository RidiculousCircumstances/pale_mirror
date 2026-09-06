package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * One observed HOT checkpoint of the exact farmer on the next retained harvest station.
 *
 * <p>The lease ID, actor ID, observed body and next cursor are one causal unit.  A reducer may
 * accept it only from that currently HOT lease and updates the job cursor, lease recovery body
 * and canonical actor body in one state update.</p>
 */
public record ResourceSiteHarvestHotTraversalAdvanced(SubjectId jobId, SceneLeaseId leaseId, SubjectId workerId,
                                                       BodyPosition observedWorker, int nextCursor) implements FrontierPayload {
    public ResourceSiteHarvestHotTraversalAdvanced {
        jobId = Objects.requireNonNull(jobId, "field-work HOT traversal job");
        leaseId = Objects.requireNonNull(leaseId, "field-work HOT traversal lease");
        workerId = Objects.requireNonNull(workerId, "field-work HOT traversal worker");
        observedWorker = Objects.requireNonNull(observedWorker, "field-work HOT observed worker");
        if (!jobId.value().startsWith("job:site-harvest-") || !leaseId.value().startsWith("lease:")
                || !workerId.value().startsWith("resident:") || nextCursor < 1 || nextCursor >= TraversalTopology.MAX_NODES) {
            throw new IllegalArgumentException("resource-site HOT traversal identity or cursor is invalid");
        }
    }

    @Override public String type() { return "frontier.resource_site_harvest_hot_traversal_advanced"; }
}
