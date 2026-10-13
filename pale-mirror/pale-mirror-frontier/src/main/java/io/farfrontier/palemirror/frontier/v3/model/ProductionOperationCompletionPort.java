package io.farfrontier.palemirror.frontier.v3.model;

/** Client acknowledges station output in the same atomic publication; never performs the recipe itself. */
@FunctionalInterface
public interface ProductionOperationCompletionPort {
    FrontierWorldStateUpdate completed(FrontierWorldState before, ProductionOperation operation);
}
