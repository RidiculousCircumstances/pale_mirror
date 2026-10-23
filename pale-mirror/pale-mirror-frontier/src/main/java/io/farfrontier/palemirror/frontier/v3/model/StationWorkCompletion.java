package io.farfrontier.palemirror.frontier.v3.model;

import java.util.LinkedHashMap;

/** Bakery's completion adapter; station algorithms do not read its phases or worker assignment. */
final class StationWorkCompletion implements ProductionOrderPort {
    @Override public ProductionOrderKind kind() { return ProductionOrderKind.STATION_WORK; }
    @Override public void validate(FrontierWorldState before, ProductionOperation operation) {
        var job = before.productionJobs().get(operation.orderId());
        if (job == null || job.stationWork().isEmpty()) throw new IllegalArgumentException("station operation lost its declared bakery order");
        StationWorkOperation.validate(before.productionStations(), job);
    }
    @Override public FungibleForfeitureSettlement inputLost(FrontierWorldState before, ProductionOperation operation, FungibleForfeitureSettlement transaction) {
        return StationWorkCancellation.cancel(before, operation, transaction);
    }
    @Override public java.util.List<SourceStockClaimLossOwner.ReleasedActivity> released(FrontierWorldState before, ProductionOperation operation) {
        return StationWorkCancellation.released(before, operation);
    }
    @Override public FrontierWorldStateUpdate completed(FrontierWorldState before, ProductionOperation operation) {
        var job = before.productionJobs().get(operation.orderId());
        if (job == null || !StationWorkOperation.require(before, job).equals(operation)
                || job.stationWork().orElseThrow().phase() != StationWorkState.Phase.PROCESSING
                || job.stationWork().orElseThrow().pendingPhysicalStep().isPresent())
            throw new IllegalArgumentException("bakery completion has no exact current station/order boundary");
        var jobs = new LinkedHashMap<>(before.productionJobs());
        jobs.put(job.id(), job.withStationWork(job.stationWork().orElseThrow().advance(StationWorkState.Phase.STATION_UNLOAD)));
        return FrontierWorldStateUpdate.begin().productionJobs(jobs);
    }
}
