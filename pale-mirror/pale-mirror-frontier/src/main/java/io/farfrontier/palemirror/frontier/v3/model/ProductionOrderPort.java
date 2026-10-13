package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** A task family owns its exact station-operation relation and consequence, not the shared machine owner. */
interface ProductionOrderPort extends ProductionOperationCompletionPort {
    ProductionOrderKind kind();
    void validate(FrontierWorldState state, ProductionOperation operation);
    FungibleForfeitureSettlement inputLost(FrontierWorldState state, ProductionOperation operation, FungibleForfeitureSettlement transaction);
    List<SourceStockClaimLossOwner.ReleasedActivity> released(FrontierWorldState state, ProductionOperation operation);
}
