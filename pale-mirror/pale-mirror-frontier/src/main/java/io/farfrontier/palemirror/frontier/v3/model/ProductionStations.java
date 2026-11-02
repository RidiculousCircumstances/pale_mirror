package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.LinkedHashMap;

/** Installed-machine runtime, independent of orders, residents and physical inventory copies. */
public record ProductionStations(Map<SubjectId, Machine> machines) {
    public record Machine(SubjectId stationId, Optional<FuelCombustion> combustion, Optional<ProductionOperation> operation,
                          long revision, Optional<ProductionStationFuelBurn> pendingFuel,
                          Optional<ProductionStationRecipeBoundary> pendingRecipe) {
        public Machine {
            Objects.requireNonNull(stationId); combustion = Objects.requireNonNull(combustion); operation = Objects.requireNonNull(operation); pendingFuel = Objects.requireNonNull(pendingFuel);
            pendingRecipe = Objects.requireNonNull(pendingRecipe);
            var retainedOperation = operation;
            if (revision < 1 || operation.filter(value -> !value.stationId().equals(stationId)).isPresent()
                    || pendingFuel.filter(value -> !value.stationId().equals(stationId) || value.machineRevision() != revision).isPresent()
                    || pendingRecipe.filter(value -> !value.stationId().equals(stationId) || value.machineRevision() != revision
                        || retainedOperation.isEmpty() || !retainedOperation.orElseThrow().id().equals(value.operationId())
                        || !retainedOperation.orElseThrow().complete() || retainedOperation.orElseThrow().transformed()).isPresent()
                    || pendingFuel.isPresent() && pendingRecipe.isPresent())
                throw new IllegalArgumentException("invalid machine identity or energy");
        }
        public Machine(SubjectId stationId, Optional<FuelCombustion> combustion, Optional<ProductionOperation> operation,
                       long revision, Optional<ProductionStationFuelBurn> pendingFuel) {
            this(stationId, combustion, operation, revision, pendingFuel, Optional.empty());
        }
        public static Machine installed(SubjectId stationId) { return new Machine(stationId, Optional.empty(), Optional.empty(), 1, Optional.empty()); }
        public Machine start(ProductionOperation next) {
            if (operation.isPresent() || pendingFuel.isPresent() || pendingRecipe.isPresent() || !next.stationId().equals(stationId))
                throw new IllegalArgumentException("machine cannot start a competing or unfuelled operation");
            return new Machine(stationId, combustion, Optional.of(next), Math.addExact(revision, 1), Optional.empty());
        }
        public Machine replace(ProductionOperation previous, ProductionOperation next) {
            if (pendingFuel.isPresent() || pendingRecipe.isPresent() || !operation.equals(Optional.of(previous)) || !previous.id().equals(next.id()))
                throw new IllegalArgumentException("machine update has stale operation evidence");
            return new Machine(stationId, combustion, Optional.of(next), Math.addExact(revision, 1), Optional.empty());
        }
        /** Attendance is not the physical effect's preimage. Only settle old work and revoke attendance;
         * retain the pinned effect revision and exact fuel/recipe receipt through handoff/restart. */
        Machine suspendAttendance(ProductionOperation previous, WorkProgress paused) {
            if (!operation.equals(Optional.of(previous)) || previous.transformed()
                    || previous.progress().isEmpty()
                    || !previous.progress().orElseThrow().pause(paused.evaluatedAtTick()).equals(paused))
                throw new IllegalArgumentException("attendance suspension has stale work or awards new labour");
            var next = previous.withAttendance(paused, Optional.empty());
            if (pendingFuel.isEmpty() && pendingRecipe.isEmpty()) return replace(previous, next);
            return new Machine(stationId, combustion, Optional.of(next), revision, pendingFuel, pendingRecipe);
        }
        public Machine release(ProductionOperation.Id id) {
            if (pendingFuel.isPresent() || pendingRecipe.isPresent() || !operation.orElseThrow().id().equals(id) || !operation.orElseThrow().transformed())
                throw new IllegalArgumentException("machine release lacks confirmed output removal");
            return new Machine(stationId, combustion, Optional.empty(), Math.addExact(revision, 1), Optional.empty());
        }
        /** A claim-owner cancellation abandons processing, never restores spent fuel or creates output. */
        Machine cancel(ProductionOperation.Id id) {
            if (pendingFuel.isPresent() || pendingRecipe.isPresent() || !operation.orElseThrow().id().equals(id))
                throw new IllegalArgumentException("machine cancellation has no exact uncommitted operation");
            return new Machine(stationId, combustion, Optional.empty(), Math.addExact(revision, 1), Optional.empty());
        }
        Machine prepareRecipe(ProductionStationRecipeBoundary boundary) {
            if (pendingFuel.isPresent() || pendingRecipe.isPresent() || operation.isEmpty() || !operation.orElseThrow().complete()
                    || operation.orElseThrow().transformed() || !operation.orElseThrow().id().equals(boundary.operationId())
                    || boundary.machineRevision() != revision || !boundary.stationId().equals(stationId))
                throw new IllegalArgumentException("station recipe preparation lost its exact completed operation");
            return new Machine(stationId, combustion, operation, revision, Optional.empty(), Optional.of(boundary));
        }
        Machine completeRecipe(ProductionOperation previous) {
            if (!operation.equals(Optional.of(previous)) || pendingFuel.isPresent())
                throw new IllegalArgumentException("station recipe completion has no exact current operation");
            var next = previous.confirmTransformation();
            return new Machine(stationId, combustion, Optional.of(next), Math.addExact(revision, 1), Optional.empty());
        }
        /** Called only by a confirmed fuel-consumption receipt, not by inventory inspection. */
        Machine ignited(ProductionCatalog.Fuel fuel, long tick) {
            if (pendingRecipe.isPresent() || combustion.filter(value -> value.remainingAt(tick) > 0).isPresent())
                throw new IllegalArgumentException("ignition cannot replace burning fuel or a pending recipe");
            return new Machine(stationId, Optional.of(FuelCombustion.ignite(fuel, tick)), operation,
                    Math.addExact(revision, 1), Optional.empty());
        }
        public long burnRemaining(long tick) { return combustion.map(value -> value.remainingAt(tick)).orElse(0L); }
        Machine prepareFuel(ProductionStationFuelBurn burn) {
            if (pendingFuel.isPresent() || pendingRecipe.isPresent() || operation.isEmpty() || burn.machineRevision() != revision || !burn.stationId().equals(stationId))
                throw new IllegalArgumentException("station fuel preparation has competing work or stale evidence");
            return new Machine(stationId, combustion, operation, revision, Optional.of(burn));
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
    public void validate(ExactInventory inventory, ProductionCatalog catalog,
                         io.farfrontier.palemirror.frontier.v3.model.tooling.ToolCatalog tools) {
        REFERENCES.validate(catalog, machines, new ValidationInputs(inventory, catalog, tools));
    }
    private record ValidationInputs(ExactInventory inventory, ProductionCatalog catalog,
            io.farfrontier.palemirror.frontier.v3.model.tooling.ToolCatalog tools) { }
    private enum Input { CURRENT }
    private static final ImmutableReferenceIndex.Source<ValidationInputs, SubjectId, ContainerRecord> STATIONS =
            new ImmutableReferenceIndex.Source<>(value -> ProductionStationDirectory.declarations(value.inventory()));
    // Active effects deliberately retain the complete inventory evidence. An idle
    // machine reads only its installation, never every unrelated carried item.
    private static final ImmutableReferenceIndex.Source<ValidationInputs, Input, ExactInventory> MATERIALS =
            new ImmutableReferenceIndex.Source<>(value -> Map.of(Input.CURRENT, value.inventory()));
    private static final ImmutableReferenceIndex.Source<ValidationInputs, Input, io.farfrontier.palemirror.frontier.v3.model.tooling.ToolCatalog> TOOLS =
            new ImmutableReferenceIndex.Source<>(value -> Map.of(Input.CURRENT, value.tools()));
    private static final ImmutableReferenceIndex<ValidationInputs, SubjectId, Machine> REFERENCES =
            new ImmutableReferenceIndex<>(4_096, 3, java.util.List.of(STATIONS, MATERIALS, TOOLS), (inputs, machine) -> {
                var reads = new java.util.ArrayList<ImmutableReferenceIndex.Read<ValidationInputs>>();
                reads.add(STATIONS.at(machine.stationId()));
                if (machine.operation().isPresent() || machine.pendingFuel().isPresent() || machine.pendingRecipe().isPresent())
                    reads.add(MATERIALS.at(Input.CURRENT));
                if (machine.pendingRecipe().isPresent()) reads.add(TOOLS.at(Input.CURRENT));
                return java.util.List.copyOf(reads);
            }, (inputs, id, machine) -> validateMachine(machine, inputs.inventory(), inputs.catalog(), inputs.tools()));
    private static void validateMachine(Machine machine, ExactInventory inventory, ProductionCatalog catalog,
            io.farfrontier.palemirror.frontier.v3.model.tooling.ToolCatalog tools) {
        var station = ProductionStationDirectory.require(inventory, machine.stationId()).productionStation().orElseThrow();
        var equipment = catalog.requireEquipment(station.equipmentType());
        if (!equipment.capabilities().contains(station.capability()))
            throw new IllegalArgumentException("installed station capability differs from its declared equipment type");
        machine.combustion().ifPresent(burn -> {
            if (station.fuelSlot().isEmpty() || burn.endsAtTick() - burn.startedAtTick() != catalog.requireFuel(burn.itemKind()).burnTicks())
                throw new IllegalArgumentException("combustion lost its declared fuel duration or machine port");
        });
        machine.operation().ifPresent(operation -> {
            var recipe = catalog.require(operation.recipeId());
            equipment.require(recipe);
            if (operation.initiator() instanceof ProductionOperation.SettlementInitiator institution) {
                var record = inventory.containers().get(station.containerId());
                if (!institution.settlementId().equals(record.ownerId()) || !operation.orderId().equals(operation.id().value())
                        || catalog.automaticPolicies().stream().noneMatch(policy -> policy.recipeId().equals(recipe.id())))
                    throw new IllegalArgumentException("automatic station operation lost its explicit institution mandate");
            }
            if (recipe.capability() != station.capability() || recipe.outputForInput(operation.inputQuantity()) != operation.outputQuantity())
                throw new IllegalArgumentException("machine operation differs from its declared recipe");
            if (!operation.transformed()) {
                ProductionOperationInputs.validate(inventory, station, operation, recipe);
            } else if (operation.initiator() instanceof ProductionOperation.OrderInitiator order
                    && recipe.outcome() instanceof ProductionRecipeOutcome.Fungible
                    && operation.inputCustody() instanceof ProductionOperation.FungibleInput input) {
                var ledger = inventory.fungibleResources(); var claim = ledger.claims().get(operation.outputClaimId());
                var account = FungibleResourceReadIndex.container(ledger, station.containerId(), ContainerSectionId.OUTPUT).orElse(null);
                if (claim == null || claim.purpose() != ClaimPurpose.STATION_OUTPUT || !claim.claimantId().equals(order.orderId())
                        || !claim.lotQuantities().equals(Map.of(operation.outputId(), operation.outputQuantity()))
                        || account == null || account.claimQuantities().getOrDefault(claim.id(), 0) != operation.outputQuantity())
                    throw new IllegalArgumentException("transformed station operation lost its reserved collection output");
            }
        });
        machine.pendingRecipe().ifPresent(boundary -> boundary.validate(inventory, station, machine.operation().orElseThrow(), catalog, tools));
        machine.pendingFuel().ifPresent(burn -> {
            if (!burn.containerId().equals(station.containerId()) || burn.authorityEpoch() < 1
                    || burn.durationTicks() != catalog.requireFuel(burn.itemKind()).burnTicks()
                    || !burn.source().getFirst().address().equals(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(station.containerId(), station.fuelSlot().orElseThrow()))))
                throw new IllegalArgumentException("machine lost its exact pending fuel port/definition");
            var ledger = inventory.fungibleResources(); var account = ledger.accounts().get(burn.accountId());
            var source = burn.source().getFirst();
            var binding = ledger.bindings().values().stream().filter(value -> value.address().equals(source.address())).findFirst().orElseThrow(
                    () -> new IllegalArgumentException("machine pending fuel lost its physical preimage"));
            if (account == null || !account.custody().equals(station.custody(ActorContainerItemOrder.StationPort.FUEL))
                    || burn.lots().entrySet().stream().anyMatch(entry -> account.lotQuantities().getOrDefault(entry.getKey(), 0) < entry.getValue())
                    || !binding.accountId().equals(account.id()) || binding.authorityEpoch() != burn.authorityEpoch()
                    || binding.quantity() != source.before() || !binding.itemKind().equals(burn.itemKind()))
                throw new IllegalArgumentException("machine pending fuel contradicts its reserved physical preimage");
        });
    }
}
