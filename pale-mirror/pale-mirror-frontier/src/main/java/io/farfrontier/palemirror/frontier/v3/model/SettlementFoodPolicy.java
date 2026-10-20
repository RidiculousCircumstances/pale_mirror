package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.Optional;

/** Read-only food policy derived from exact residents and current ledger stock. */
public final class SettlementFoodPolicy {
    public static final String BREAD = "minecraft:bread";

    private SettlementFoodPolicy() { }

    /** Two ordinary meals per living resident, plus the current target portion, never missed-meal debt. */
    public static int reserveRequirement(FrontierWorldState state, SubjectId settlementId, long atTick) {
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().economics().isPresent()) {
            var participant = state.companies().goodsTrade().participants().participants().get(settlementId);
            return Math.toIntExact(GoodsParticipantAssessment.domestic(state, participant, atTick).view().supplyPlan().orElseThrow()
                    .reserves().get(BREAD).reorderPoint());
        }
        return baselineReserveRequirement(state, settlementId);
    }

    /** Owner-fact baseline for non-economic profiles and economic fact collection. */
    static int baselineReserveRequirement(FrontierWorldState state, SubjectId settlementId) {
        if (SettlementReservePlanning.enabled(state)) return Math.toIntExact(
                SettlementReservePlanning.retainedTargets(state, settlementId).get(BREAD).reorderPoint());
        return state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .mapToInt(resident -> Math.addExact(2, Math.ceilDiv(state.humanPopulation().nutrition(resident.id())
                        .nutritionWanted(state.bootstrap().ruleset().residentLife()),
                        state.bootstrap().ruleset().residentLife().breadNutritionUnits())))
                .reduce(0, Math::addExact);
    }

    /** A discretionary birth cannot spend the next resident's meal or mask current hunger. */
    public static boolean allowsPopulationGrowth(FrontierWorldState state, SubjectId settlementId, long atTick) {
        return state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .noneMatch(resident -> state.humanPopulation().nutrition(resident.id()).wantsFood(state.bootstrap().ruleset().residentLife()))
                && reserveCoverageBread(state, settlementId) > reserveRequirement(state, settlementId, atTick);
    }

    private static java.util.Set<SubjectId> stores(FrontierWorldState state, SubjectId home) {
        return ResourceStorageSelection.containers(state, home, BREAD, java.util.Set.of(ResourceStorageRole.WAREHOUSE));
    }

    /** Current canonical eligible warehouse stock, including HOT stacks; not a spending permit. */
    public static int breadStock(FrontierWorldState state, SubjectId settlementId) {
        var stores = stores(state, settlementId);
        int exact = state.inventory().items().values().stream()
                .filter(item -> item.economicOwnerId().equals(settlementId))
                .filter(item -> BREAD.equals(item.itemKind()))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot
                        && stores.contains(slot.containerId()))
                .mapToInt(ExactItemStack::count).reduce(0, Math::addExact);
        int fungible = state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container container
                        && stores.contains(container.containerId()))
                .flatMap(account -> account.lotQuantities().entrySet().stream())
                .filter(entry -> state.inventory().fungibleResources().lots().get(entry.getKey()).economicOwnerId().equals(settlementId))
                .filter(entry -> BREAD.equals(state.inventory().fungibleResources().lots()
                        .get(entry.getKey()).itemKind()))
                .mapToInt(java.util.Map.Entry::getValue).reduce(0, Math::addExact);
        return Math.addExact(exact, fungible);
    }

    /** Food counted toward reserve before physical consumption confirms; HOT binding alone is not consumption. */
    public static int reserveCoverageBread(FrontierWorldState state, SubjectId settlementId) {
        var stores = stores(state, settlementId);
        int exact = state.inventory().items().values().stream()
                .filter(item -> item.economicOwnerId().equals(settlementId))
                .filter(item -> BREAD.equals(item.itemKind()))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot
                        && stores.contains(slot.containerId()))
                .filter(item -> !hasUnconfirmedPhysicalCustody(state, item.id()))
                .mapToInt(ExactItemStack::count).reduce(0, Math::addExact);
        int fungible = state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container container
                        && stores.contains(container.containerId()))
                .flatMap(account -> account.lotQuantities().entrySet().stream())
                .filter(entry -> state.inventory().fungibleResources().lots().get(entry.getKey()).economicOwnerId().equals(settlementId))
                .filter(entry -> BREAD.equals(state.inventory().fungibleResources().lots()
                        .get(entry.getKey()).itemKind()))
                .mapToInt(java.util.Map.Entry::getValue).reduce(0, Math::addExact);
        int outgoing = state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container container && stores.contains(container.containerId()))
                .flatMap(account -> account.claimQuantities().entrySet().stream())
                .filter(entry -> {
                    ClaimAllocation claim = state.inventory().fungibleResources().claims().get(entry.getKey());
                    return claim.economicOwnerId().equals(settlementId) && claim.itemKind().equals(BREAD)
                            && (claim.purpose() == ClaimPurpose.GOODS_TRADE);
                }).mapToInt(java.util.Map.Entry::getValue).reduce(0, Math::addExact);
        return Math.subtractExact(Math.addExact(exact, fungible), outgoing);
    }

    /** Unbound, unclaimed stock available to COLD planning, distinct from total stock or HOT take eligibility. */
    public static int coldUsableBread(FrontierWorldState state, SubjectId settlementId) {
        var stores = stores(state, settlementId);
        int exact = state.inventory().items().values().stream().filter(item -> BREAD.equals(item.itemKind()))
                .filter(item -> item.economicOwnerId().equals(settlementId))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot && stores.contains(slot.containerId()))
                .filter(item -> !hasUnconfirmedPhysicalCustody(state, item.id()))
                .mapToInt(ExactItemStack::count).reduce(0, Math::addExact);
        int fungible = state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container container && stores.contains(container.containerId()))
                .filter(account -> state.inventory().fungibleResources().bindings().values().stream()
                        .noneMatch(binding -> binding.accountId().equals(account.id())))
                .mapToInt(account -> state.inventory().fungibleResources().unclaimedQuantity(account.id(), settlementId, BREAD))
                .reduce(0, Math::addExact);
        return Math.addExact(exact, fungible);
    }

    /** Unclaimed usable warehouse stock, including current HOT bindings. This is a stock
     * diagnostic, not service admission or permission to write a physically held container. */
    public static int usableBread(FrontierWorldState state, SubjectId settlementId) {
        var stores = stores(state, settlementId).stream()
                .filter(container -> !ReferenceContainerCustody.blocksCanonicalUse(state, container))
                .collect(java.util.stream.Collectors.toSet());
        int exact = state.inventory().items().values().stream()
                .filter(item -> BREAD.equals(item.itemKind()) && item.economicOwnerId().equals(settlementId))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot && stores.contains(slot.containerId()))
                .filter(item -> !hasUnconfirmedPhysicalCustody(state, item.id()))
                .mapToInt(ExactItemStack::count).reduce(0, Math::addExact);
        var resources = state.inventory().fungibleResources();
        int fungible = resources.accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container container && stores.contains(container.containerId()))
                .mapToInt(account -> resources.unclaimedQuantity(account.id(), settlementId, BREAD))
                .reduce(0, Math::addExact);
        return Math.addExact(exact, fungible);
    }

    public static Optional<ExactItemStack> exportableBread(FrontierWorldState state, SubjectId settlementId, long atTick) {
        int reserve = reserveRequirement(state, settlementId, atTick);
        int available = coldUsableBread(state, settlementId);
        var stores = stores(state, settlementId);
        return state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id))
                .filter(item -> BREAD.equals(item.itemKind()))
                .filter(item -> item.economicOwnerId().equals(settlementId))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot && stores.contains(slot.containerId()))
                .filter(item -> !hasUnconfirmedPhysicalCustody(state, item.id()))
                .filter(item -> item.count() == 64 && available - item.count() >= reserve).findFirst();
    }

    /** COLD export query; a live physical binding is not COLD planning authority. */
    public static Optional<FungibleResourceCustodySupport.LotAtContainer> exportableFungibleBread(
            FrontierWorldState state, SubjectId settlementId, long atTick) {
        var stores = stores(state, settlementId);
        int reserve = reserveRequirement(state, settlementId, atTick);
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        if (coldUsableBread(state, settlementId) - 64 < reserve) return Optional.empty();
        return resources.accounts().values().stream().sorted(Comparator.comparing(CustodyAccount::id))
                .filter(account -> account.custody() instanceof ResourceCustody.Container container && stores.contains(container.containerId()))
                .filter(account -> resources.bindings().values().stream().noneMatch(binding -> binding.accountId().equals(account.id())))
                .map(account -> exportCandidate(resources, account, settlementId)).flatMap(Optional::stream).findFirst();
    }

    private static Optional<FungibleResourceCustodySupport.LotAtContainer> exportCandidate(
            FungibleResourceLedger resources, CustodyAccount account, SubjectId settlementId) {
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
                        // Pending effects declare their exact item through typed roles.
                        && intent.roles().namedRoles().containsValue(itemId));
    }
}
