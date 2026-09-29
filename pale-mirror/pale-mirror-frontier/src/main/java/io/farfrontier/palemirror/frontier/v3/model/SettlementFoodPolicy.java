package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.Optional;

/** Read-only food policy derived from exact residents and current ledger stock. */
public final class SettlementFoodPolicy {
    public static final String BREAD = "minecraft:bread";

    private SettlementFoodPolicy() { }

    /** Two ordinary meals per living resident, plus currently accrued unmet individual need. */
    public static int reserveRequirement(FrontierWorldState state, SubjectId settlementId) {
        return state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .mapToInt(resident -> Math.addExact(2, state.humanPopulation().nutrition(resident.id()).hungerDeficit()))
                .reduce(0, Math::addExact);
    }

    /** A discretionary birth cannot spend the next resident's meal or mask current hunger. */
    public static boolean allowsPopulationGrowth(FrontierWorldState state, SubjectId settlementId) {
        return state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .noneMatch(resident -> state.humanPopulation().nutrition(resident.id()).hungerDeficit() > 0)
                && breadStock(state, settlementId) > reserveRequirement(state, settlementId);
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
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        CustodyAccount account = FungibleResourceCustodySupport.accountAtContainer(state, depot).orElse(null);
        if (account == null || resources.bindings().values().stream()
                .anyMatch(binding -> binding.accountId().equals(account.id()))) return Optional.empty();
        int unclaimed = resources.unclaimedQuantity(account.id(), settlementId, BREAD);
        int accountBread = account.lotQuantities().entrySet().stream()
                .filter(entry -> BREAD.equals(resources.lots().get(entry.getKey()).itemKind()))
                .mapToInt(java.util.Map.Entry::getValue).sum();
        // The reserve may reside in other exact stacks at this depot. Subtract the
        // account's claimed portion from total COLD stock, not the whole reserve from
        // this single fungible account; doing the latter prevents legitimate exports.
        if (coldUsableBread(state, settlementId) - (accountBread - unclaimed) - 64 < reserve)
            return Optional.empty();
        java.util.Map<SubjectId, Integer> pinned = new java.util.HashMap<>();
        account.claimQuantities().keySet().forEach(id -> resources.claims().get(id).lotQuantities()
                .forEach((lot, count) -> pinned.merge(lot, count, Math::addExact)));
        return account.lotQuantities().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .filter(entry -> entry.getValue() - pinned.getOrDefault(entry.getKey(), 0) >= 64)
                .map(entry -> new FungibleResourceCustodySupport.LotAtContainer(account.id(),
                        resources.lots().get(entry.getKey()), entry.getValue()))
                .filter(candidate -> candidate.lot().economicOwnerId().equals(settlementId)
                        && candidate.lot().itemKind().equals(BREAD)).findFirst();
    }

    public static boolean hasUnconfirmedPhysicalCustody(FrontierWorldState state, SubjectId itemId) {
        return state.physicalIntents().values().stream()
                .anyMatch(intent -> intent.status() != PhysicalIntentStatus.CONFIRMED
                        // CARGO_LOADING retains the source as SOURCE_ITEM, while provision
                        // uses ITEM. The declared typed roles cover both without inference.
                        && intent.roles().namedRoles().containsValue(itemId));
    }
}
