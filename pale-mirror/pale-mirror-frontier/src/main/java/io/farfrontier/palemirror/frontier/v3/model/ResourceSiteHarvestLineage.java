package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;

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
                                         BodyPosition terminalBody, PhysicalIntentId predecessorIntentId, InventoryCustody.ContainerSlot outputSlot,
                                         boolean outputReceiptResolved, Optional<SubjectId> successorTaskId, Optional<SubjectId> successorJobId) {
    public ResourceSiteHarvestLineage {
        Objects.requireNonNull(predecessorJobId, "harvest lineage predecessor job");
        Objects.requireNonNull(predecessorTaskId, "harvest lineage predecessor task");
        Objects.requireNonNull(workerId, "harvest lineage worker");
        Objects.requireNonNull(outputItemId, "harvest lineage output");
        Objects.requireNonNull(terminalBody, "harvest lineage terminal body");
        Objects.requireNonNull(predecessorIntentId, "harvest lineage intent"); Objects.requireNonNull(outputSlot, "harvest lineage output slot");
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
        return completed(job, growthEpoch, true);
    }

    public static ResourceSiteHarvestLineage completed(ResourceSiteHarvestJob job, long growthEpoch, boolean outputReceiptResolved) {
        return new ResourceSiteHarvestLineage(job.id(), job.taskId(), job.workerId(), job.outputItemId(), growthEpoch,
                job.traversal().linearCorridorSurfaces().getLast().standingBody(), job.intentId(), job.outputSlot(), outputReceiptResolved, Optional.empty(), Optional.empty());
    }

    public ResourceSiteHarvestLineage bindSuccessor(ResourceSiteHarvestJob job) {
        Objects.requireNonNull(job, "harvest lineage successor job");
        if (successorTaskId.isPresent() || !workerId.equals(job.workerId())) {
            throw new IllegalArgumentException("resource-site successor must retain its one completed farmer");
        }
        return new ResourceSiteHarvestLineage(predecessorJobId, predecessorTaskId, workerId, outputItemId, completedGrowthEpoch,
                terminalBody, predecessorIntentId, outputSlot, outputReceiptResolved, Optional.of(job.taskId()), Optional.of(job.id()));
    }

    /** A physical receipt is open only while an exact current intent can still resolve it. */
    public boolean receiptPending() { return !outputReceiptResolved; }

    /**
     * The old field write may remain RUNNING after its exact wheat has entered a later COLD
     * custody step.  The successor is not inferred from a missing stack: it is the active
     * COLD job or retained terminal receipt that names this exact input and its result.  That
     * durable relationship lets a natural replica observation converge to the current depot
     * contents instead of replaying wheat or inventing a conflict.
     */
    public boolean composedIntoCanonicalSuccessor(FrontierWorldState state) {
        Objects.requireNonNull(state, "harvest successor state");
        boolean activeCold = state.productionJobs().values().stream().anyMatch(job -> outputItemId.equals(job.consumedItemId())
                && job.inputHold() instanceof ProductionInputHold.Cold);
        boolean terminalExact = state.companies().market().workOrders().values().stream()
                .flatMap(order -> order.terminalReceipt().stream())
                .anyMatch(receipt -> receipt.inputRepresentation() == TerminalProductionReceipt.ResourceRepresentation.EXACT_ITEM
                        && receipt.outputRepresentation() == TerminalProductionReceipt.ResourceRepresentation.EXACT_ITEM
                        && outputItemId.equals(receipt.inputId()));
        return activeCold || terminalExact;
    }
    public ResourceSiteHarvestLineage resolveReceipt() {
        if (outputReceiptResolved) throw new IllegalStateException("resource-site harvest receipt is already resolved");
        return new ResourceSiteHarvestLineage(predecessorJobId, predecessorTaskId, workerId, outputItemId, completedGrowthEpoch,
                terminalBody, predecessorIntentId, outputSlot, true, successorTaskId, successorJobId);
    }
}
