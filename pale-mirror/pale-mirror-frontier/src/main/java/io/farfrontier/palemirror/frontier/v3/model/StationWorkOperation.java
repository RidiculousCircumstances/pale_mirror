package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Optional;

/** Station fulfillment binds its exact order to the shared machine; the machine service never inspects this family. */
public final class StationWorkOperation {
    private static final ImmutableReferenceIndex.Source<ProductionStations, SubjectId, ProductionStations.Machine> MACHINES =
            new ImmutableReferenceIndex.Source<>(ProductionStations::machines);
    private static final ImmutableReferenceIndex<ProductionStations, SubjectId, ProductionJob> OPERATIONS =
            new ImmutableReferenceIndex<>(SimulationCapacity.MAX_RESIDENTS, 1, java.util.List.of(MACHINES),
                    (stations, job) -> job.stationWork().<java.util.List<ImmutableReferenceIndex.Read<ProductionStations>>>map(
                            work -> java.util.List.of(MACHINES.at(work.stationId()))).orElseGet(java.util.List::of),
                    (stations, id, job) -> { if (job.stationWork().isPresent()) validate(stations, job); });
    static void validateAll(FrontierBootstrap bootstrap, ProductionStations stations, java.util.Map<SubjectId, ProductionJob> jobs) {
        if (jobs.size() > SimulationCapacity.MAX_RESIDENTS)
            jobs.values().stream().filter(job -> job.stationWork().isPresent()).forEach(job -> validate(stations, job));
        else OPERATIONS.validate(bootstrap, jobs, stations);
    }
    private StationWorkOperation() { }
    public static java.util.OptionalInt loadSlot(FrontierWorldState state, ProductionJob job) {
        var work = job.stationWork().orElseThrow();
        var station = ProductionStationEnergy.record(state, work.stationId()).productionStation().orElseThrow();
        if (job.inputHold() instanceof ProductionInputHold.Materialized) {
            for (int slot : station.inputSlots()) if (state.inventory().slotVacant(new InventoryCustody.ContainerSlot(station.containerId(), slot))
                    && ReferenceContainerCustody.expectedFungibleSlot(state, station.containerId(), slot).isEmpty())
                return java.util.OptionalInt.of(slot);
            return java.util.OptionalInt.empty();
        }
        return ContainerMaterialDestination.select(state, station.custody(ActorContainerItemOrder.StationPort.INPUT), work.inputItemKind(),
                job.inputQuantities(), job.inputCount(), Optional.empty(), true).map(value -> java.util.OptionalInt.of(value.slot()))
                .orElseGet(java.util.OptionalInt::empty);
    }
    public record Loaded(ProductionJob job, ExactInventory inventory, ProductionStations machines) { }
    public static ProductionStationProcessing.StartReadiness loadReadiness(FrontierWorldState state, ProductionJob job) {
        var work = job.stationWork().orElseThrow();
        int quantity = work.materials().queued().isEmpty() && work.materials().loaded().isEmpty() ? feasibleInput(state, job)
                : StationWorkMaterialAccounting.plannedUnits(job);
        // Preserve the station's precise occupied/pending/energy diagnosis even
        // when not one recipe batch can run yet.
        if (!work.materials().queued().isEmpty()) {
            var machine = state.productionStations().require(work.stationId());
            return machine.operation().isPresent() ? ProductionStationProcessing.StartReadiness.OPERATION_ACTIVE
                    : machine.pendingFuel().isPresent() ? ProductionStationProcessing.StartReadiness.FUEL_EFFECT_PENDING
                    : ProductionStationProcessing.StartReadiness.READY;
        }
        return ProductionStationProcessing.startReadiness(state, work.stationId(), work.recipeId(),
                quantity == 0 ? job.inputCount() : quantity);
    }
    private static int feasibleInput(FrontierWorldState state, ProductionJob job) {
        if (job.rights().mode() != ProductionRights.Mode.PUBLIC_PRODUCTION
                || job.inputHold() instanceof ProductionInputHold.Materialized
                || !job.stationWork().orElseThrow().materials().loaded().isEmpty()
                || !job.stationWork().orElseThrow().materials().queued().isEmpty()) return job.inputCount();
        var work = job.stationWork().orElseThrow();
        return ProductionBatchSizing.inputQuantity(state.bootstrap().ruleset().production().require(work.recipeId()),
                job.inputCount(), 64);
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
            var work = job.stationWork().orElseThrow(); var claim = claimId(job);
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
        if (!(job.inputHold() instanceof ProductionInputHold.Materialized)) {
            var work = job.stationWork().orElseThrow();
            var depositedInput = new StationWorkMaterials.Loaded(work.inputItemKind(), claimId(job), job.inputQuantities());
            if (!work.materials().queued().isEmpty()) {
                var next = work.materials().queued().getFirst();
                var resources = inventory.fungibleResources();
                ProductionInputHold hold;
                if (!next.withdrawn() && ReferenceContainerCustody.hasLiveCustody(before, next.sourceContainer())) {
                    long sourceEpoch = before.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(next.sourceContainer())).authorityEpoch();
                    hold = new ProductionInputHold.FungibleBound(next.selection().firstLotId(), next.sourceAccount(), next.claimId(), sourceEpoch, next.lots());
                } else hold = new ProductionInputHold.FungibleCold(next.selection().firstLotId(), next.sourceAccount(), next.claimId(), next.lots());
                return new Loaded(job.nextStationMaterial(depositedInput, hold), inventory, before.productionStations());
            }
            job = job.withStationWork(work.withMaterials(work.materials().deposited(depositedInput)));
        }
        var station = ProductionStationEnergy.record(before, job.stationWork().orElseThrow().stationId()).productionStation().orElseThrow();
        var machines = loaded(before, job, station);
        inventory = ProductionStationProcessing.adoptInputOwnership(inventory, machines.require(station.id()).operation().orElseThrow());
        return new Loaded(job.withStationWork(job.stationWork().orElseThrow().advance(StationWorkState.Phase.PROCESSING)), inventory, machines);
    }
    public static ProductionOperation.Id operationId(ProductionJob job) {
        return new ProductionOperation.Id(new SubjectId("operation:production-" + job.id().value().replace(':', '-')));
    }
    public static ProductionOperation require(FrontierWorldState state, ProductionJob job) {
        var work = job.stationWork().orElseThrow();
        var operation = ProductionStationProcessing.require(state, work.stationId(), job.id(), work.recipeId());
        if (!operation.initiator().equals(new ProductionOperation.OrderInitiator(ProductionOrderKind.STATION_WORK, job.id())))
            throw new IllegalArgumentException("bakery cannot adopt a foreign station client operation");
        return operation;
    }
    public static ProductionStations loaded(FrontierWorldState state, ProductionJob job, ProductionStationSpec station) {
        var work = job.stationWork().orElseThrow();
        var recipe = state.bootstrap().ruleset().production().require(work.recipeId());
        int quantity = job.recipeInputQuantities().values().stream().mapToInt(Integer::intValue).sum();
        var operation = new ProductionOperation(operationId(job), station.id(), job.id(),
                work.recipeId(), input(state, job), job.outputItemId(), quantity, job.outputCount(),
                Optional.empty(), false,
                new ProductionOperation.OrderInitiator(ProductionOrderKind.STATION_WORK, job.id()), Optional.empty());
        return ProductionStationProcessing.start(state, station, operation);
    }
    public static void validate(ProductionStations stations, ProductionJob job) {
        var work = job.stationWork().orElseThrow();
        if (work.phase() != StationWorkState.Phase.PROCESSING && work.phase() != StationWorkState.Phase.STATION_UNLOAD) return;
        var operation = stations.require(work.stationId()).operation().orElseThrow(
                () -> new IllegalArgumentException("station order lost its exact machine operation"));
        if (!operation.initiator().equals(new ProductionOperation.OrderInitiator(ProductionOrderKind.STATION_WORK, job.id()))
                || !operation.orderId().equals(job.id()) || !operation.recipeId().equals(work.recipeId())
                || !matchesInput(operation, job)
                || !operation.outputId().equals(job.outputItemId())
                || operation.inputQuantity() != job.recipeInputQuantities().values().stream().mapToInt(Integer::intValue).sum()
                || operation.outputQuantity() != job.outputCount()
                || operation.transformed() != (work.phase() == StationWorkState.Phase.STATION_UNLOAD)
                || work.pendingPhysicalStep().filter(step -> step.phase() == StationWorkState.Phase.PROCESSING).isPresent() && !operation.complete())
            throw new IllegalArgumentException("bakery order contradicts its exact station operation");
    }
    private static ProductionOperation.InputCustody input(FrontierWorldState state, ProductionJob job) {
        var work = job.stationWork().orElseThrow();
        if (job.inputHold() instanceof ProductionInputHold.Materialized)
            return new ProductionOperation.ExactInput(job.consumedItemId());
        var materials = new ProductionOperation.FungibleInput(work.stationAccountId(), job.recipeInputQuantities(),
                job.recipeInputClaims());
        if (work.maintenanceTarget().isEmpty()) return materials;
        var item = state.inventory().items().get(work.maintenanceTarget().orElseThrow().itemId());
        if (item == null) throw new IllegalArgumentException("repair target vanished before station work");
        return new ProductionOperation.RepairInput(materials, item);
    }
    private static boolean matchesInput(ProductionOperation operation, ProductionJob job) {
        var work = job.stationWork().orElseThrow();
        if (job.inputHold() instanceof ProductionInputHold.Materialized)
            return operation.inputCustody().equals(new ProductionOperation.ExactInput(job.consumedItemId()));
        var materials = new ProductionOperation.FungibleInput(work.stationAccountId(), job.recipeInputQuantities(),
                job.recipeInputClaims());
        if (work.maintenanceTarget().isEmpty()) return operation.inputCustody().equals(materials);
        return operation.inputCustody() instanceof ProductionOperation.RepairInput repair
                && repair.materialsInput().equals(materials)
                && repair.target().id().equals(work.maintenanceTarget().orElseThrow().itemId());
    }
    static java.util.Set<SubjectId> activeOrders(java.util.Map<SubjectId, ProductionJob> jobs) {
        return jobs.values().stream().filter(job -> job.stationWork().isPresent())
                .filter(job -> job.stationWork().orElseThrow().phase() == StationWorkState.Phase.PROCESSING
                        || job.stationWork().orElseThrow().phase() == StationWorkState.Phase.STATION_UNLOAD)
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
