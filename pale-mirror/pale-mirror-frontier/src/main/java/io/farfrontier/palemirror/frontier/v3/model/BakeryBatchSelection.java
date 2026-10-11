package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Comparator;
import java.util.Optional;

/** Recipe-owned admission proposal. Claims and physical custody remain owned by their existing services. */
public record BakeryBatchSelection(Optional<ExactItemStack> exactInput,
                                   Optional<FungibleResourceCustodySupport.LotSelection> fungibleInput) {
    public BakeryBatchSelection {
        java.util.Objects.requireNonNull(exactInput, "exact bakery input");
        java.util.Objects.requireNonNull(fungibleInput, "fungible bakery input");
        if (exactInput.isPresent() == fungibleInput.isPresent())
            throw new IllegalArgumentException("bakery batch must declare exactly one input representation");
    }

    public int quantity() {
        return exactInput.map(ExactItemStack::count).orElseGet(() -> fungibleInput.orElseThrow().quantity());
    }
    public SubjectId sourceContainer(FrontierWorldState state) {
        if (exactInput.isPresent()) return ((InventoryCustody.ContainerSlot) exactInput.orElseThrow().custody()).containerId();
        return ((ResourceCustody.Container) state.inventory().fungibleResources().accounts().get(fungibleInput.orElseThrow().accountId()).custody()).containerId();
    }
    /** Diagnostic/retry discovery includes fenced stock, but never authorizes its use. */
    public static java.util.List<SubjectId> storedInputContainers(FrontierWorldState state, SubjectId home) {
        var resources = state.inventory().fungibleResources();
        return state.inventory().containers().values().stream().filter(record -> record.ownerId().equals(home)
                && record.storage().map(policy -> policy.role() == ResourceStorageRole.WAREHOUSE && policy.filter().accepts("minecraft:wheat")).orElse(false))
                .filter(record -> state.inventory().items().values().stream().anyMatch(item -> item.economicOwnerId().equals(home)
                        && item.itemKind().equals("minecraft:wheat") && item.custody() instanceof InventoryCustody.ContainerSlot slot
                        && slot.containerId().equals(record.id()))
                    || FungibleResourceCustodySupport.accountAtContainer(state, record.id()).map(account -> account.lotQuantities().keySet().stream()
                        .anyMatch(id -> resources.lots().get(id).economicOwnerId().equals(home) && resources.lots().get(id).itemKind().equals("minecraft:wheat"))).orElse(false))
                .map(ContainerRecord::id).sorted().toList();
    }

    public static Optional<BakeryBatchSelection> available(FrontierWorldState state, SubjectId settlementId) {
        return select(state, ProductionRights.publicService(settlementId), (source, quantity) -> true);
    }

    public static Optional<BakeryBatchSelection> admissible(FrontierWorldState state, SubjectId settlementId) {
        return admissible(state, ProductionRights.publicService(settlementId));
    }
    public static Optional<BakeryBatchSelection> admissible(FrontierWorldState state, ProductionRights rights) {
        // An accepted batch finishes normally; this policy gates only new public batches.
        if (rights.mode() == ProductionRights.Mode.PUBLIC_PRODUCTION
                && !ProductionStockDemand.read(state, rights.resourceOwner().id(), SettlementFoodPolicy.BREAD).requested())
            return Optional.empty();
        return select(state, rights, (source, quantity) -> ProductionOutputCapacity.selectDestination(state, rights, quantity, source).isPresent());
    }

    private static Optional<BakeryBatchSelection> select(FrontierWorldState state, ProductionRights rights,
                                                         java.util.function.BiPredicate<SubjectId, Integer> capacity) {
        var origin = ResourceStorageAccess.at(state, rights.destinationContainerId()).endpoint().station();
        for (var storage : ResourceStorageSelection.candidates(state, rights.employer().settlementId(), "minecraft:wheat",
                origin, java.util.Set.of(ResourceStorageRole.WAREHOUSE))) {
            var home = FrontierWorldStateSupport.settlement(state.bootstrap(), rights.employer().settlementId());
            var workshop = home.structures().stream().filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow();
            if (!ResourceStorageNavigation.reachable(state,
                    SettlementWorkshopServicePort.forWorkshop(workshop).exteriorApproach(), storage)) continue;
            var choice = selectAt(state, rights, storage.containerId(), capacity);
            if (choice.isPresent()) return choice;
        }
        return Optional.empty();
    }
    private static Optional<BakeryBatchSelection> selectAt(FrontierWorldState state, ProductionRights rights,
            SubjectId depot, java.util.function.BiPredicate<SubjectId, Integer> capacity) {
        SubjectId resourceOwner = rights.resourceOwner().id();
        var exact = state.inventory().items().values().stream()
                .filter(item -> item.itemKind().equals("minecraft:wheat") && item.economicOwnerId().equals(resourceOwner)
                        && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot)
                        && !ResourceSiteHarvestLineage.hasPendingOutputReceipt(state.resourceSites().sites().values(), item.id())
                        && capacity.test(depot, item.count()))
                .sorted(Comparator.comparingInt(ExactItemStack::count).reversed().thenComparing(ExactItemStack::id))
                .findFirst().map(item -> new BakeryBatchSelection(Optional.of(item), Optional.empty()));
        if (ReferenceContainerCustody.hasLiveCustody(state, depot) && exact.isPresent()) return exact;
        var account = FungibleResourceCustodySupport.accountAtContainer(state, depot);
        int maximum = account.map(value -> Math.min(64, state.inventory().fungibleResources()
                .unclaimedQuantity(value.id(), resourceOwner, "minecraft:wheat"))).orElse(0);
        // A bounded stack-sized search also handles a nearly full output container.
        for (int quantity = maximum; quantity >= 1; quantity--) {
            if (!capacity.test(depot, quantity)) continue;
            var input = FungibleResourceCustodySupport.selectAtContainer(state, depot, resourceOwner, "minecraft:wheat", quantity);
            if (input.isPresent()) return Optional.of(new BakeryBatchSelection(Optional.empty(), input));
        }
        return exact;
    }
}
