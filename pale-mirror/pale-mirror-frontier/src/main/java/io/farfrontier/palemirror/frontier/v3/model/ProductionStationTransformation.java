package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Machine-owned recipe boundary. The completion port is injected by composition, not inferred from an order. */
public final class ProductionStationTransformation {
    public static Optional<ProductionStationRecipeBoundary> next(FrontierWorldState state, SubjectId stationId) {
        var machine = state.productionStations().require(stationId);
        var record = ProductionStationEnergy.record(state, stationId); var spec = record.productionStation().orElseThrow();
        var operation = machine.operation().orElse(null);
        if (operation == null || !operation.complete() || operation.transformed() || machine.pendingFuel().isPresent()
                || machine.pendingRecipe().isPresent() || !(operation.inputCustody() instanceof ProductionOperation.FungibleInput input)
                || ContainerPhysicalAuthorityComposition.pending(state, record.id())
                || ReferenceContainerCustody.blocksCanonicalUse(state, record.id())) return Optional.empty();
        if (!ProductionStationRecipe.portVacant(state, spec, ActorContainerItemOrder.StationPort.OUTPUT)) return Optional.empty();
        var ledger = state.inventory().fungibleResources();
        boolean hot = ReferenceContainerCustody.hasLiveCustody(state, record.id());
        if (hot && !ReferenceContainerCustody.hasOperationalCustody(state, record.id())) return Optional.empty();
        long epoch = 0;
        var before = new ArrayList<FungiblePhysicalObservation.Stack>(); var after = new ArrayList<FungiblePhysicalObservation.Stack>();
        if (hot) {
            if (!FungibleResourceCustodySupport.hasCurrentPhysicalPortion(state, input.accountId(), input.lots()))
                return Optional.empty();
            var bindings = ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(input.accountId()))
                    .sorted(Comparator.comparing(binding -> binding.address().toString())).toList();
            var sourceAddress = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(record.id(), spec.inputSlot()));
            var source = bindings.stream().filter(binding -> binding.address().equals(sourceAddress)).findFirst().orElseThrow(
                    () -> new IllegalArgumentException("completed station operation lost its physical input port"));
            epoch = source.authorityEpoch();
            for (var binding : bindings) {
                if (binding.authorityEpoch() != epoch) throw new IllegalArgumentException("station recipe has mixed authority epochs");
                before.add(new FungiblePhysicalObservation.Stack(binding.address(), binding.itemKind(), binding.quantity()));
                int remaining = binding.quantity() - (binding.address().equals(sourceAddress) ? operation.inputQuantity() : 0);
                if (remaining < 0) throw new IllegalArgumentException("completed station operation lost its input quantity");
                if (remaining > 0) after.add(new FungiblePhysicalObservation.Stack(binding.address(), binding.itemKind(), remaining));
            }
            var recipe = state.bootstrap().ruleset().production().require(operation.recipeId());
            after.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                    new InventoryCustody.ContainerSlot(record.id(), spec.outputSlot())), recipe.product().itemKind(), operation.outputQuantity()));
        }
        return Optional.of(new ProductionStationRecipeBoundary(stationId, record.id(), operation.id(), machine.revision(), epoch, before, after));
    }
    public static boolean pendingForContainer(FrontierWorldState state, SubjectId container) {
        return state.productionStations().machines().values().stream().flatMap(machine -> machine.pendingRecipe().stream())
                .anyMatch(boundary -> boundary.containerId().equals(container));
    }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, ProductionStationRecipeChanged event,
                                          ProductionOperationCompletionPort completion) {
        var boundary = event.boundary(); var machine = state.productionStations().require(subject);
        if (!subject.equals(boundary.stationId()) || machine.revision() != boundary.machineRevision()
                || !machine.operation().map(ProductionOperation::id).equals(Optional.of(boundary.operationId()))
                || !ProductionStationEnergy.record(state, subject).id().equals(boundary.containerId()))
            throw new IllegalArgumentException("station recipe receipt has stale instance, operation or revision");
        if (event.stage() == ProductionStationRecipeChanged.Stage.HOT_PREPARED) {
            if (!next(state, subject).equals(Optional.of(boundary))) throw new IllegalArgumentException("station recipe preparation lost its exact preimage");
            return state.withChanges(FrontierWorldStateUpdate.begin().productionStations(state.productionStations().with(machine.prepareRecipe(boundary))));
        }
        var operation = machine.operation().orElseThrow();
        var input = (ProductionOperation.FungibleInput) operation.inputCustody();
        var recipe = state.bootstrap().ruleset().production().require(operation.recipeId());
        var ledger = state.inventory().fungibleResources();
        var owner = ledger.lots().get(input.lots().keySet().iterator().next()).economicOwnerId();
        var output = new ResourceLot(operation.outputId(), owner, recipe.product().itemKind(), operation.outputQuantity(),
                "recipe:" + recipe.id().value(), input.lots().keySet().stream().sorted().toList());
        var station = ProductionStationEnergy.record(state, subject).productionStation().orElseThrow();
        ExactInventory inventory;
        if (event.stage() == ProductionStationRecipeChanged.Stage.COLD_COMPLETED) {
            if (!next(state, subject).equals(Optional.of(boundary))) throw new IllegalArgumentException("station COLD recipe lost exclusive current input");
            inventory = ProductionStationRecipe.transformCold(state, station, operation.recipeId(), input.accountId(), input.lots(), input.claims(), output);
        } else {
            if (!machine.pendingRecipe().equals(Optional.of(boundary)) || event.observed().size() != boundary.after().size()
                    || !new HashSet<>(event.observed()).equals(new HashSet<>(boundary.after())))
                throw new IllegalArgumentException("station physical recipe lacks its exact retained postimage");
            inventory = ProductionStationRecipe.transformObserved(state, station, operation.recipeId(), input.accountId(), boundary.authorityEpoch(),
                    input.lots(), input.claims(), output, event.observed());
        }
        inventory = ProductionStationProcessing.reserveOutput(inventory, operation, boundary.authorityEpoch());
        var finished = machine.completeRecipe(operation);
        if (!(operation.initiator() instanceof ProductionOperation.OrderInitiator)) finished = finished.release(operation.id());
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory)
                .productionStations(state.productionStations().with(finished)).merge(completion.completed(state, operation)));
    }
    private ProductionStationTransformation() { }
}
