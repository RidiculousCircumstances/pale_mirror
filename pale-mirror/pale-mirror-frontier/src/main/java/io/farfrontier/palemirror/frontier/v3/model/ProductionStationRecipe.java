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
        for (int slot : station.slots(port)) if (!state.inventory().slotVacant(new InventoryCustody.ContainerSlot(station.containerId(), slot))) return false;
        return FungibleResourceCustodySupport.accountAtContainer(state, station.containerId(), station.section(port))
                .map(account -> account.lotQuantities().isEmpty()).orElse(true);
    }

    /** Validates a producer-declared compartment; it never chooses a compartment from an item kind. */
    public static boolean accepts(ProductionCatalog catalog, ProductionStationSpec station, ContainerSectionId section, String kind) {
        if (!station.compartmentLayout().sections().containsKey(section)) return false;
        var supported = catalog.recipes().stream().filter(recipe -> recipe.capability() == station.capability()).toList();
        if (section.equals(ContainerSectionId.INPUT)) return supported.stream().anyMatch(recipe -> recipe.acceptsInput(kind));
        if (section.equals(ContainerSectionId.OUTPUT)) return supported.stream().anyMatch(recipe -> recipe.product().itemKind().equals(kind));
        if (section.equals(ContainerSectionId.FUEL)) return catalog.fuels().stream().anyMatch(fuel -> fuel.itemKind().equals(kind));
        return false;
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
        if (!(input.custody() instanceof InventoryCustody.ContainerSlot source)
                || !source.containerId().equals(station.containerId()) || !station.inputSlots().contains(source.slot()))
            throw new IllegalArgumentException("exact recipe input is outside its declared input inventory");
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
                || !(input.custody() instanceof InventoryCustody.ContainerSlot source
                    && source.containerId().equals(station.containerId()) && station.inputSlots().contains(source.slot()))
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
        return state.inventory().withFungibleResources(ledger.transformCold(accountId, inputs, claims, output,
                ContainerResourceAccounts.id(station.custody(ActorContainerItemOrder.StationPort.OUTPUT)),
                station.custody(ActorContainerItemOrder.StationPort.OUTPUT)));
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
        var materials = new ProductionOperation.FungibleInput(accountId, inputs, claims);
        var image = ProductionStationMaterialImage.of(ledger, station, materials);
        var expected = new java.util.HashSet<>(image.remaining());
        var product = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot())), output.itemKind(), output.quantity());
        expected.add(product);
        if (image.epoch() != authorityEpoch || observed.size() != expected.size() || !expected.equals(new java.util.HashSet<>(observed)))
            throw new IllegalArgumentException("observed recipe differs from its complete reserved material transformation");
        var reduced = ledger.destroyObserved(accountId, authorityEpoch, inputs, claims, image.remaining());
        var holder = station.custody(ActorContainerItemOrder.StationPort.OUTPUT); var destination = ContainerResourceAccounts.id(holder);
        var transformed = reduced.issue(output, new CustodyAccount(destination, holder, Map.of(output.id(), output.quantity()), Map.of()));
        return state.inventory().withFungibleResources(transformed.rebind(destination, authorityEpoch,
                FungiblePhysicalObservation.bind(transformed, destination, authorityEpoch, List.of(product))));
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
                || account == null || !account.custody().equals(station.custody(ActorContainerItemOrder.StationPort.INPUT))
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
        {
            var operation = state.productionStations().require(station.id()).operation().orElseThrow();
            int inputQuantity = inputs.values().stream().reduce(0, Math::addExact);
            if (!operation.recipeId().equals(recipeId) || operation.transformed() || !operation.complete()
                    || operation.inputQuantity() != inputQuantity || operation.outputQuantity() != outputQuantity)
                throw new IllegalArgumentException("recipe requires its exact completed station-owned processing operation");
        }
    }
}
