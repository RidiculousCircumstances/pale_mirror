package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Frozen machine effect boundary; inventory coordinates are evidence, not resource identity. */
public record ProductionStationRecipeBoundary(SubjectId stationId, SubjectId containerId,
        ProductionOperation.Id operationId, long machineRevision, long authorityEpoch,
        List<FungiblePhysicalObservation.Stack> before, List<FungiblePhysicalObservation.Stack> after,
        Optional<ExactItemStack> exactBefore, Optional<ExactItemStack> exactAfter) {
    public ProductionStationRecipeBoundary(SubjectId stationId, SubjectId containerId,
            ProductionOperation.Id operationId, long machineRevision, long authorityEpoch,
            List<FungiblePhysicalObservation.Stack> before, List<FungiblePhysicalObservation.Stack> after) {
        this(stationId, containerId, operationId, machineRevision, authorityEpoch, before, after, Optional.empty(), Optional.empty());
    }
    public ProductionStationRecipeBoundary {
        Objects.requireNonNull(stationId); Objects.requireNonNull(containerId); Objects.requireNonNull(operationId);
        before = List.copyOf(before); after = List.copyOf(after);
        Objects.requireNonNull(exactBefore); Objects.requireNonNull(exactAfter);
        if (machineRevision < 1 || authorityEpoch < 0 || before.size() > 12 || after.size() > 12
                || authorityEpoch == 0 && (!before.isEmpty() || !after.isEmpty())
                || authorityEpoch > 0 && (before.isEmpty() || after.isEmpty() && exactAfter.isEmpty())
                || exactBefore.isPresent() && (exactAfter.isEmpty() || !exactBefore.orElseThrow().id().equals(exactAfter.orElseThrow().id()))
                || before.stream().map(FungiblePhysicalObservation.Stack::address).distinct().count() != before.size()
                || after.stream().map(FungiblePhysicalObservation.Stack::address).distinct().count() != after.size())
            throw new IllegalArgumentException("invalid bounded station recipe pre/postimage");
    }
    /** Recovery revalidates the frozen effect against current bindings and the exact declared recipe. */
    void validate(ExactInventory inventory, ProductionStationSpec station, ProductionOperation operation, ProductionCatalog catalog,
                  io.farfrontier.palemirror.frontier.v3.model.tooling.ToolCatalog tools) {
        if (authorityEpoch < 1 || !stationId.equals(station.id()) || !containerId.equals(station.containerId())
                || !operationId.equals(operation.id()) || !operation.complete() || operation.transformed()
                || operation.inputCustody().materials().isEmpty())
            throw new IllegalArgumentException("prepared recipe lost its exact installed instance or input allocation");
        var input = operation.inputCustody().materials().orElseThrow();
        var recipe = catalog.require(operation.recipeId());
        ProductionOperationInputs.validate(inventory, station, operation, recipe);
        var image = ProductionStationMaterialImage.of(inventory.fungibleResources(), station, input);
        if (image.epoch() != authorityEpoch) throw new IllegalArgumentException("prepared recipe epoch changed");
        var actual = new HashSet<>(image.before());
        var expected = new HashSet<>(image.remaining());
        if (recipe.outcome() instanceof ProductionRecipeOutcome.Fungible) {
            expected.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                    new InventoryCustody.ContainerSlot(containerId, station.outputSlot())), recipe.product().itemKind(), operation.outputQuantity()));
            if (exactBefore.isPresent() || exactAfter.isPresent()) throw new IllegalArgumentException("resource recipe cannot mutate equipment");
        } else {
            var owner = inventory.fungibleResources().lots().get(input.lots().keySet().iterator().next()).economicOwnerId();
            var result = ProductionIndividualResult.resolve(recipe, tools, operation.outputId(), owner,
                    new InventoryCustody.ContainerSlot(containerId, station.outputSlot()), operation.inputCustody().maintenanceTarget());
            if (!exactBefore.equals(operation.inputCustody().maintenanceTarget()) || !exactAfter.equals(Optional.of(result)))
                throw new IllegalArgumentException("prepared recipe has a foreign individual result");
        }
        if (!actual.equals(new HashSet<>(before)) || !expected.equals(new HashSet<>(after)))
            throw new IllegalArgumentException("prepared recipe pre/postimage differs from its frozen operation");
    }
}
