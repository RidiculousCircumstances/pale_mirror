package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.Optional;

/** Read-only settlement food policy; the provision process is an executor, not a stock-query API. */
public final class SettlementFoodPolicy {
    public static final String BREAD = "minecraft:bread";

    private SettlementFoodPolicy() { }

    /** Transitional ration reserve: replace the pending legacy cycle with exact resident demand at adoption. */
    public static int reserveRequirement(FrontierWorldState state, SubjectId settlementId) {
        int living = Math.toIntExact(state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE).count());
        SettlementProvision provision = state.humanPopulation().provision(settlementId);
        int pending = provision.status() == SettlementProvisionStatus.IN_PROGRESS
                ? provision.requiredRations() - provision.fulfilledRations() : 0;
        return Math.addExact(Math.multiplyExact(living, 2), pending);
    }

    /** Birth policy still reads the active provision outcome until resident-life adoption. */
    public static boolean allowsPopulationGrowth(FrontierWorldState state, SubjectId settlementId) {
        SettlementProvisionStatus status = state.humanPopulation().provision(settlementId).status();
        return status == SettlementProvisionStatus.IDLE || status == SettlementProvisionStatus.SECURE;
    }

    /** Current canonical depot stock, including physically bound HOT stacks; not a spending permit. */
    public static int breadStock(FrontierWorldState state, SubjectId settlementId) {
        SubjectId depot = FrontierWorldState.depotId(settlementId);
        if (ReferenceContainerCustody.blocksCanonicalUse(state, depot)) return 0;
        int exact = state.inventory().items().values().stream()
                .filter(item -> BREAD.equals(item.itemKind()))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot
                        && slot.containerId().equals(depot))
                .mapToInt(ExactItemStack::count).reduce(0, Math::addExact);
        int fungible = state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container container
                        && container.containerId().equals(depot))
                .flatMap(account -> account.lotQuantities().entrySet().stream())
                .filter(entry -> BREAD.equals(state.inventory().fungibleResources().lots()
                        .get(entry.getKey()).itemKind()))
                .mapToInt(java.util.Map.Entry::getValue).reduce(0, Math::addExact);
        return Math.addExact(exact, fungible);
    }

    /** Food counted toward reserve before physical consumption confirms; HOT binding alone is not consumption. */
    public static int reserveCoverageBread(FrontierWorldState state, SubjectId settlementId) {
        SubjectId depot = FrontierWorldState.depotId(settlementId);
        if (ReferenceContainerCustody.blocksCanonicalUse(state, depot)) return 0;
        int exact = state.inventory().items().values().stream()
                .filter(item -> BREAD.equals(item.itemKind()))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot
                        && slot.containerId().equals(depot))
                .filter(item -> !hasUnconfirmedPhysicalCustody(state, item.id()))
                .mapToInt(ExactItemStack::count).reduce(0, Math::addExact);
        int fungible = state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container container
                        && container.containerId().equals(depot))
                .flatMap(account -> account.lotQuantities().entrySet().stream())
                .filter(entry -> BREAD.equals(state.inventory().fungibleResources().lots()
                        .get(entry.getKey()).itemKind()))
                .mapToInt(java.util.Map.Entry::getValue).reduce(0, Math::addExact);
        return Math.addExact(exact, fungible);
    }

    /** Unbound stock available to COLD planning, distinct from total stock or HOT take eligibility. */
    public static int coldUsableBread(FrontierWorldState state, SubjectId settlementId) {
        SubjectId depot = FrontierWorldState.depotId(settlementId);
        if (ReferenceContainerCustody.blocksCanonicalUse(state, depot)) return 0;
        int exact = state.inventory().items().values().stream().filter(item -> BREAD.equals(item.itemKind()))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot))
                .filter(item -> !hasUnconfirmedPhysicalCustody(state, item.id()))
                .mapToInt(ExactItemStack::count).reduce(0, Math::addExact);
        int fungible = state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container container && container.containerId().equals(depot))
                .filter(account -> state.inventory().fungibleResources().bindings().values().stream()
                        .noneMatch(binding -> binding.accountId().equals(account.id())))
                .flatMap(account -> account.lotQuantities().entrySet().stream())
                .filter(entry -> BREAD.equals(state.inventory().fungibleResources().lots().get(entry.getKey()).itemKind()))
                .mapToInt(java.util.Map.Entry::getValue).sum();
        return Math.addExact(exact, fungible);
    }

    public static Optional<ExactItemStack> exportableBread(FrontierWorldState state, SubjectId settlementId) {
        int reserve = reserveRequirement(state, settlementId);
        int available = coldUsableBread(state, settlementId);
        SubjectId depot = FrontierWorldState.depotId(settlementId);
        return state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id))
                .filter(item -> BREAD.equals(item.itemKind()))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot))
                .filter(item -> !hasUnconfirmedPhysicalCustody(state, item.id()))
                .filter(item -> item.count() == 64 && available - item.count() >= reserve).findFirst();
    }

    /** COLD export query; a live physical binding is not COLD planning authority. */
    public static Optional<FungibleResourceCustodySupport.LotAtContainer> exportableFungibleBread(
            FrontierWorldState state, SubjectId settlementId) {
        SubjectId depot = FrontierWorldState.depotId(settlementId);
        if (ReferenceContainerCustody.blocksCanonicalUse(state, depot)) return Optional.empty();
        int reserve = reserveRequirement(state, settlementId);
        int available = state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container container && container.containerId().equals(depot))
                .filter(account -> state.inventory().fungibleResources().bindings().values().stream()
                        .noneMatch(binding -> binding.accountId().equals(account.id())))
                .flatMap(account -> account.lotQuantities().entrySet().stream())
                .filter(entry -> BREAD.equals(state.inventory().fungibleResources().lots().get(entry.getKey()).itemKind()))
                .mapToInt(java.util.Map.Entry::getValue).sum();
        if (coldUsableBread(state, settlementId) - 64 < reserve) return Optional.empty();
        return FungibleResourceCustodySupport.firstAtContainer(state, depot, BREAD, 64).filter(lot ->
                state.inventory().fungibleResources().bindings().values().stream()
                        .noneMatch(binding -> binding.accountId().equals(lot.accountId())));
    }

    public static boolean hasUnconfirmedPhysicalCustody(FrontierWorldState state, SubjectId itemId) {
        return state.physicalIntents().values().stream()
                .anyMatch(intent -> intent.status() != PhysicalIntentStatus.CONFIRMED
                        // CARGO_LOADING retains the source as SOURCE_ITEM, while provision
                        // uses ITEM. The declared typed roles cover both without inference.
                        && intent.roles().namedRoles().containsValue(itemId));
    }
}
