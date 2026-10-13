package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Collection loss is owned by the exact station operation; the order adapter disposes only its own activity. */
final class StationOutputForfeitureOwner implements FungibleClaimForfeitureOwner {
    private static ProductionOperation require(FrontierWorldState state, ClaimAllocation claim) {
        var operation = state.productionStations().machines().values().stream().flatMap(machine -> machine.operation().stream())
                .filter(value -> value.outputClaimId().equals(claim.id())).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("station output claim lost its exact operation"));
        var recipe = state.bootstrap().ruleset().production().require(operation.recipeId());
        if (claim.purpose() != ClaimPurpose.STATION_OUTPUT || !operation.transformed()
                || !(operation.initiator() instanceof ProductionOperation.OrderInitiator order)
                || !order.orderId().equals(claim.claimantId()) || !claim.itemKind().equals(recipe.product().itemKind())
                || claim.quantity() != operation.outputQuantity()
                || !claim.lotQuantities().equals(Map.of(operation.outputId(), operation.outputQuantity())))
            throw new IllegalArgumentException("station output loss has foreign collection evidence");
        return operation;
    }
    @Override public void validate(FrontierWorldState state, ClaimAllocation claim, SubjectId sourceAccount) {
        var operation = require(state, claim);
        if (!((ProductionOperation.FungibleInput) operation.inputCustody()).accountId().equals(sourceAccount)
                || ContainerPhysicalAuthorityComposition.pending(state, ProductionStationEnergy.record(state, operation.stationId()).id()))
            throw new IllegalArgumentException("station output loss cannot bypass a prepared pickup");
    }
    @Override public FungibleForfeitureSettlement settle(FrontierWorldState before, List<ClaimAllocation> claims, FungibleForfeitureSettlement transaction) {
        var stations = transaction.productionStations();
        for (var claim : claims) {
            var operation = require(before, claim);
            stations = stations.with(stations.require(operation.stationId()).cancel(operation.id()));
            transaction = ProductionOperationCompletionComposition.inputLost(before, operation, transaction);
        }
        return transaction.withStations(stations);
    }
    @Override public List<SourceStockClaimLossOwner.ReleasedActivity> released(FrontierWorldState before, ClaimAllocation claim) {
        return ProductionOperationCompletionComposition.released(before, require(before, claim));
    }
}
