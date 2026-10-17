package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Optional;

/** Bakery binds its order to the shared machine; the machine service never inspects this family. */
public final class BakeryStationOperation {
    private BakeryStationOperation() { }
    public record Loaded(ProductionJob job, ExactInventory inventory, ProductionStations machines) { }
    public static ProductionStationProcessing.StartReadiness loadReadiness(FrontierWorldState state, ProductionJob job) {
        var work = job.bakeryWork().orElseThrow();
        int quantity = feasibleInput(state, job);
        // Preserve the station's precise occupied/pending/energy diagnosis even
        // when not one recipe batch can run yet.
        return ProductionStationProcessing.startReadiness(state, work.stationId(), work.recipeId(),
                quantity == 0 ? job.inputCount() : quantity);
    }
    private static int feasibleInput(FrontierWorldState state, ProductionJob job) {
        if (job.rights().mode() != ProductionRights.Mode.PUBLIC_PRODUCTION
                || job.inputHold() instanceof ProductionInputHold.Materialized) return job.inputCount();
        var work = job.bakeryWork().orElseThrow();
        return ProductionBatchSizing.inputQuantity(state.bootstrap().ruleset().production().require(work.recipeId()),
                job.inputCount(), 64, state.productionStations().require(work.stationId()).storedHeat());
    }
    /** Resource transfer is already positively settled. Only reservation metadata
     * changes here; surplus input stays at the station, available to later work. */
    public static Loaded settleLoad(FrontierWorldState before, ProductionJob job, ExactInventory deposited, long epoch) {
        int quantity = feasibleInput(before, job);
        if (quantity == 0) throw new IllegalArgumentException("station load has no executable recipe portion");
        var inventory = deposited;
        if (quantity < job.inputCount()) {
            var selected = new java.util.TreeMap<SubjectId, Integer>(); int remaining = quantity;
            for (var entry : new java.util.TreeMap<>(job.inputQuantities()).entrySet()) {
                int take = Math.min(remaining, entry.getValue());
                if (take > 0) selected.put(entry.getKey(), take);
                remaining -= take;
            }
            var work = job.bakeryWork().orElseThrow(); var claim = claimId(job);
            var ledger = inventory.fungibleResources();
            ledger = epoch == 0 ? ledger.releaseClaim(work.stationAccountId(), claim)
                    : ledger.releaseBoundClaim(work.stationAccountId(), claim, epoch);
            var allocation = new ClaimAllocation(claim, job.id(), job.rights().resourceOwner().id(), work.inputItemKind(),
                    quantity, selected, ClaimPurpose.PRODUCTION_WORK);
            ledger = epoch == 0 ? ledger.reserve(allocation, work.stationAccountId())
                    : ledger.reserveBound(allocation, work.stationAccountId(), epoch);
            ProductionInputHold hold = switch (job.inputHold()) {
                case ProductionInputHold.FungibleCold value -> new ProductionInputHold.FungibleCold(selected.firstKey(), value.accountId(), claim, selected);
                case ProductionInputHold.FungibleBound value -> new ProductionInputHold.FungibleBound(selected.firstKey(), value.accountId(), claim, value.authorityEpoch(), selected);
                default -> throw new IllegalArgumentException("indivisible input cannot be partially assigned");
            };
            job = job.withLoadedPortion(hold, before.bootstrap().ruleset().production().require(work.recipeId()));
            inventory = inventory.withFungibleResources(ledger);
        }
        var station = ProductionStationEnergy.record(before, job.bakeryWork().orElseThrow().stationId()).productionStation().orElseThrow();
        var machines = loaded(before, job, station);
        inventory = ProductionStationProcessing.adoptInputOwnership(inventory, machines.require(station.id()).operation().orElseThrow());
        return new Loaded(job.withBakeryWork(job.bakeryWork().orElseThrow().advance(BakeryWorkState.Phase.PROCESSING)), inventory, machines);
    }
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
