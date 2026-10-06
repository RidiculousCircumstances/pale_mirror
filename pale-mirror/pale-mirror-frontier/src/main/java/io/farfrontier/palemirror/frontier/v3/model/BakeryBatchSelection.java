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

    public static Optional<BakeryBatchSelection> available(FrontierWorldState state, SubjectId settlementId) {
        return select(state, ProductionRights.publicService(settlementId), quantity -> true);
    }

    public static Optional<BakeryBatchSelection> admissible(FrontierWorldState state, SubjectId settlementId) {
        return admissible(state, ProductionRights.publicService(settlementId));
    }
    public static Optional<BakeryBatchSelection> admissible(FrontierWorldState state, ProductionRights rights) {
        return select(state, rights, quantity -> ProductionOutputCapacity.canAdmitBreadBatch(state, rights, quantity));
    }

    private static Optional<BakeryBatchSelection> select(FrontierWorldState state, ProductionRights rights,
                                                         java.util.function.IntPredicate capacity) {
        SubjectId depot = rights.destinationContainerId();
        SubjectId resourceOwner = rights.resourceOwner().id();
        var exact = state.inventory().items().values().stream()
                .filter(item -> item.itemKind().equals("minecraft:wheat") && item.economicOwnerId().equals(resourceOwner)
                        && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot)
                        && !ResourceSiteHarvestLineage.hasPendingOutputReceipt(state.resourceSites().sites().values(), item.id())
                        && capacity.test(item.count()))
                .sorted(Comparator.comparingInt(ExactItemStack::count).reversed().thenComparing(ExactItemStack::id))
                .findFirst().map(item -> new BakeryBatchSelection(Optional.of(item), Optional.empty()));
        if (ReferenceContainerCustody.hasLiveCustody(state, depot) && exact.isPresent()) return exact;
        var account = FungibleResourceCustodySupport.accountAtContainer(state, depot);
        int maximum = account.map(value -> Math.min(64, state.inventory().fungibleResources()
                .unclaimedQuantity(value.id(), resourceOwner, "minecraft:wheat"))).orElse(0);
        // A bounded stack-sized search also handles a nearly full output container.
        for (int quantity = maximum; quantity >= 1; quantity--) {
            if (!capacity.test(quantity)) continue;
            var input = FungibleResourceCustodySupport.selectAtContainer(state, depot, resourceOwner, "minecraft:wheat", quantity);
            if (input.isPresent()) return Optional.of(new BakeryBatchSelection(Optional.empty(), input));
        }
        return exact;
    }
}
