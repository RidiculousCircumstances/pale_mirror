package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;

/** Station input loss cancels only its own operation. Stock accounting is performed by the common transaction. */
final class StationClaimForfeitureOwner implements FungibleClaimForfeitureOwner {
    private static ProductionOperation require(FrontierWorldState state, ClaimAllocation claim) {
        var operation = state.productionStations().machines().values().stream().flatMap(machine -> machine.operation().stream())
                .filter(value -> value.id().value().equals(claim.claimantId())).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("station claim lost its exact processing owner"));
        if (claim.purpose() != ClaimPurpose.STATION_PROCESSING || operation.transformed()
                || operation.inputCustody().materials().isEmpty()
                || operation.inputCustody().materials().orElseThrow().claims().getOrDefault(claim.id(), 0) != claim.quantity()
                || claim.lotQuantities().isEmpty() || claim.lotQuantities().entrySet().stream().anyMatch(entry ->
                    operation.inputCustody().materials().orElseThrow().lots().getOrDefault(entry.getKey(), 0) < entry.getValue()))
            throw new IllegalArgumentException("station stock loss contradicts its current operation allocation");
        return operation;
    }
    @Override public void validate(FrontierWorldState state, ClaimAllocation claim, SubjectId sourceAccount) {
        var operation = require(state, claim);
        if (!operation.inputCustody().materials().orElseThrow().accountId().equals(sourceAccount)
                || ContainerPhysicalAuthorityComposition.pending(state, ProductionStationEnergy.record(state, operation.stationId()).id()))
            throw new IllegalArgumentException("station claim loss cannot bypass a retained physical effect");
    }
    @Override public FungibleForfeitureSettlement settle(FrontierWorldState before, List<ClaimAllocation> claims,
                                                        FungibleForfeitureSettlement transaction) {
        var stations = transaction.productionStations();
        var operations = new java.util.LinkedHashMap<ProductionOperation.Id, ProductionOperation>();
        for (var claim : claims) { var operation = require(before, claim); operations.putIfAbsent(operation.id(), operation); }
        for (var operation : operations.values()) {
            stations = stations.with(stations.require(operation.stationId()).cancel(operation.id()));
            var resources = transaction.inventory().fungibleResources();
            var remaining = operation.inputCustody().materials().orElseThrow().claims().keySet().stream()
                    .filter(resources.claims()::containsKey).collect(java.util.stream.Collectors.toSet());
            if (!remaining.isEmpty()) transaction = transaction.withTrade(transaction.inventory().withFungibleResources(
                    resources.releaseClaims(remaining)), transaction.companies());
            transaction = ProductionOperationCompletionComposition.inputLost(before, operation, transaction);
        }
        return transaction.withStations(stations);
    }
    @Override public List<SourceStockClaimLossOwner.ReleasedActivity> released(FrontierWorldState before, ClaimAllocation claim) {
        return ProductionOperationCompletionComposition.released(before, require(before, claim));
    }
}
