package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * A durable facility job that owns one exact input until its output is confirmed or work is
 * explicitly cancelled. COLD holds the stack itself; HOT retains its physical depot stack.
 */
public record ProductionJob(
        SubjectId id,
        SubjectId taskId,
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
        int traversalCursor,
        java.util.Optional<BakeryWorkState> bakeryWork,
        StationApproachState spatial
) {
    public ProductionJob {
        Objects.requireNonNull(id, "production job id");
        Objects.requireNonNull(taskId, "production task id");
        Objects.requireNonNull(settlementId, "settlement id");
        Objects.requireNonNull(facilityId, "facility id");
        Objects.requireNonNull(workerId, "worker id");
        Objects.requireNonNull(consumedItemId, "consumed item id");
        Objects.requireNonNull(inputHold, "production input hold");
        Objects.requireNonNull(outputItemId, "output item id");
        Objects.requireNonNull(workProgress, "production work progress"); workTraversal = Objects.requireNonNull(workTraversal, "production work traversal");
        bakeryWork = Objects.requireNonNull(bakeryWork, "bakery work state");
        Objects.requireNonNull(spatial, "production spatial continuation");
        if (bakeryWork.isPresent() && spatial.pending())
            throw new IllegalArgumentException("bakery goals cannot carry a legacy station approach");
        if (!taskId.value().startsWith("task:")) throw new IllegalArgumentException("production job needs its declared strategic task");
        if (!consumedItemId.equals(inputHold.itemId())) throw new IllegalArgumentException("production input hold must retain its exact item id");
        if (outputItemKind == null || !outputItemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")) {
            throw new IllegalArgumentException("production output kind must be namespace:path");
        }
        if (outputCount <= 0 || outputCount > 64) throw new IllegalArgumentException("production output count must be 1..64");
        int retainedInput = switch (inputHold) {
            case ProductionInputHold.FungibleCold cold -> cold.inputLots().values().stream().mapToInt(Integer::intValue).sum();
            case ProductionInputHold.FungibleBound bound -> bound.inputLots().values().stream().mapToInt(Integer::intValue).sum();
            case ProductionInputHold.Cold cold -> cold.item().count();
            case ProductionInputHold.Materialized ignored -> outputCount; // Checked against inventory at admission.
        };
        if (outputCount != retainedInput)
            throw new IllegalArgumentException("bread job output must match its retained input allocation");
        if (!workTraversal.provenance().equals(facilityId) || workTraversal.edges().stream().anyMatch(edge -> edge.kind() != TraversalKind.PEDESTRIAN
                || !edge.traversableBy(TraversalCapability.PEDESTRIAN)) || traversalCursor < 0
                || traversalCursor >= workTraversal.linearCorridorSurfaces().size()) {
            throw new IllegalArgumentException("production job must retain one open pedestrian work traversal and cursor");
        }
        if (spatial.approach().isPresent() && !spatial.approach().orElseThrow().target().equals(
                ProductionJourneyKnowledge.target(workTraversal, traversalCursor, workProgress)))
            throw new IllegalArgumentException("production approach must retain its unfinished semantic station");
    }

    /** Admission starts without an observed departure; current-schema hydration supplies spatial state explicitly. */
    public ProductionJob(SubjectId id, SubjectId taskId, SubjectId settlementId, SubjectId facilityId, SubjectId workerId,
                         SubjectId consumedItemId, ProductionInputHold inputHold, SubjectId outputItemId,
                         String outputItemKind, int outputCount, ProductionWorkProgress workProgress,
                         TraversalTopology workTraversal, int traversalCursor, java.util.Optional<BakeryWorkState> bakeryWork) {
        this(id, taskId, settlementId, facilityId, workerId, consumedItemId, inputHold, outputItemId,
                outputItemKind, outputCount, workProgress, workTraversal, traversalCursor, bakeryWork, StationApproachState.initial());
    }

    /** Existing test fixtures and the retiring route constructor have no bakery phase. */
    public ProductionJob(SubjectId id, SubjectId taskId, SubjectId settlementId, SubjectId facilityId, SubjectId workerId,
                         SubjectId consumedItemId, ProductionInputHold inputHold, SubjectId outputItemId,
                         String outputItemKind, int outputCount, ProductionWorkProgress workProgress,
                         TraversalTopology workTraversal, int traversalCursor) {
        this(id, taskId, settlementId, facilityId, workerId, consumedItemId, inputHold, outputItemId,
                outputItemKind, outputCount, workProgress, workTraversal, traversalCursor, java.util.Optional.empty());
    }

    /** Compatibility fixture constructor: an existing inventory stack is a materialized hold. */
    public ProductionJob(SubjectId id, SubjectId taskId, SubjectId settlementId, SubjectId facilityId, SubjectId workerId, SubjectId consumedItemId,
                         SubjectId outputItemId, String outputItemKind, int outputCount) {
        this(id, taskId, settlementId, facilityId, workerId, consumedItemId, new ProductionInputHold.Materialized(consumedItemId), outputItemId,
                outputItemKind, outputCount, ProductionWorkProgress.notStarted(), fixtureTraversal(id, facilityId), 0);
    }

    public ProductionJob(SubjectId id, SubjectId taskId, SubjectId settlementId, SubjectId facilityId, SubjectId workerId, SubjectId consumedItemId,
                         ProductionInputHold inputHold, SubjectId outputItemId, String outputItemKind, int outputCount) {
        this(id, taskId, settlementId, facilityId, workerId, consumedItemId, inputHold, outputItemId, outputItemKind, outputCount,
                ProductionWorkProgress.notStarted(), fixtureTraversal(id, facilityId), 0);
    }

    public ProductionJob withInputHold(ProductionInputHold next) {
        return new ProductionJob(id, taskId, settlementId, facilityId, workerId, consumedItemId, next, outputItemId, outputItemKind, outputCount,
                workProgress, workTraversal, traversalCursor, bakeryWork, spatial);
    }

    /** Family-owned reservation view; cargo/return ownership can survive releasing the machine. */
    public boolean reservesFacility() {
        return bakeryWork.map(BakeryWorkState::reservesStation).orElse(true);
    }
    /** Replaces only an unconsumed bakery input allocation; the job, worker and output identity remain stable. */
    public ProductionJob reallocateBakeryInput(ProductionInputHold next) {
        if (bakeryWork.isEmpty() || bakeryWork.orElseThrow().phase() != BakeryWorkState.Phase.DEPOT_PICKUP
                || bakeryWork.orElseThrow().pendingPhysicalStep().isPresent()
                || bakeryWork.orElseThrow().block().map(value -> value.reason() != BakeryWorkBlock.Reason.SOURCE_CHANGED).orElse(true)
                || !(next instanceof ProductionInputHold.FungibleCold || next instanceof ProductionInputHold.FungibleBound
                        || next instanceof ProductionInputHold.Materialized))
            throw new IllegalArgumentException("only an unconsumed bakery input can be reallocated");
        return new ProductionJob(id, taskId, settlementId, facilityId, workerId, next.itemId(), next,
                outputItemId, outputItemKind, outputCount, workProgress, workTraversal, traversalCursor,
                java.util.Optional.of(bakeryWork.orElseThrow().withReallocatedDepotAccount(
                        next instanceof ProductionInputHold.FungibleCold cold ? cold.accountId()
                                : next instanceof ProductionInputHold.FungibleBound bound ? bound.accountId()
                                : bakeryWork.orElseThrow().sourceAccountId())), spatial);
    }
    /** Exact resource subjects retained by this job, including every fungible input lot. */
    public java.util.Map<SubjectId, Integer> inputQuantities() {
        return switch (inputHold) {
            case ProductionInputHold.FungibleCold cold -> cold.inputLots();
            case ProductionInputHold.FungibleBound bound -> bound.inputLots();
            default -> java.util.Map.of(consumedItemId, outputCount);
        };
    }
    /** Resource representation cannot bypass labor or a live physical custodian. */
    public void requireColdCompletion(FrontierWorldState state) {
        var actor = state.actorLocations().get(workerId);
        SubjectId depot = FrontierWorldState.depotId(settlementId);
        if (ReferenceContainerCustody.hasLiveCustody(state, depot) || ReferenceContainerCustody.blocksCanonicalUse(state, depot)
                || !workProgress.terminalEffectEligible() || traversalCursor != workTraversal.linearCorridorSurfaces().size() - 1
                || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || !FrontierSceneAdmission.available(state, java.util.List.of(workerId))
                || state.sceneLeases().values().stream().anyMatch(lease -> lease.retainsMemberCustody(workerId))) {
            throw new IllegalArgumentException("production completion requires finished work and exclusive COLD worker and resource custody");
        }
    }

    public ProductionJob withWorkProgress(ProductionWorkProgress next) {
        return new ProductionJob(id, taskId, settlementId, facilityId, workerId, consumedItemId, inputHold, outputItemId, outputItemKind, outputCount,
                next, workTraversal, traversalCursor, bakeryWork, spatial.cleared());
    }

    public ProductionJob withWorkTraversal(TraversalTopology next, int nextCursor) {
        return new ProductionJob(id, taskId, settlementId, facilityId, workerId, consumedItemId, inputHold, outputItemId, outputItemKind, outputCount,
                workProgress, next, nextCursor, bakeryWork, spatial.cleared());
    }

    public ProductionJob withBakeryWork(BakeryWorkState next) {
        if (bakeryWork.isEmpty()) throw new IllegalArgumentException("only an admitted bakery job may advance bakery work");
        return new ProductionJob(id, taskId, settlementId, facilityId, workerId, consumedItemId, inputHold, outputItemId,
                outputItemKind, outputCount, workProgress, workTraversal, traversalCursor, java.util.Optional.of(next), spatial);
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
        return new ProductionJob(id, taskId, settlementId, facilityId, workerId, consumedItemId, inputHold, outputItemId, outputItemKind, outputCount,
                workProgress, Objects.requireNonNull(next, "rebased production-work traversal"), 0, bakeryWork, spatial.cleared());
    }

    public ProductionJob withSpatial(StationApproachState next) {
        return new ProductionJob(id, taskId, settlementId, facilityId, workerId, consumedItemId, inputHold,
                outputItemId, outputItemKind, outputCount, workProgress, workTraversal, traversalCursor, bakeryWork, next);
    }

    private static TraversalTopology fixtureTraversal(SubjectId jobId, SubjectId facilityId) {
        return TraversalTopology.corridor(new TraversalTopologyId("topology:production-fixture-" + jobId.value().replace(':', '-')), 0L, facilityId,
                TraversalKind.PEDESTRIAN, java.util.Set.of(TraversalCapability.PEDESTRIAN), java.util.List.of(SurfaceAnchor.at(0, 0, 0)));
    }
}
