package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/**
 * Compact, immutable terminal description retained by the accepted order after
 * its live job is removed.  It is historical evidence owned by that order,
 * not a second production or provision authority.
 */
public record TerminalProductionReceipt(SubjectId jobId, SubjectId workerId,
                                        SubjectId inputId, SubjectId outputId,
                                        ResourceRepresentation inputRepresentation, ResourceRepresentation outputRepresentation,
                                        String outputKind, int outputCount, TraversalTopologyId topologyId,
                                        long topologyRevision, int traversalCursor, BodyPosition terminalBody) {
    public enum ResourceRepresentation { EXACT_ITEM, RESOURCE_LOT }
    public TerminalProductionReceipt {
        Objects.requireNonNull(jobId, "terminal production job");
        Objects.requireNonNull(workerId, "terminal production worker");
        Objects.requireNonNull(inputId, "terminal production input");
        Objects.requireNonNull(outputId, "terminal production output");
        Objects.requireNonNull(inputRepresentation, "terminal production input representation"); Objects.requireNonNull(outputRepresentation, "terminal production output representation");
        Objects.requireNonNull(outputKind, "terminal production output kind");
        topologyId = Objects.requireNonNull(topologyId, "terminal production topology");
        terminalBody = Objects.requireNonNull(terminalBody, "terminal production body");
        if (outputKind.isBlank() || outputCount < 1 || topologyRevision < 0L || traversalCursor < 0) throw new IllegalArgumentException("terminal production receipt is invalid");
    }
    public static TerminalProductionReceipt of(ProductionJob job) {
        Objects.requireNonNull(job, "completed production job");
        ResourceRepresentation representation = job.inputHold() instanceof ProductionInputHold.FungibleCold ? ResourceRepresentation.RESOURCE_LOT : ResourceRepresentation.EXACT_ITEM;
        return new TerminalProductionReceipt(job.id(), job.workerId(), job.consumedItemId(), job.outputItemId(), representation, representation, job.outputItemKind(), job.outputCount(),
                job.workTraversal().id(), job.workTraversal().revision(), job.traversalCursor(), job.workTraversal().linearCorridorSurfaces().get(job.traversalCursor()).standingBody());
    }
}
