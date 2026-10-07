package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Read-only feasible meal source; hunger alone does not make a meal an executable activity. */
public final class ResidentMealOpportunity {
    private ResidentMealOpportunity() { }

    public record Source(ResidentFoodSource foodSource, SurfaceAnchor clearingSurface,
                         FungibleResourceCustodySupport.LotSelection selection, FoodPortion portion) {
        public Source {
            Objects.requireNonNull(foodSource, "meal food source");
            Objects.requireNonNull(clearingSurface, "meal clearing surface");
            Objects.requireNonNull(selection, "meal resource selection");
            Objects.requireNonNull(portion, "meal food portion");
            if (!selection.lotQuantities().equals(portion.lotQuantities()))
                throw new IllegalArgumentException("meal opportunity differs from its exact portion");
        }
    }

    /** Cheap economic eligibility; geometry is planned only for an actual meal admission. */
    public record Candidate(ResidentFoodSource foodSource, FungibleResourceCustodySupport.LotSelection selection,
                            FoodPortion portion) {
        public Candidate {
            Objects.requireNonNull(foodSource, "meal candidate food source");
            Objects.requireNonNull(selection, "meal candidate selection");
            Objects.requireNonNull(portion, "meal candidate portion");
            if (!selection.lotQuantities().equals(portion.lotQuantities()))
                throw new IllegalArgumentException("meal candidate differs from its exact portion");
        }
    }

    public enum Wait { RESIDENT_STATE, CONTAINER_CUSTODY, SERVICE_ACCESS, FOOD_STOCK, MISSION_SUPPLY, INVENTORY_CAPACITY }

    /** The food owner explains source refusal; the scheduler never inspects resource semantics. */
    public record CandidateAdmission(Optional<Candidate> candidate, Optional<Wait> pending) {
        public CandidateAdmission {
            Objects.requireNonNull(candidate, "candidate admission");
            Objects.requireNonNull(pending, "candidate wait");
            if (candidate.isPresent() == pending.isPresent())
                throw new IllegalArgumentException("candidate admission needs exactly one result");
        }
        private static CandidateAdmission waiting(Wait reason) {
            return new CandidateAdmission(Optional.empty(), Optional.of(reason));
        }
    }

    public static Optional<Source> find(FrontierWorldState state, SubjectId residentId) {
        return find(state, residentId, state.humanPopulation().nutrition(residentId).lastEvaluatedTick());
    }

    public static Optional<Source> find(FrontierWorldState state, SubjectId residentId, long atTick) {
        return candidate(state, residentId, atTick).flatMap(candidate -> {
            if (candidate.foodSource() instanceof ResidentFoodSource.Personal)
                return Optional.of(new Source(candidate.foodSource(), state.actorLocations().get(residentId).supportingSurface(),
                        candidate.selection(), candidate.portion()));
            return ServiceAccessCoordinator.mealClearingSurface(state, residentId).map(clearing ->
                    new Source(candidate.foodSource(), clearing, candidate.selection(), candidate.portion()));
        });
    }

    public static Optional<Candidate> candidate(FrontierWorldState state, SubjectId residentId, long atTick) {
        return candidateAdmission(state, residentId, atTick).candidate();
    }

    public static CandidateAdmission candidateAdmission(FrontierWorldState state, SubjectId residentId, long atTick) {
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        if (resident == null || state.humanPopulation().meals().containsKey(residentId)
                || state.actorMovements().containsKey(residentId)
                || state.humanPopulation().migration(residentId) != null)
            return CandidateAdmission.waiting(Wait.RESIDENT_STATE);
        ActorLocation body = state.actorLocations().get(residentId);
        if (body == null || body.condition().status() != ActorLifeStatus.ALIVE)
            return CandidateAdmission.waiting(Wait.RESIDENT_STATE);
        var rules = state.bootstrap().ruleset().residentLife();
        int wanted = state.humanPopulation().nutrition(residentId).accrueThrough(atTick, rules,
                resident.characteristics().effectiveMetabolismPermille(atTick)).nutritionWanted(rules);
        var resources = state.inventory().fungibleResources();
        var presentation = UnitInventoryPresentation.inventory(state, residentId);
        for (var account : UnitInventory.personalAccounts(state, residentId)) {
            if (!account.claimQuantities().isEmpty()) continue;
            var carry = presentation.get(account.id());
            for (var food : new java.util.TreeMap<>(rules.foods().foods()).values()) {
                int quantity = food.portionFor(wanted, resources.unclaimedQuantity(carry.accountId(), resident.settlementId(), food.itemKind()));
                if (quantity == 0) continue;
                var selection = FungibleResourceCustodySupport.selectAtAccount(resources, carry.accountId(),
                        resident.settlementId(), food.itemKind(), quantity).orElseThrow();
                return new CandidateAdmission(Optional.of(new Candidate(
                        new ResidentFoodSource.Personal(residentId, carry.accountId(), carry.slot()), selection,
                        new FoodPortion(food.itemKind(), food.nutritionPerItem(), selection.lotQuantities()))), Optional.empty());
            }
        }
        if (!ActivityExecutionCapabilities.permitsHomeFood(state, HumanAssignmentProjection.compile(state).assignment(residentId)))
            return CandidateAdmission.waiting(Wait.MISSION_SUPPLY);
        var portionSlot = UnitInventoryPresentation.freeSlot(state, residentId);
        if (portionSlot.isEmpty()) return CandidateAdmission.waiting(Wait.INVENTORY_CAPACITY);
        SubjectId depot = FrontierWorldState.depotId(resident.settlementId());
        if (ReferenceContainerCustody.blocksCanonicalUse(state, depot))
            return CandidateAdmission.waiting(Wait.CONTAINER_CUSTODY);
        if (!ResidentMealServiceAccess.mayStart(state, depot, residentId))
            return CandidateAdmission.waiting(Wait.SERVICE_ACCESS);
        var account = FungibleResourceCustodySupport.accountAtContainer(state, depot).orElse(null);
        if (account == null) return CandidateAdmission.waiting(Wait.FOOD_STOCK);
        // Stable item-kind ordering is selection policy, never implicit resource identity.
        for (var food : new java.util.TreeMap<>(rules.foods().foods()).values()) {
            int available = state.inventory().fungibleResources().unclaimedQuantity(account.id(),
                    resident.settlementId(), food.itemKind());
            int quantity = food.portionFor(wanted, available);
            if (quantity == 0) continue;
            var selection = FungibleResourceCustodySupport.selectAtContainer(state, depot,
                    resident.settlementId(), food.itemKind(), quantity).orElseThrow();
            return new CandidateAdmission(Optional.of(new Candidate(new ResidentFoodSource.Depot(resident.settlementId(),
                    depot, account.id(), portionSlot.orElseThrow()), selection,
                    new FoodPortion(food.itemKind(), food.nutritionPerItem(), selection.lotQuantities()))), Optional.empty());
        }
        return CandidateAdmission.waiting(Wait.FOOD_STOCK);
    }
}
