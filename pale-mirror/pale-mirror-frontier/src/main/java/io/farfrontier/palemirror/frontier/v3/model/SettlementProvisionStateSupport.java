package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;

/** State-owned aftermath for a confirmed exact food receipt. Planning remains process-owned. */
public final class SettlementProvisionStateSupport {
    private SettlementProvisionStateSupport() { }

    /** Terminal provision and physical theft use one owner-owned claim/nutrition transition. */
    public static FrontierWorldState reduceResolved(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                      SettlementProvisionResolved resolved) {
        SettlementProvision provision = state.humanPopulation().provision(resolved.settlementId());
        if (!subject.equals(resolved.settlementId()) || provision.status() != SettlementProvisionStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("settlement provision resolution lacks an active owner");
        }
        java.util.Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId> unspent = provision.allocations()
                .subList(provision.nextAllocation(), provision.allocations().size()).stream()
                .filter(SettlementRationAllocation::fungible).map(allocation -> {
                    var source = allocation.fungibleSource().orElseThrow();
                    ClaimAllocation claim = state.inventory().fungibleResources().claims().get(source.claimId());
                    if (claim == null) return null;
                    if (claim.purpose() != ClaimPurpose.SETTLEMENT_RATION
                            || !claim.claimantId().equals(provision.settlementId()) || !claim.economicOwnerId().equals(provision.settlementId())
                            || !"minecraft:bread".equals(claim.itemKind()) || claim.quantity() != allocation.count()
                            || !claim.lotQuantities().equals(java.util.Map.of(allocation.itemId(), allocation.count()))) {
                        throw new IllegalArgumentException("terminal provision cannot release a foreign ration claim");
                    }
                    return claim.id();
                }).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        ExactInventory inventory = unspent.isEmpty() ? state.inventory() : state.inventory().withFungibleResources(
                state.inventory().fungibleResources().releaseClaims(unspent));
        SettlementProvision next = resolved.status() == SettlementProvisionStatus.CONFLICT ? provision.conflict() : provision.shortage();
        HumanPopulation population = state.humanPopulation().withProvision(next);
        return state.withInventory(inventory).withHumanPopulation(resolved.status() == SettlementProvisionStatus.CONFLICT ? population
                : resolveUnserved(population, provision, provision.nextAllocation()));
    }

    public static FrontierWorldState reducePhysicalConsumptionAfterInventory(FrontierWorldState state, PhysicalIntent intent,
                                                                              ExactItemConsumedObservation consumed) {
        SettlementProvision provision = state.humanPopulation().provision(intent.causeSubjectId());
        if (provision.activeIntentId().filter(intent.id()::equals).isEmpty()) {
            throw new IllegalArgumentException("physical food receipt has no active settlement provision");
        }
        SettlementRationAllocation allocation = provision.currentOrActiveAllocation();
        if (!allocation.itemId().equals(consumed.itemId()) || allocation.count() != consumed.consumedCount()) {
            throw new IllegalArgumentException("physical settlement provision receipt does not match its allocation");
        }
        HumanPopulation population = feedCurrentAllocation(state.humanPopulation(), provision);
        if (provision.nextAllocation() + 1 == provision.allocations().size()) {
            population = resolveUnserved(population, provision, provision.nextAllocation() + 1);
        }
        return state.withHumanPopulation(population.withProvision(provision.consumeCurrent(consumed.itemId(), consumed.consumedCount())));
    }

    private static HumanPopulation feedCurrentAllocation(HumanPopulation population, SettlementProvision provision) {
        for (var residentId : provision.currentOrActiveAllocation().recipientIds()) {
            population = population.resolveNutrition(residentId, provision.cycleOrdinal(), true);
        }
        return population;
    }

    private static HumanPopulation resolveUnserved(HumanPopulation population, SettlementProvision provision, int confirmedAllocationCount) {
        if (confirmedAllocationCount < 0 || confirmedAllocationCount > provision.allocations().size()) {
            throw new IllegalArgumentException("invalid confirmed provision allocation count");
        }
        java.util.Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId> served = provision.allocations().subList(0, confirmedAllocationCount).stream()
                .flatMap(allocation -> allocation.recipientIds().stream()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (var residentId : provision.recipientIds()) if (!served.contains(residentId)) {
            population = population.resolveNutrition(residentId, provision.cycleOrdinal(), false);
        }
        return population;
    }
}
