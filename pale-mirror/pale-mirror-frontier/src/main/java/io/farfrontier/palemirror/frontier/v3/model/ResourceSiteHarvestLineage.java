package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/**
 * The one retained predecessor/successor relationship for a renewable field.
 *
 * <p>The resource-site lifecycle owns this small history.  It is deliberately
 * not an assignment cache: the completed job already owns the original worker
 * and output, while this record prevents the next epoch from discovering a
 * different farmer merely because several are currently eligible.</p>
 */
public record ResourceSiteHarvestLineage(SubjectId predecessorJobId, SubjectId predecessorTaskId,
                                         SubjectId workerId, SubjectId outputItemId, long completedGrowthEpoch,
                                         Optional<SubjectId> successorTaskId, Optional<SubjectId> successorJobId) {
    public ResourceSiteHarvestLineage {
        Objects.requireNonNull(predecessorJobId, "harvest lineage predecessor job");
        Objects.requireNonNull(predecessorTaskId, "harvest lineage predecessor task");
        Objects.requireNonNull(workerId, "harvest lineage worker");
        Objects.requireNonNull(outputItemId, "harvest lineage output");
        successorTaskId = Optional.ofNullable(successorTaskId).orElse(Optional.empty());
        successorJobId = Optional.ofNullable(successorJobId).orElse(Optional.empty());
        if (completedGrowthEpoch < 1L || !predecessorJobId.value().startsWith("job:site-harvest-")
                || !predecessorTaskId.value().startsWith("task:") || !workerId.value().startsWith("resident:")
                || !outputItemId.value().startsWith("item:site-harvest-")) {
            throw new IllegalArgumentException("resource-site harvest lineage has invalid canonical identities");
        }
        if (successorTaskId.isEmpty() != successorJobId.isEmpty()) {
            throw new IllegalArgumentException("resource-site successor task and job must be retained together");
        }
    }

    public static ResourceSiteHarvestLineage completed(ResourceSiteHarvestJob job, long growthEpoch) {
        return new ResourceSiteHarvestLineage(job.id(), job.taskId(), job.workerId(), job.outputItemId(), growthEpoch,
                Optional.empty(), Optional.empty());
    }

    public ResourceSiteHarvestLineage bindSuccessor(ResourceSiteHarvestJob job) {
        Objects.requireNonNull(job, "harvest lineage successor job");
        if (successorTaskId.isPresent() || !workerId.equals(job.workerId())) {
            throw new IllegalArgumentException("resource-site successor must retain its one completed farmer");
        }
        return new ResourceSiteHarvestLineage(predecessorJobId, predecessorTaskId, workerId, outputItemId, completedGrowthEpoch,
                Optional.of(job.taskId()), Optional.of(job.id()));
    }
}
