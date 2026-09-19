package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;

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
                                         boolean outputReceiptResolved, Optional<SubjectId> successorTaskId, Optional<SubjectId> successorJobId,
                                         ResourceSiteHarvestCausality causality) {
    public ResourceSiteHarvestLineage {
        Objects.requireNonNull(predecessorJobId, "harvest lineage predecessor job");
        Objects.requireNonNull(predecessorTaskId, "harvest lineage predecessor task");
        Objects.requireNonNull(workerId, "harvest lineage worker");
        Objects.requireNonNull(outputItemId, "harvest lineage output");
        Objects.requireNonNull(terminalBody, "harvest lineage terminal body");
        Objects.requireNonNull(predecessorIntentId, "harvest lineage intent"); Objects.requireNonNull(outputSlot, "harvest lineage output slot");
        causality = Objects.requireNonNull(causality, "harvest lineage causality");
        if (!predecessorIntentId.equals(causality.intentId())) throw new IllegalArgumentException("harvest lineage causality has a foreign intent");
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

    /** Compatibility constructor for older in-memory fixtures; persisted current-schema records always carry causality. */
    public ResourceSiteHarvestLineage(SubjectId predecessorJobId, SubjectId predecessorTaskId, SubjectId workerId, SubjectId outputItemId,
                                      long completedGrowthEpoch, BodyPosition terminalBody, PhysicalIntentId predecessorIntentId,
                                      InventoryCustody.ContainerSlot outputSlot, boolean outputReceiptResolved,
                                      Optional<SubjectId> successorTaskId, Optional<SubjectId> successorJobId) {
        this(predecessorJobId, predecessorTaskId, workerId, outputItemId, completedGrowthEpoch, terminalBody, predecessorIntentId,
                outputSlot, outputReceiptResolved, successorTaskId, successorJobId,
                ResourceSiteHarvestCausality.notCaptured(predecessorIntentId, outputItemId, outputSlot));
    }

    public static ResourceSiteHarvestLineage completed(ResourceSiteHarvestJob job, long growthEpoch) {
        return completed(job, growthEpoch, true);
    }

    public static ResourceSiteHarvestLineage completed(ResourceSiteHarvestJob job, long growthEpoch, boolean outputReceiptResolved) {
        return completed(job, growthEpoch, outputReceiptResolved, ResourceSiteHarvestCausality.notCaptured(job));
    }

    public static ResourceSiteHarvestLineage completed(ResourceSiteHarvestJob job, long growthEpoch, boolean outputReceiptResolved,
                                                        ResourceSiteHarvestCausality causality) {
        return new ResourceSiteHarvestLineage(job.id(), job.taskId(), job.workerId(), job.outputItemId(), growthEpoch,
                job.traversal().linearCorridorSurfaces().getLast().standingBody(), job.intentId(), job.outputSlot(), outputReceiptResolved,
                Optional.empty(), Optional.empty(), causality);
    }

    public ResourceSiteHarvestLineage bindSuccessor(ResourceSiteHarvestJob job) {
        Objects.requireNonNull(job, "harvest lineage successor job");
        if (successorTaskId.isPresent() || !workerId.equals(job.workerId())) {
            throw new IllegalArgumentException("resource-site successor must retain its one completed farmer");
        }
        return new ResourceSiteHarvestLineage(predecessorJobId, predecessorTaskId, workerId, outputItemId, completedGrowthEpoch,
                terminalBody, predecessorIntentId, outputSlot, outputReceiptResolved, Optional.of(job.taskId()), Optional.of(job.id()), causality);
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
        return resolveReceipt(null);
    }
    /** The late physical observation is retained by its exact producer id, never summarized from a status. */
    public ResourceSiteHarvestLineage resolveReceipt(PhysicalObservationId observationId) {
        if (outputReceiptResolved) throw new IllegalStateException("resource-site harvest receipt is already resolved");
        String observation = observationId == null ? "confirmed:" + predecessorIntentId.value() : "confirmed:" + observationId.value();
        return new ResourceSiteHarvestLineage(predecessorJobId, predecessorTaskId, workerId, outputItemId, completedGrowthEpoch,
                terminalBody, predecessorIntentId, outputSlot, true, successorTaskId, successorJobId,
                causality.confirmed(observation));
    }
    public ResourceSiteHarvestLineage withTrace(RetainedDiagnosticTrace trace) {
        return new ResourceSiteHarvestLineage(predecessorJobId, predecessorTaskId, workerId, outputItemId, completedGrowthEpoch,
                terminalBody, predecessorIntentId, outputSlot, outputReceiptResolved, successorTaskId, successorJobId, causality.withTrace(trace));
    }
}
