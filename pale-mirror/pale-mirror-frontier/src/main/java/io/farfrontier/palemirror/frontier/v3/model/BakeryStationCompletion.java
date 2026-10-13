package io.farfrontier.palemirror.frontier.v3.model;

import java.util.LinkedHashMap;

/** Bakery's completion adapter; station algorithms do not read its phases or worker assignment. */
final class BakeryStationCompletion implements ProductionOrderPort {
    @Override public ProductionOrderKind kind() { return ProductionOrderKind.BAKERY; }
    @Override public void validate(FrontierWorldState before, ProductionOperation operation) {
        var job = before.productionJobs().get(operation.orderId());
        if (job == null || job.bakeryWork().isEmpty()) throw new IllegalArgumentException("station operation lost its declared bakery order");
        BakeryStationOperation.validate(before.productionStations(), job);
    }
    @Override public FungibleForfeitureSettlement inputLost(FrontierWorldState before, ProductionOperation operation, FungibleForfeitureSettlement transaction) {
        return BakeryStationCancellation.cancel(before, operation, transaction);
    }
    @Override public java.util.List<SourceStockClaimLossOwner.ReleasedActivity> released(FrontierWorldState before, ProductionOperation operation) {
        return BakeryStationCancellation.released(before, operation);
    }
    @Override public FrontierWorldStateUpdate completed(FrontierWorldState before, ProductionOperation operation) {
        var job = before.productionJobs().get(operation.orderId());
        if (job == null || !BakeryStationOperation.require(before, job).equals(operation)
                || job.bakeryWork().orElseThrow().phase() != BakeryWorkState.Phase.PROCESSING
                || job.bakeryWork().orElseThrow().pendingPhysicalStep().isPresent())
            throw new IllegalArgumentException("bakery completion has no exact current station/order boundary");
        var jobs = new LinkedHashMap<>(before.productionJobs());
        jobs.put(job.id(), job.withBakeryWork(job.bakeryWork().orElseThrow().advance(BakeryWorkState.Phase.STATION_UNLOAD)));
        return FrontierWorldStateUpdate.begin().productionJobs(jobs);
    }
}
