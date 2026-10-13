package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Optional;

/** Bakery binds its order to the shared machine; the machine service never inspects this family. */
public final class BakeryStationOperation {
    private BakeryStationOperation() { }
    public static ProductionOperation.Id operationId(ProductionJob job) {
        return new ProductionOperation.Id(new SubjectId("operation:production-" + job.id().value().replace(':', '-')));
    }
    public static ProductionOperation require(FrontierWorldState state, ProductionJob job) {
        var work = job.bakeryWork().orElseThrow();
        var operation = ProductionStationProcessing.require(state, work.stationId(), job.id(), work.recipeId());
        if (!operation.initiator().equals(new ProductionOperation.OrderInitiator(ProductionOrderKind.BAKERY, job.id())))
            throw new IllegalArgumentException("bakery cannot adopt a foreign station client operation");
        return operation;
    }
    public static ProductionStations loaded(FrontierWorldState state, ProductionJob job, ProductionStationSpec station) {
        var work = job.bakeryWork().orElseThrow();
        var recipe = state.bootstrap().ruleset().production().require(work.recipeId());
        int quantity = job.inputQuantities().values().stream().mapToInt(Integer::intValue).sum();
        var operation = new ProductionOperation(operationId(job), station.id(), job.id(),
                work.recipeId(), job.inputHold() instanceof ProductionInputHold.Materialized
                        ? new ProductionOperation.ExactInput(job.consumedItemId())
                        : new ProductionOperation.FungibleInput(work.stationAccountId(), job.inputQuantities(), java.util.Map.of(
                            claimId(job), job.inputCount())), job.outputItemId(), quantity, job.outputCount(),
                Math.multiplyExact(recipe.heatPerBatch(), quantity / recipe.ingredient().quantity()), Optional.empty(), false,
                new ProductionOperation.OrderInitiator(ProductionOrderKind.BAKERY, job.id()));
        return ProductionStationProcessing.start(state, station, operation);
    }
    public static void validate(ProductionStations stations, ProductionJob job) {
        var work = job.bakeryWork().orElseThrow();
        if (work.phase() != BakeryWorkState.Phase.PROCESSING && work.phase() != BakeryWorkState.Phase.STATION_UNLOAD) return;
        var operation = stations.require(work.stationId()).operation().orElseThrow();
        if (!operation.initiator().equals(new ProductionOperation.OrderInitiator(ProductionOrderKind.BAKERY, job.id()))
                || !operation.orderId().equals(job.id()) || !operation.recipeId().equals(work.recipeId())
                || !operation.inputCustody().equals(job.inputHold() instanceof ProductionInputHold.Materialized
                        ? new ProductionOperation.ExactInput(job.consumedItemId()) : new ProductionOperation.FungibleInput(
                            work.stationAccountId(), job.inputQuantities(), java.util.Map.of(claimId(job), job.inputCount())))
                || !operation.outputId().equals(job.outputItemId())
                || operation.inputQuantity() != job.inputQuantities().values().stream().mapToInt(Integer::intValue).sum()
                || operation.outputQuantity() != job.outputCount()
                || operation.transformed() != (work.phase() == BakeryWorkState.Phase.STATION_UNLOAD)
                || work.pendingPhysicalStep().filter(step -> step.phase() == BakeryWorkState.Phase.PROCESSING).isPresent() && !operation.complete())
            throw new IllegalArgumentException("bakery order contradicts its exact station operation");
    }
    static java.util.Set<SubjectId> activeOrders(java.util.Map<SubjectId, ProductionJob> jobs) {
        return jobs.values().stream().filter(job -> job.bakeryWork().isPresent())
                .filter(job -> job.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.PROCESSING
                        || job.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.STATION_UNLOAD)
                .map(ProductionJob::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static SubjectId claimId(ProductionJob job) {
        return switch (job.inputHold()) {
            case ProductionInputHold.FungibleCold hold -> hold.claimId();
            case ProductionInputHold.FungibleBound hold -> hold.claimId();
            case ProductionInputHold.Materialized ignored -> throw new IllegalArgumentException("exact input has no fungible claim");
            case ProductionInputHold.Cold ignored -> throw new IllegalArgumentException("unmaterialized exact input has no fungible claim");
        };
    }
}
