package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * A durable facility job that owns one exact input until its output is confirmed or work is
 * explicitly cancelled. COLD holds the stack itself; HOT retains its physical depot stack.
 */
public record ProductionJob(
        SubjectId id,
        SubjectId settlementId,
        SubjectId facilityId,
        SubjectId workerId,
        SubjectId consumedItemId,
        ProductionInputHold inputHold,
        SubjectId outputItemId,
        String outputItemKind,
        int outputCount,
        ProductionWorkProgress workProgress,
        TraversalTopology workTraversal,
        int traversalCursor
) {
    public ProductionJob {
        Objects.requireNonNull(id, "production job id");
        Objects.requireNonNull(settlementId, "settlement id");
        Objects.requireNonNull(facilityId, "facility id");
        Objects.requireNonNull(workerId, "worker id");
        Objects.requireNonNull(consumedItemId, "consumed item id");
        Objects.requireNonNull(inputHold, "production input hold");
        Objects.requireNonNull(outputItemId, "output item id");
        Objects.requireNonNull(workProgress, "production work progress"); workTraversal = Objects.requireNonNull(workTraversal, "production work traversal");
        if (!consumedItemId.equals(inputHold.itemId())) throw new IllegalArgumentException("production input hold must retain its exact item id");
        if (outputItemKind == null || !outputItemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")) {
            throw new IllegalArgumentException("production output kind must be namespace:path");
        }
        if (outputCount <= 0 || outputCount > 64) throw new IllegalArgumentException("production output count must be 1..64");
        if (!workTraversal.provenance().equals(facilityId) || workTraversal.edges().stream().anyMatch(edge -> edge.kind() != TraversalKind.PEDESTRIAN
                || !edge.traversableBy(TraversalCapability.PEDESTRIAN)) || traversalCursor < 0
                || traversalCursor >= workTraversal.linearCorridorSurfaces().size()) {
            throw new IllegalArgumentException("production job must retain one open pedestrian work traversal and cursor");
        }
    }

    /** Compatibility fixture constructor: an existing inventory stack is a materialized hold. */
    public ProductionJob(SubjectId id, SubjectId settlementId, SubjectId facilityId, SubjectId workerId, SubjectId consumedItemId,
                         SubjectId outputItemId, String outputItemKind, int outputCount) {
        this(id, settlementId, facilityId, workerId, consumedItemId, new ProductionInputHold.Materialized(consumedItemId), outputItemId,
                outputItemKind, outputCount, ProductionWorkProgress.notStarted(), fixtureTraversal(id, facilityId), 0);
    }

    public ProductionJob(SubjectId id, SubjectId settlementId, SubjectId facilityId, SubjectId workerId, SubjectId consumedItemId,
                         ProductionInputHold inputHold, SubjectId outputItemId, String outputItemKind, int outputCount) {
        this(id, settlementId, facilityId, workerId, consumedItemId, inputHold, outputItemId, outputItemKind, outputCount,
                ProductionWorkProgress.notStarted(), fixtureTraversal(id, facilityId), 0);
    }

    public ProductionJob withInputHold(ProductionInputHold next) {
        return new ProductionJob(id, settlementId, facilityId, workerId, consumedItemId, next, outputItemId, outputItemKind, outputCount,
                workProgress, workTraversal, traversalCursor);
    }

    public ProductionJob withWorkProgress(ProductionWorkProgress next) {
        return new ProductionJob(id, settlementId, facilityId, workerId, consumedItemId, inputHold, outputItemId, outputItemKind, outputCount,
                next, workTraversal, traversalCursor);
    }

    public ProductionJob withWorkTraversal(TraversalTopology next, int nextCursor) {
        return new ProductionJob(id, settlementId, facilityId, workerId, consumedItemId, inputHold, outputItemId, outputItemKind, outputCount,
                workProgress, next, nextCursor);
    }

    /**
     * Pins an otherwise untouched workshop route to the one body actually observed while its
     * ambient lease transferred into the scene.  Ambient local motion can finish a sub-cell
     * turn between candidate admission and that durable transfer; after the first retained edge
     * or any work stage, the topology/cursor is progress truth and may not be rewritten.
     */
    public ProductionJob rebaseUnstartedTraversal(TraversalTopology next) {
        if (traversalCursor != 0 || !workProgress.equals(ProductionWorkProgress.notStarted())) {
            throw new IllegalArgumentException("only an unstarted production worker may rebase its traversal at HOT hand-off");
        }
        return new ProductionJob(id, settlementId, facilityId, workerId, consumedItemId, inputHold, outputItemId, outputItemKind, outputCount,
                workProgress, Objects.requireNonNull(next, "rebased production-work traversal"), 0);
    }

    private static TraversalTopology fixtureTraversal(SubjectId jobId, SubjectId facilityId) {
        return TraversalTopology.corridor(new TraversalTopologyId("topology:production-fixture-" + jobId.value().replace(':', '-')), 0L, facilityId,
                TraversalKind.PEDESTRIAN, java.util.Set.of(TraversalCapability.PEDESTRIAN), java.util.List.of(SurfaceAnchor.at(0, 0, 0)));
    }
}
