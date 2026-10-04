package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Objects;

/** Captured production predecessor, not another worker position or progress owner. */
public record ProductionWorkObservation(ActorHotObservation authority, TraversalTopologyId topologyId,
                                        long topologyRevision, int cursor, ProductionWorkProgress progress, long spatialRevision) {
    public ProductionWorkObservation {
        Objects.requireNonNull(authority, "production physical authority");
        Objects.requireNonNull(topologyId, "production observed topology");
        Objects.requireNonNull(progress, "production observed predecessor");
        if (authority.actuation().execution().activityKind() != ActorActivityKind.PRODUCTION
                || topologyRevision < 0 || cursor < 0 || spatialRevision < 1)
            throw new IllegalArgumentException("production observation has foreign kind or invalid predecessor");
    }
    public void requireOwner(SubjectId jobId) {
        if (!authority.actuation().execution().activityOwnerId().equals(jobId))
            throw new IllegalArgumentException("production observation has foreign job");
    }
    public void require(FrontierWorldState state, ProductionJob job, SceneLease lease, BodyPosition observed) {
        requireOwner(job.id());
        if (!authority.actuation().execution().actorId().equals(job.workerId())
                || !topologyId.equals(job.workTraversal().id()) || topologyRevision != job.workTraversal().revision()
                || cursor != job.traversalCursor() || !progress.equals(job.workProgress())
                || spatialRevision != job.spatial().revision() || job.bakeryWork().isPresent())
            throw new IllegalArgumentException("production observation has stale worker, route, cursor or stage");
        authority.require(state, authority.actuation().execution(), lease.revision(), observed);
    }
}
