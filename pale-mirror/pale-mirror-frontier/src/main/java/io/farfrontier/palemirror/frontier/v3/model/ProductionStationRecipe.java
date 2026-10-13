package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Station-local recipe accounting; job phase and physical intent admission remain with the owner. */
public final class ProductionStationRecipe {
    private ProductionStationRecipe() { }

    /** Container capacity is not a machine port address. Unbound/COLD stock still
     * occupies its catalog-declared port, regardless of available packing slots. */
    public static boolean portVacant(FrontierWorldState state, ProductionStationSpec station,
                                     ActorContainerItemOrder.StationPort port) {
        int slot = station.slot(port);
        if (!state.inventory().slotVacant(new InventoryCustody.ContainerSlot(station.containerId(), slot))) return false;
        var ledger = state.inventory().fungibleResources();
        return ledger.accounts().values().stream().filter(account -> account.custody().equals(
                        new ResourceCustody.Container(station.containerId())))
                .flatMap(account -> account.lotQuantities().keySet().stream())
                .noneMatch(id -> portForItemKind(state.bootstrap().ruleset().production(), station,
                        ledger.lots().get(id).itemKind()) == slot);
    }

    /** Declared recipe port for an unbound station lot; never compact machine output into input. */
    public static int portForItemKind(ProductionCatalog catalog, ProductionStationSpec station, String itemKind) {
        Objects.requireNonNull(station, "station port capability");
        Objects.requireNonNull(itemKind, "station port item kind");
        var supported = catalog.recipes().stream().filter(recipe -> recipe.capability() == station.capability()).toList();
        boolean input = supported.stream().anyMatch(recipe -> recipe.ingredient().accepts(itemKind));
        boolean output = supported.stream().anyMatch(recipe -> recipe.product().itemKind().equals(itemKind));
        boolean fuel = catalog.fuels().stream().anyMatch(value -> value.itemKind().equals(itemKind));
        if ((input ? 1 : 0) + (output ? 1 : 0) + (fuel ? 1 : 0) != 1)
            throw new IllegalArgumentException("station has no unambiguous declared port for " + itemKind);
        return input ? station.inputSlot() : output ? station.outputSlot() : station.fuelSlot();
    }

    /** Exact-stack variant of the same COLD station-local transformation. */
    public static ExactInventory transformExactCold(FrontierWorldState state, ProductionStationSpec station, ProductionRecipe.Id recipeId,
                                                    ExactItemStack input, ExactItemStack output) {
        Objects.requireNonNull(state, "exact station recipe state");
        Objects.requireNonNull(station, "exact recipe station");
        Objects.requireNonNull(input, "exact recipe input");
        Objects.requireNonNull(output, "exact recipe output");
        requireRecipe(state, station, recipeId, Map.of(input.itemKind(), input.count()), output.itemKind(), output.count());
        ContainerRecord container = state.inventory().containers().get(station.containerId());
        InventoryCustody.ContainerSlot source = new InventoryCustody.ContainerSlot(station.containerId(), station.inputSlot());
        InventoryCustody.ContainerSlot destination = new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot());
        if (container == null || !container.productionStation().equals(java.util.Optional.of(station))
                || !input.custody().equals(source) || !output.custody().equals(destination)
                || !input.economicOwnerId().equals(output.economicOwnerId())
                || !input.equals(state.inventory().items().get(input.id()))
                || state.inventory().items().containsKey(output.id())
                || state.inventory().itemAt(destination.containerId(), destination.slot()).isPresent()
                || !portVacant(state, station, ActorContainerItemOrder.StationPort.OUTPUT)
                || ReferenceContainerCustody.hasLiveCustody(state, station.containerId())
                || ReferenceContainerCustody.blocksCanonicalUse(state, station.containerId()))
            throw new IllegalArgumentException("exact cold recipe lacks exclusive station input and free output port");
        return state.inventory().withoutItem(input.id()).store(output);
    }

    /** The physical executor has witnessed the tagged input vanish and tagged output appear at this station. */
    public static ExactInventory transformExactObserved(FrontierWorldState state, ProductionStationSpec station, ProductionRecipe.Id recipeId,
                                                        ExactItemStack input, ExactItemStack output) {
        Objects.requireNonNull(state, "observed exact station state");
        requireRecipe(state, station, recipeId, Map.of(input.itemKind(), input.count()), output.itemKind(), output.count());
        if (!(ReferenceContainerCustody.hasOperationalCustody(state, station.containerId())
                || ReferenceContainerCustody.hasLiveCustody(state, station.containerId())
                && ContainerPhysicalAuthorityComposition.pending(state, station.containerId()))
                || ReferenceContainerCustody.blocksCanonicalUse(state, station.containerId()))
            throw new IllegalArgumentException("observed exact recipe has no current physical machine authority");
        // The cold variant's identity, port and recipe checks are repeated here without its
        // mutually exclusive no-physical-writer condition.
        ContainerRecord container = state.inventory().containers().get(station.containerId());
        if (container == null || !container.productionStation().equals(java.util.Optional.of(station))
                || !input.custody().equals(new InventoryCustody.ContainerSlot(station.containerId(), station.inputSlot()))
                || !output.custody().equals(new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot()))
                || !input.economicOwnerId().equals(output.economicOwnerId())
                || !input.equals(state.inventory().items().get(input.id()))
                || state.inventory().items().containsKey(output.id())
                || !state.containerSlotAvailable(new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot()))
                || !portVacant(state, station, ActorContainerItemOrder.StationPort.OUTPUT))
            throw new IllegalArgumentException("observed exact recipe has no exclusively held input and free output port");
        return state.inventory().withoutItem(input.id()).store(output);
    }

    public static ExactInventory transformCold(FrontierWorldState state, ProductionStationSpec station, ProductionRecipe.Id recipeId,
                                               SubjectId accountId, Map<SubjectId, Integer> inputs,
                                               Map<SubjectId, Integer> claims, ResourceLot output) {
        FungibleResourceLedger ledger = requireInput(state, station, recipeId, accountId, inputs, claims, output);
        if (ReferenceContainerCustody.hasLiveCustody(state, station.containerId())
                || ReferenceContainerCustody.blocksCanonicalUse(state, station.containerId()))
            throw new IllegalArgumentException("cold station recipe competes with current physical machine authority");
        if (!portVacant(state, station, ActorContainerItemOrder.StationPort.OUTPUT))
            throw new IllegalArgumentException("cold station recipe has no free declared output port");
        return state.inventory().withFungibleResources(ledger.transformCold(accountId, inputs, claims, output));
    }

    public static ExactInventory transformObserved(FrontierWorldState state, ProductionStationSpec station, ProductionRecipe.Id recipeId,
                                                   SubjectId accountId, long authorityEpoch,
                                                   Map<SubjectId, Integer> inputs, Map<SubjectId, Integer> claims,
                                                   ResourceLot output, List<FungiblePhysicalObservation.Stack> observed) {
        FungibleResourceLedger ledger = requireInput(state, station, recipeId, accountId, inputs, claims, output);
        if (!(ReferenceContainerCustody.hasOperationalCustody(state, station.containerId())
                || ReferenceContainerCustody.hasLiveCustody(state, station.containerId())
                && ContainerPhysicalAuthorityComposition.pending(state, station.containerId()))
                || ReferenceContainerCustody.blocksCanonicalUse(state, station.containerId()))
            throw new IllegalArgumentException("observed station recipe has no current physical machine authority");
        InventoryCustody.ContainerSlot inputSlot = new InventoryCustody.ContainerSlot(station.containerId(), station.inputSlot());
        InventoryCustody.ContainerSlot outputSlot = new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot());
        List<PhysicalStackBinding> before = ledger.bindings().values().stream()
                .filter(binding -> binding.accountId().equals(accountId)).toList();
        var inputBinding = before.stream().filter(binding -> binding.address().equals(
                new PhysicalStackAddress.ContainerSlot(inputSlot))).toList();
        var retained = before.stream().filter(binding -> !binding.address().equals(
                new PhysicalStackAddress.ContainerSlot(inputSlot))).toList();
        var product = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(outputSlot), output.itemKind(), output.quantity());
        int consumed = inputs.values().stream().mapToInt(Integer::intValue).sum();
        int inputRemainder = inputBinding.size() == 1 ? inputBinding.getFirst().quantity() - consumed : 0;
        var remainder = inputRemainder > 0 ? new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(inputSlot),
                inputBinding.getFirst().itemKind(), inputRemainder) : null;
        if (inputBinding.size() != 1 || before.stream().anyMatch(binding -> binding.authorityEpoch() != authorityEpoch)
                || inputs.entrySet().stream().anyMatch(entry -> inputBinding.getFirst().lotQuantities().getOrDefault(entry.getKey(), 0) < entry.getValue())
                || !inputBinding.getFirst().claimQuantities().equals(claims)
                || inputRemainder < 0 || observed.size() != retained.size() + 1 + (remainder == null ? 0 : 1)
                || !observed.contains(product) || remainder != null && !observed.contains(remainder)
                || retained.stream().anyMatch(binding -> !observed.contains(new FungiblePhysicalObservation.Stack(
                    binding.address(), binding.itemKind(), binding.quantity()))))
            throw new IllegalArgumentException("observed recipe must replace the exact input port with the exact output port");
        return state.inventory().withFungibleResources(
                ledger.transformObserved(accountId, authorityEpoch, inputs, claims, output, observed));
    }

    private static FungibleResourceLedger requireInput(FrontierWorldState state, ProductionStationSpec station, ProductionRecipe.Id recipeId,
                                                       SubjectId accountId, Map<SubjectId, Integer> inputs,
                                                       Map<SubjectId, Integer> claims, ResourceLot output) {
        Objects.requireNonNull(state, "production station state");
        Objects.requireNonNull(station, "production station");
        Objects.requireNonNull(accountId, "station resource account");
        inputs = Map.copyOf(Objects.requireNonNull(inputs, "station recipe inputs"));
        claims = Map.copyOf(Objects.requireNonNull(claims, "station recipe claims"));
        Objects.requireNonNull(output, "station recipe output");
        ContainerRecord container = state.inventory().containers().get(station.containerId());
        FungibleResourceLedger ledger = state.inventory().fungibleResources();
        CustodyAccount account = ledger.accounts().get(accountId);
        if (container == null || !container.productionStation().equals(java.util.Optional.of(station))
                || account == null || !account.custody().equals(new ResourceCustody.Container(station.containerId()))
                || inputs.entrySet().stream().anyMatch(entry -> account.lotQuantities().getOrDefault(entry.getKey(), 0) < entry.getValue())
                || claims.entrySet().stream().anyMatch(entry -> account.claimQuantities().getOrDefault(entry.getKey(), 0) != entry.getValue())
                || inputs.keySet().stream().anyMatch(id -> ledger.lots().get(id) == null
                    || !ledger.lots().get(id).economicOwnerId().equals(output.economicOwnerId())))
            throw new IllegalArgumentException("recipe requires the current exclusively held station input");
        Map<String, Integer> kinds = new java.util.LinkedHashMap<>();
        inputs.forEach((id, quantity) -> kinds.merge(ledger.lots().get(id).itemKind(), quantity, Math::addExact));
        requireRecipe(state, station, recipeId, kinds, output.itemKind(), output.quantity());
        var machine = state.productionStations().require(station.id());
        if (machine.operation().isPresent()) {
            var operation = machine.operation().orElseThrow();
            if (!operation.inputCustody().equals(new ProductionOperation.FungibleInput(accountId, inputs, claims))
                    || !operation.outputId().equals(output.id()))
                throw new IllegalArgumentException("recipe must consume the exact operation allocation and produce its retained output identity");
        }
        return ledger;
    }

    private static void requireRecipe(FrontierWorldState state, ProductionStationSpec station, ProductionRecipe.Id recipeId,
                                      Map<String, Integer> inputs, String outputKind, int outputQuantity) {
        var recipe = state.bootstrap().ruleset().production().require(recipeId);
        recipe.requireTransformation(station.capability(), inputs, outputKind, outputQuantity);
        state.bootstrap().ruleset().production().requireEquipment(station.equipmentType()).require(recipe);
        if (recipe.heatPerBatch() != 0 || recipe.processingMode() != ProductionRecipe.ProcessingMode.ATTENDED) {
            var operation = state.productionStations().require(station.id()).operation().orElseThrow();
            int inputQuantity = inputs.values().stream().reduce(0, Math::addExact);
            if (!operation.recipeId().equals(recipeId) || operation.transformed() || !operation.complete()
                    || operation.inputQuantity() != inputQuantity || operation.outputQuantity() != outputQuantity
                    || operation.committedHeat() != Math.multiplyExact(recipe.heatPerBatch(), inputQuantity / recipe.ingredient().quantity()))
                throw new IllegalArgumentException("recipe requires its exact completed station-owned processing operation");
        }
    }
}
