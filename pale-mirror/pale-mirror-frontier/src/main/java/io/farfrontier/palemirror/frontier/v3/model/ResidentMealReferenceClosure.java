package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;

/** Cross-owner proof that each retained meal names its current bread and custody stage. */
public final class ResidentMealReferenceClosure {
    private ResidentMealReferenceClosure() { }

    public static void validate(FrontierWorldState state) {
        FungibleResourceLedger ledger = state.inventory().fungibleResources();
        HumanAssignmentProjection assignments = state.humanPopulation().meals().isEmpty()
                ? null : HumanAssignmentProjection.compile(state);
        for (ResidentMeal meal : state.humanPopulation().meals().values()) {
            meal.portion().validate(state.bootstrap().ruleset().residentLife().foods());
            ActorLocation actor = state.actorLocations().get(meal.residentId());
            if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
                throw new IllegalArgumentException("retained meal has no living resident body");
            if (meal.coldTravel().isPresent()) {
                var travel = meal.coldTravel().orElseThrow();
                AmbientActorLease previous = state.ambientLeases().get(meal.residentId());
                long expectedEpoch = previous == null ? 1L : Math.addExact(previous.revision(), 1L);
                if (!actor.supportingSurface().equals(travel.route().getFirst())
                        || travel.departedAtTick() < meal.startedAtTick()
                        || travel.authorityEpoch() != expectedEpoch
                        || previous != null && previous.status() != AmbientLeaseStatus.CLOSED)
                    throw new IllegalArgumentException("retained meal route lacks its exact COLD checkpoint or authority epoch");
            }
            if (!assignments.assignment(meal.residentId()).ownerId().equals(meal.retainedWorkOwner()))
                throw new IllegalArgumentException("retained meal lost or replaced its exact work owner");
            ClaimAllocation claim = ledger.claims().get(meal.claimId());
            CustodyAccount source = ledger.accounts().get(meal.sourceAccountId());
            CustodyAccount held = ledger.accounts().get(meal.actorAccountId());
            if (meal.phase() == ResidentMeal.Phase.MOVE || meal.phase() == ResidentMeal.Phase.TAKE) {
                if (!currentClaim(ledger, meal, claim, source, new ResourceCustody.Container(meal.depotId()))
                        || held != null)
                    throw new IllegalArgumentException("approaching resident meal lacks exact depot bread claim");
            } else if (meal.carriesFood()) {
                if (!currentClaim(ledger, meal, claim, held, new ResourceCustody.Actor(meal.residentId())))
                    throw new IllegalArgumentException("consuming resident meal lacks exact actor-held bread claim");
            } else if (claim != null || held != null) {
                throw new IllegalArgumentException("returning resident meal still owns a bread claim or hand");
            }
        }
    }

    private static boolean currentClaim(FungibleResourceLedger ledger, ResidentMeal meal,
                                        ClaimAllocation claim, CustodyAccount account,
                                        ResourceCustody expectedCustody) {
        return claim != null && claim.purpose() == ClaimPurpose.RESIDENT_MEAL
                && claim.claimantId().equals(meal.residentId())
                && claim.economicOwnerId().equals(meal.settlementId())
                && claim.lotQuantities().equals(meal.portion().lotQuantities())
                && meal.portion().lotQuantities().keySet().stream().allMatch(id -> {
                    ResourceLot lot = ledger.lots().get(id);
                    return lot != null && lot.itemKind().equals(meal.portion().itemKind())
                            && lot.economicOwnerId().equals(meal.settlementId());
                })
                && account != null && account.custody().equals(expectedCustody)
                && account.claimQuantities().getOrDefault(meal.claimId(), 0) == meal.portion().quantity()
                && meal.portion().lotQuantities().entrySet().stream().allMatch(entry ->
                    account.lotQuantities().getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }
}
