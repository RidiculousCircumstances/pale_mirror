package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Station-local recipe accounting; job phase and physical intent admission remain with the owner. */
public final class ProductionStationRecipe {
    /** Current declared bread recipe is 1:1; stack transport bounds are not a minimum batch. */
    public static int breadOutputQuantity(int wheatQuantity) {
        if (wheatQuantity < 1 || wheatQuantity > 64)
            throw new IllegalArgumentException("bread recipe requires a bounded positive input quantity");
        return wheatQuantity;
    }
    private ProductionStationRecipe() { }

    /** Declared recipe port for an unbound station lot; never compact machine output into input. */
    public static int portForItemKind(ProductionStationSpec station, String itemKind) {
        Objects.requireNonNull(station, "station port capability");
        Objects.requireNonNull(itemKind, "station port item kind");
        return switch (station.capability()) {
            case BAKING -> switch (itemKind) {
                case "minecraft:wheat" -> station.inputSlot();
                case "minecraft:bread" -> station.outputSlot();
                default -> throw new IllegalArgumentException("bakery station has no declared port for " + itemKind);
            };
        };
    }

    /** Exact-stack variant of the same COLD station-local transformation. */
    public static ExactInventory transformExactCold(FrontierWorldState state, ProductionStationSpec station,
                                                    ExactItemStack input, ExactItemStack output) {
        Objects.requireNonNull(state, "exact station recipe state");
        Objects.requireNonNull(station, "exact recipe station");
        Objects.requireNonNull(input, "exact recipe input");
        Objects.requireNonNull(output, "exact recipe output");
        ContainerRecord container = state.inventory().containers().get(station.containerId());
        InventoryCustody.ContainerSlot source = new InventoryCustody.ContainerSlot(station.containerId(), station.inputSlot());
        InventoryCustody.ContainerSlot destination = new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot());
        if (container == null || !container.productionStation().equals(java.util.Optional.of(station))
                || station.capability() != ProductionStationSpec.Capability.BAKING
                || !input.custody().equals(source) || !output.custody().equals(destination)
                || !"minecraft:wheat".equals(input.itemKind()) || !"minecraft:bread".equals(output.itemKind())
                || input.count() != output.count() || !input.economicOwnerId().equals(output.economicOwnerId())
                || !container.ownerId().equals(input.economicOwnerId())
                || !input.equals(state.inventory().items().get(input.id()))
                || state.inventory().items().containsKey(output.id())
                || state.inventory().itemAt(destination.containerId(), destination.slot()).isPresent()
                || state.inventory().fungibleResources().accounts().values().stream().anyMatch(account ->
                        account.custody().equals(new ResourceCustody.Container(station.containerId())))
                || ReferenceContainerCustody.hasLiveCustody(state, station.containerId())
                || ReferenceContainerCustody.blocksCanonicalUse(state, station.containerId()))
            throw new IllegalArgumentException("exact cold recipe lacks exclusive station input and free output port");
        return state.inventory().withoutItem(input.id()).store(output);
    }

    /** The physical executor has witnessed the tagged input vanish and tagged output appear at this station. */
    public static ExactInventory transformExactObserved(FrontierWorldState state, ProductionStationSpec station,
                                                        ExactItemStack input, ExactItemStack output) {
        Objects.requireNonNull(state, "observed exact station state");
        if (!(ReferenceContainerCustody.hasOperationalCustody(state, station.containerId())
                || ReferenceContainerCustody.hasLiveCustody(state, station.containerId())
                && BakeryPhysicalAuthority.pendingForContainer(state, station.containerId()))
                || ReferenceContainerCustody.blocksCanonicalUse(state, station.containerId()))
            throw new IllegalArgumentException("observed exact recipe has no current physical machine authority");
        // The cold variant's identity, port and recipe checks are repeated here without its
        // mutually exclusive no-physical-writer condition.
        ContainerRecord container = state.inventory().containers().get(station.containerId());
        if (container == null || !container.productionStation().equals(java.util.Optional.of(station))
                || station.capability() != ProductionStationSpec.Capability.BAKING
                || !input.custody().equals(new InventoryCustody.ContainerSlot(station.containerId(), station.inputSlot()))
                || !output.custody().equals(new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot()))
                || !"minecraft:wheat".equals(input.itemKind()) || !"minecraft:bread".equals(output.itemKind())
                || input.count() != output.count() || !input.economicOwnerId().equals(output.economicOwnerId())
                || !container.ownerId().equals(input.economicOwnerId())
                || !input.equals(state.inventory().items().get(input.id()))
                || state.inventory().items().containsKey(output.id())
                || !state.containerSlotAvailable(new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot())))
            throw new IllegalArgumentException("observed exact recipe has no exclusively held input and free output port");
        return state.inventory().withoutItem(input.id()).store(output);
    }

    public static ExactInventory transformCold(FrontierWorldState state, ProductionStationSpec station,
                                               SubjectId accountId, Map<SubjectId, Integer> inputs,
                                               Map<SubjectId, Integer> claims, ResourceLot output) {
        FungibleResourceLedger ledger = requireInput(state, station, accountId, inputs, claims, output);
        if (ReferenceContainerCustody.hasLiveCustody(state, station.containerId())
                || ReferenceContainerCustody.blocksCanonicalUse(state, station.containerId()))
            throw new IllegalArgumentException("cold station recipe competes with current physical machine authority");
        if (!state.inventory().slotVacant(new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot())))
            throw new IllegalArgumentException("cold station recipe has no free declared output port");
        return state.inventory().withFungibleResources(ledger.transformCold(accountId, inputs, claims, output));
    }

    public static ExactInventory transformObserved(FrontierWorldState state, ProductionStationSpec station,
                                                   SubjectId accountId, long authorityEpoch,
                                                   Map<SubjectId, Integer> inputs, Map<SubjectId, Integer> claims,
                                                   ResourceLot output, List<FungiblePhysicalObservation.Stack> observed) {
        FungibleResourceLedger ledger = requireInput(state, station, accountId, inputs, claims, output);
        if (!(ReferenceContainerCustody.hasOperationalCustody(state, station.containerId())
                || ReferenceContainerCustody.hasLiveCustody(state, station.containerId())
                && BakeryPhysicalAuthority.pendingForContainer(state, station.containerId()))
                || ReferenceContainerCustody.blocksCanonicalUse(state, station.containerId()))
            throw new IllegalArgumentException("observed station recipe has no current physical machine authority");
        InventoryCustody.ContainerSlot inputSlot = new InventoryCustody.ContainerSlot(station.containerId(), station.inputSlot());
        InventoryCustody.ContainerSlot outputSlot = new InventoryCustody.ContainerSlot(station.containerId(), station.outputSlot());
        List<PhysicalStackBinding> before = ledger.bindings().values().stream()
                .filter(binding -> binding.accountId().equals(accountId)).toList();
        if (before.size() != 1 || before.getFirst().authorityEpoch() != authorityEpoch
                || !before.getFirst().address().equals(new PhysicalStackAddress.ContainerSlot(inputSlot))
                || !before.getFirst().lotQuantities().equals(inputs)
                || !before.getFirst().claimQuantities().equals(claims)
                || observed.size() != 1
                || !observed.getFirst().address().equals(new PhysicalStackAddress.ContainerSlot(outputSlot))
                || !observed.getFirst().itemKind().equals(output.itemKind())
                || observed.getFirst().quantity() != output.quantity())
            throw new IllegalArgumentException("observed recipe must replace the exact input port with the exact output port");
        return state.inventory().withFungibleResources(
                ledger.transformObserved(accountId, authorityEpoch, inputs, claims, output, observed));
    }

    private static FungibleResourceLedger requireInput(FrontierWorldState state, ProductionStationSpec station,
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
                || !account.lotQuantities().equals(inputs) || !account.claimQuantities().equals(claims)
                || !container.ownerId().equals(output.economicOwnerId()))
            throw new IllegalArgumentException("recipe requires the current exclusively held station input");
        switch (station.capability()) {
            case BAKING -> {
                if (!"minecraft:bread".equals(output.itemKind()) || output.quantity() != breadOutputQuantity(
                        inputs.values().stream().mapToInt(Integer::intValue).sum())
                        || inputs.keySet().stream().anyMatch(id -> {
                            ResourceLot lot = ledger.lots().get(id);
                            return lot == null || !"minecraft:wheat".equals(lot.itemKind());
                        }))
                    throw new IllegalArgumentException("bakery station has no declared wheat-to-bread batch");
            }
        }
        return ledger;
    }
}
