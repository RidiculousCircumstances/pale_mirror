package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Frozen machine effect boundary; inventory coordinates are evidence, not resource identity. */
public record ProductionStationRecipeBoundary(SubjectId stationId, SubjectId containerId,
        ProductionOperation.Id operationId, long machineRevision, long authorityEpoch,
        List<FungiblePhysicalObservation.Stack> before, List<FungiblePhysicalObservation.Stack> after) {
    public ProductionStationRecipeBoundary {
        Objects.requireNonNull(stationId); Objects.requireNonNull(containerId); Objects.requireNonNull(operationId);
        before = List.copyOf(before); after = List.copyOf(after);
        if (machineRevision < 1 || authorityEpoch < 0 || before.size() > 3 || after.size() > 3
                || authorityEpoch == 0 && (!before.isEmpty() || !after.isEmpty())
                || authorityEpoch > 0 && (before.isEmpty() || after.isEmpty())
                || before.stream().map(FungiblePhysicalObservation.Stack::address).distinct().count() != before.size()
                || after.stream().map(FungiblePhysicalObservation.Stack::address).distinct().count() != after.size())
            throw new IllegalArgumentException("invalid bounded station recipe pre/postimage");
    }
    /** Recovery revalidates the frozen effect against current bindings and the exact declared recipe. */
    void validate(ExactInventory inventory, ProductionStationSpec station, ProductionOperation operation, ProductionCatalog catalog) {
        if (authorityEpoch < 1 || !stationId.equals(station.id()) || !containerId.equals(station.containerId())
                || !operationId.equals(operation.id()) || !operation.complete() || operation.transformed()
                || !(operation.inputCustody() instanceof ProductionOperation.FungibleInput input))
            throw new IllegalArgumentException("prepared recipe lost its exact installed instance or input allocation");
        var bindings = inventory.fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(input.accountId())).toList();
        var actual = new HashSet<FungiblePhysicalObservation.Stack>();
        var expected = new HashSet<FungiblePhysicalObservation.Stack>();
        var inputAddress = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(containerId, station.inputSlot()));
        boolean found = false;
        for (var binding : bindings) {
            if (binding.authorityEpoch() != authorityEpoch || !(binding.address() instanceof PhysicalStackAddress.ContainerSlot slot)
                    || !slot.slot().containerId().equals(containerId) || slot.slot().slot() == station.outputSlot())
                throw new IllegalArgumentException("prepared recipe has a foreign physical port or epoch");
            actual.add(new FungiblePhysicalObservation.Stack(binding.address(), binding.itemKind(), binding.quantity()));
            int remaining = binding.quantity();
            if (binding.address().equals(inputAddress)) {
                found = true; remaining -= operation.inputQuantity();
                if (remaining < 0 || !binding.claimQuantities().equals(input.claims())
                        || input.lots().entrySet().stream().anyMatch(entry -> binding.lotQuantities().getOrDefault(entry.getKey(), 0) < entry.getValue()))
                    throw new IllegalArgumentException("prepared recipe lost its reserved input port");
            } else if (slot.slot().slot() != station.fuelSlot()) {
                throw new IllegalArgumentException("prepared recipe contains an undeclared port");
            }
            if (remaining > 0) expected.add(new FungiblePhysicalObservation.Stack(binding.address(), binding.itemKind(), remaining));
        }
        var recipe = catalog.require(operation.recipeId());
        expected.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(containerId, station.outputSlot())), recipe.product().itemKind(), operation.outputQuantity()));
        if (!found || !actual.equals(new HashSet<>(before)) || !expected.equals(new HashSet<>(after)))
            throw new IllegalArgumentException("prepared recipe pre/postimage differs from its frozen operation");
    }
}
