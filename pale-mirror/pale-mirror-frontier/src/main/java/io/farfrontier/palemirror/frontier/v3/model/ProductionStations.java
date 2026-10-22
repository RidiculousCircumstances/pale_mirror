package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.LinkedHashMap;

/** Installed-machine runtime, independent of orders, residents and physical inventory copies. */
public record ProductionStations(Map<SubjectId, Machine> machines) {
    public record Machine(SubjectId stationId, long storedHeat, Optional<ProductionOperation> operation,
                          long revision, Optional<ProductionStationFuelBurn> pendingFuel,
                          Optional<ProductionStationRecipeBoundary> pendingRecipe) {
        public Machine {
            Objects.requireNonNull(stationId); operation = Objects.requireNonNull(operation); pendingFuel = Objects.requireNonNull(pendingFuel);
            pendingRecipe = Objects.requireNonNull(pendingRecipe);
            var retainedOperation = operation;
            if (storedHeat < 0 || storedHeat > 1_000_000_000L
                    || revision < 1 || operation.filter(value -> !value.stationId().equals(stationId)).isPresent()
                    || pendingFuel.filter(value -> !value.stationId().equals(stationId) || value.machineRevision() != revision).isPresent()
                    || pendingRecipe.filter(value -> !value.stationId().equals(stationId) || value.machineRevision() != revision
                        || retainedOperation.isEmpty() || !retainedOperation.orElseThrow().id().equals(value.operationId())
                        || !retainedOperation.orElseThrow().complete() || retainedOperation.orElseThrow().transformed()).isPresent()
                    || pendingFuel.isPresent() && pendingRecipe.isPresent())
                throw new IllegalArgumentException("invalid machine identity or energy");
        }
        public Machine(SubjectId stationId, long storedHeat, Optional<ProductionOperation> operation,
                       long revision, Optional<ProductionStationFuelBurn> pendingFuel) {
            this(stationId, storedHeat, operation, revision, pendingFuel, Optional.empty());
        }
        public static Machine installed(SubjectId stationId) { return new Machine(stationId, 0, Optional.empty(), 1, Optional.empty()); }
        public Machine start(ProductionOperation next) {
            if (operation.isPresent() || pendingFuel.isPresent() || pendingRecipe.isPresent() || !next.stationId().equals(stationId) || storedHeat < next.committedHeat())
                throw new IllegalArgumentException("machine cannot start a competing or unfuelled operation");
            return new Machine(stationId, storedHeat - next.committedHeat(), Optional.of(next), Math.addExact(revision, 1), Optional.empty());
        }
        public Machine replace(ProductionOperation previous, ProductionOperation next) {
            if (pendingFuel.isPresent() || pendingRecipe.isPresent() || !operation.equals(Optional.of(previous)) || !previous.id().equals(next.id()))
                throw new IllegalArgumentException("machine update has stale operation evidence");
            return new Machine(stationId, storedHeat, Optional.of(next), Math.addExact(revision, 1), Optional.empty());
        }
        public Machine release(ProductionOperation.Id id) {
            if (pendingFuel.isPresent() || pendingRecipe.isPresent() || !operation.orElseThrow().id().equals(id) || !operation.orElseThrow().transformed())
                throw new IllegalArgumentException("machine release lacks confirmed output removal");
            return new Machine(stationId, storedHeat, Optional.empty(), Math.addExact(revision, 1), Optional.empty());
        }
        /** A claim-owner cancellation abandons processing, never restores spent fuel or creates output. */
        Machine cancel(ProductionOperation.Id id) {
            if (pendingFuel.isPresent() || pendingRecipe.isPresent() || !operation.orElseThrow().id().equals(id))
                throw new IllegalArgumentException("machine cancellation has no exact uncommitted operation");
            return new Machine(stationId, storedHeat, Optional.empty(), Math.addExact(revision, 1), Optional.empty());
        }
        Machine prepareRecipe(ProductionStationRecipeBoundary boundary) {
            if (pendingFuel.isPresent() || pendingRecipe.isPresent() || operation.isEmpty() || !operation.orElseThrow().complete()
                    || operation.orElseThrow().transformed() || !operation.orElseThrow().id().equals(boundary.operationId())
                    || boundary.machineRevision() != revision || !boundary.stationId().equals(stationId))
                throw new IllegalArgumentException("station recipe preparation lost its exact completed operation");
            return new Machine(stationId, storedHeat, operation, revision, Optional.empty(), Optional.of(boundary));
        }
        Machine completeRecipe(ProductionOperation previous) {
            if (!operation.equals(Optional.of(previous)) || pendingFuel.isPresent())
                throw new IllegalArgumentException("station recipe completion has no exact current operation");
            var next = previous.confirmTransformation();
            return new Machine(stationId, storedHeat, Optional.of(next), Math.addExact(revision, 1), Optional.empty());
        }
        /** Called only by a confirmed fuel-consumption receipt, not by inventory inspection. */
        Machine fuelled(long heat) {
            if (heat <= 0) throw new IllegalArgumentException("fuel must contribute positive energy");
            return new Machine(stationId, Math.addExact(storedHeat, heat), operation, Math.addExact(revision, 1), Optional.empty());
        }
        Machine prepareFuel(ProductionStationFuelBurn burn) {
            if (pendingFuel.isPresent() || operation.isPresent() || burn.machineRevision() != revision || !burn.stationId().equals(stationId))
                throw new IllegalArgumentException("station fuel preparation has competing work or stale evidence");
            return new Machine(stationId, storedHeat, operation, revision, Optional.of(burn));
        }
    }
    public ProductionStations {
        machines = Map.copyOf(Objects.requireNonNull(machines));
        if (machines.size() > 4_096 || machines.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().stationId()))
                || machines.values().stream().flatMap(value -> value.operation().stream()).map(ProductionOperation::id).distinct().count()
                    != machines.values().stream().filter(value -> value.operation().isPresent()).count())
            throw new IllegalArgumentException("invalid or competing installed-machine runtime");
    }
    public static ProductionStations empty() { return new ProductionStations(Map.of()); }
    public Machine require(SubjectId id) {
        var value = machines.get(id);
        if (value == null) throw new IllegalArgumentException("missing installed-machine runtime " + id);
        return value;
    }
    public ProductionStations with(Machine machine) {
        var next = new LinkedHashMap<>(machines); next.put(machine.stationId(), machine); return new ProductionStations(next);
    }
    public void validate(ExactInventory inventory, ProductionCatalog catalog) {
        for (var machine : machines.values()) {
            var station = inventory.containers().values().stream().flatMap(value -> value.productionStation().stream())
                    .filter(value -> value.id().equals(machine.stationId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("machine runtime lost its installed instance"));
            var equipment = catalog.requireEquipment(station.equipmentType());
            if (!equipment.capabilities().contains(station.capability()))
                throw new IllegalArgumentException("installed station capability differs from its declared equipment type");
            machine.operation().ifPresent(operation -> {
                var recipe = catalog.require(operation.recipeId());
                equipment.require(recipe);
                if (operation.initiator() instanceof ProductionOperation.SettlementInitiator institution) {
                    var record = inventory.containers().get(station.containerId());
                    if (!institution.settlementId().equals(record.ownerId()) || !operation.orderId().equals(operation.id().value())
                            || catalog.automaticPolicies().stream().noneMatch(policy -> policy.recipeId().equals(recipe.id())))
                        throw new IllegalArgumentException("automatic station operation lost its explicit institution mandate");
                }
                if (recipe.capability() != station.capability() || recipe.outputForInput(operation.inputQuantity()) != operation.outputQuantity()
                        || Math.multiplyExact(recipe.heatPerBatch(), operation.inputQuantity() / recipe.ingredient().quantity()) != operation.committedHeat())
                    throw new IllegalArgumentException("machine operation differs from its declared recipe");
                if (!operation.transformed()) {
                    switch (operation.inputCustody()) {
                        case ProductionOperation.ExactInput exact -> {
                            var item = inventory.items().get(exact.itemId());
                            if (item == null || item.count() != operation.inputQuantity() || !recipe.ingredient().accepts(item.itemKind())
                                    || !item.custody().equals(new InventoryCustody.ContainerSlot(station.containerId(), station.inputSlot())))
                                throw new IllegalArgumentException("station operation lost its exact reserved input");
                        }
                        case ProductionOperation.FungibleInput fungible -> {
                            var ledger = inventory.fungibleResources(); var account = ledger.accounts().get(fungible.accountId());
                            if (account == null || !account.custody().equals(new ResourceCustody.Container(station.containerId()))
                                    || fungible.lots().entrySet().stream().anyMatch(entry ->
                                        account.lotQuantities().getOrDefault(entry.getKey(), 0) < entry.getValue()
                                        || !recipe.ingredient().accepts(ledger.lots().get(entry.getKey()).itemKind()))
                                    || fungible.claims().entrySet().stream().anyMatch(entry ->
                                        account.claimQuantities().getOrDefault(entry.getKey(), 0) != entry.getValue()
                                        || ledger.claims().get(entry.getKey()).purpose() != ClaimPurpose.STATION_PROCESSING
                                        || !ledger.claims().get(entry.getKey()).claimantId().equals(operation.id().value())))
                                throw new IllegalArgumentException("station operation lost its exact lot/claim allocation");
                        }
                    }
                } else if (operation.initiator() instanceof ProductionOperation.OrderInitiator order
                        && operation.inputCustody() instanceof ProductionOperation.FungibleInput input) {
                    var ledger = inventory.fungibleResources(); var claim = ledger.claims().get(operation.outputClaimId());
                    var account = ledger.accounts().get(input.accountId());
                    if (claim == null || claim.purpose() != ClaimPurpose.STATION_OUTPUT || !claim.claimantId().equals(order.orderId())
                            || !claim.lotQuantities().equals(Map.of(operation.outputId(), operation.outputQuantity()))
                            || account == null || account.claimQuantities().getOrDefault(claim.id(), 0) != operation.outputQuantity())
                        throw new IllegalArgumentException("transformed station operation lost its reserved collection output");
                }
            });
            machine.pendingRecipe().ifPresent(boundary -> boundary.validate(inventory, station, machine.operation().orElseThrow(), catalog));
            machine.pendingFuel().ifPresent(burn -> {
                if (!burn.containerId().equals(station.containerId()) || burn.authorityEpoch() < 1
                        || burn.heat() != catalog.requireFuel(burn.itemKind()).heatUnits()
                        || !burn.source().getFirst().address().equals(new PhysicalStackAddress.ContainerSlot(
                            new InventoryCustody.ContainerSlot(station.containerId(), station.fuelSlot()))))
                    throw new IllegalArgumentException("machine lost its exact pending fuel port/definition");
                var ledger = inventory.fungibleResources(); var account = ledger.accounts().get(burn.accountId());
                var source = burn.source().getFirst();
                var binding = ledger.bindings().values().stream().filter(value -> value.address().equals(source.address())).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("machine pending fuel lost its physical preimage"));
                if (account == null || !account.custody().equals(new ResourceCustody.Container(station.containerId()))
                        || burn.lots().entrySet().stream().anyMatch(entry -> account.lotQuantities().getOrDefault(entry.getKey(), 0) < entry.getValue())
                        || !binding.accountId().equals(account.id()) || binding.authorityEpoch() != burn.authorityEpoch()
                        || binding.quantity() != source.before() || !binding.itemKind().equals(burn.itemKind()))
                    throw new IllegalArgumentException("machine pending fuel contradicts its reserved physical preimage");
            });
        }
    }
}
