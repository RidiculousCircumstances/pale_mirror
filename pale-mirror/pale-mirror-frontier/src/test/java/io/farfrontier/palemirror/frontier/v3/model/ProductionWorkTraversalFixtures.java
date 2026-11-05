package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/**
 * Legacy job fixtures may construct an immutable initial world; the production API requires current
 * state.
 */
final class ProductionWorkTraversalFixtures {
    static TraversalTopology compile(
            FrontierBootstrap bootstrap,
            SettlementStructure facility,
            ActorLocation worker,
            SubjectId job) {
        var state = FrontierWorldState.initial(bootstrap);
        var actor =
                FrontierWorldStateSupport.settlement(bootstrap, facility.settlementId())
                        .residents()
                        .getFirst()
                        .id();
        return ProductionWorkTraversal.compile(state, facility, actor, worker, job);
    }

    private ProductionWorkTraversalFixtures() {}
}
